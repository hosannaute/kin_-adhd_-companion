package app.kin.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import app.kin.KinApplication
import app.kin.MainActivity
import app.kin.ReminderActivity
import app.kin.R
import app.kin.data.Task
import app.kin.data.BreakSession
import app.kin.data.resolved
import app.kin.domain.TaskEntryAction
import androidx.core.net.toUri
import app.kin.domain.ReminderPolicy
import kotlinx.coroutines.flow.first
import java.time.LocalTime
import java.util.concurrent.TimeUnit

class Scheduler(private val context: Context) {
    private val work get() = WorkManager.getInstance(context)
    fun task(task: Task) {
        cancelTask(task.id)
        if (!task.reminder || task.resolved) return
        val request = OneTimeWorkRequestBuilder<KinWorker>()
            .setInputData(workDataOf("task" to task.id))
            .setInitialDelay((task.scheduledAt - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS).build()
        work.enqueueUniqueWork("task:${task.id}", ExistingWorkPolicy.REPLACE, request)
    }
    fun cancelTask(id: Long) {
        work.cancelUniqueWork("task:$id")
        Notifications.cancelTask(context, id)
    }
    fun focus(id: Long, delay: Long) {
        work.enqueueUniqueWork("focus:$id", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<KinWorker>().setInputData(workDataOf("focus" to id))
                .setInitialDelay(delay, TimeUnit.MILLISECONDS).build())
    }
    fun cancelFocus(id: Long) { work.cancelUniqueWork("focus:$id") }
    fun breakTimer(value: BreakSession, delay: Long) {
        work.enqueueUniqueWork("break:${value.id}", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<BreakReminderWorker>().setInputData(workDataOf("breakId" to value.id, "revision" to value.revision))
                .setInitialDelay(delay.coerceAtLeast(0), TimeUnit.MILLISECONDS).build())
    }
    fun cancelBreak(id: Long) { work.cancelUniqueWork("break:$id"); BreakNotifications.cancel(context, id) }
}

class KinWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as KinApplication
        return try {
            val focusId = inputData.getLong("focus", 0)
            if (focusId != 0L) {
                if (app.repository.refreshFocus(focusId)) {
                    Notifications.show(app, Int.MAX_VALUE, "A little progress, well earned", "Your focus session is complete. +15 XP", "focus")
                } else {
                    // WorkManager is inexact and can run early after a wall-clock change.
                    val session = app.database.dao().session(focusId)
                    if (session != null && session.running && !session.ended) {
                        app.scheduler.focus(focusId, app.repository.remaining(session))
                    }
                }
            } else {
                val task = app.database.dao().task(inputData.getLong("task", 0)) ?: return Result.success()
                if (!task.resolved && task.scheduledAt > System.currentTimeMillis()) {
                    app.scheduler.task(task)
                    return Result.success()
                }
                val preferences = app.preferences.flow.first()
                if (!task.resolved && task.reminder && preferences.reminders &&
                    !ReminderPolicy.isQuiet(LocalTime.now().hour, preferences.quietStart, preferences.quietEnd)) {
                    Notifications.task(app, task)
                }
            }
            Result.success()
        } catch (_: java.io.IOException) { Result.retry() }
    }
}

object Notifications {
    const val TASK_ID = 17003
    fun taskTag(taskId: Long) = "kin.task.$taskId"
    fun cancelTask(context: Context, taskId: Long) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(taskTag(taskId), TASK_ID)
        // Remove an old 0.1/0.2 task notification without touching focus/foreground controls.
        manager.activeNotifications.filter { it.tag == null && it.id == taskId.toInt() &&
            it.id != Int.MAX_VALUE && it.notification.channelId == "kin_gentle" }.forEach { manager.cancel(it.id) }
    }
    fun allowed(context: Context): Boolean =
        (android.os.Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,
            Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun task(context: Context, task: Task) {
        if (task.resolved || !allowed(context)) return
        fun open(action: TaskEntryAction) = PendingIntent.getActivity(context, 0,
            Intent(context, ReminderActivity::class.java)
                .setData("kin-reminder://task/${task.id}/${action.name}".toUri())
                .putExtra("route", if (action == TaskEntryAction.START) "focus" else "tasks")
                .putExtra("taskId", task.id).putExtra("taskAction", action.name)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val snooze = PendingIntent.getBroadcast(context, 0,
            Intent(context, ReminderActionReceiver::class.java).setAction(ReminderActionReceiver.SNOOZE)
                .setData("kin-reminder://task/${task.id}/snooze".toUri()).putExtra("taskId", task.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val options = open(TaskEntryAction.OPTIONS)
        val notification = NotificationCompat.Builder(context, "kin_gentle").setSmallIcon(R.drawable.ic_kin)
            .setContentTitle("It's time for our next adventure").setContentText(task.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${task.title}\nStart small, snooze, or open Options to break it down, reschedule or skip."))
            .setContentIntent(options).setAutoCancel(true)
            .addAction(0, "Start", open(TaskEntryAction.START)).addAction(0, "Snooze 10 min", snooze)
            .addAction(0, "Options", options).build()
        try { NotificationManagerCompat.from(context).notify(taskTag(task.id), TASK_ID, notification) }
        catch (_: SecurityException) { /* Permission can change while posting. */ }
    }
    fun createChannels(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("kin_gentle", "Gentle reminders", NotificationManager.IMPORTANCE_DEFAULT))
    }
    fun show(context: Context, id: Int, title: String, message: String, route: String) {
        if (!allowed(context)) return
        val intent = Intent(context, MainActivity::class.java).putExtra("route", route)
        val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try {
            NotificationManagerCompat.from(context).notify(id,
                NotificationCompat.Builder(context, "kin_gentle").setSmallIcon(R.drawable.ic_kin)
                    .setContentTitle(title).setContentText(message).setContentIntent(pending)
                    .setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_DEFAULT).build())
        } catch (_: SecurityException) { /* Permission can be revoked between checking and notifying. */ }
    }
}
