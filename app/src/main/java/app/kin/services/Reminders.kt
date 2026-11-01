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
import app.kin.R
import app.kin.data.Task
import app.kin.domain.ReminderPolicy
import kotlinx.coroutines.flow.first
import java.time.LocalTime
import java.util.concurrent.TimeUnit

class Scheduler(private val context: Context) {
    private val work get() = WorkManager.getInstance(context)
    fun task(task: Task) {
        cancelTask(task.id)
        if (!task.reminder || task.completed) return
        val request = OneTimeWorkRequestBuilder<KinWorker>()
            .setInputData(workDataOf("task" to task.id))
            .setInitialDelay((task.scheduledAt - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS).build()
        work.enqueueUniqueWork("task:${task.id}", ExistingWorkPolicy.REPLACE, request)
    }
    fun cancelTask(id: Long) {
        work.cancelUniqueWork("task:$id")
        NotificationManagerCompat.from(context).cancel(id.toInt())
    }
    fun focus(id: Long, delay: Long) {
        work.enqueueUniqueWork("focus:$id", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<KinWorker>().setInputData(workDataOf("focus" to id))
                .setInitialDelay(delay, TimeUnit.MILLISECONDS).build())
    }
    fun cancelFocus(id: Long) { work.cancelUniqueWork("focus:$id") }
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
                val preferences = app.preferences.flow.first()
                if (!task.completed && task.reminder && preferences.reminders &&
                    !ReminderPolicy.isQuiet(LocalTime.now().hour, preferences.quietStart, preferences.quietEnd)) {
                    Notifications.show(app, task.id.toInt(), "It's time for our next adventure", task.title, "tasks")
                }
            }
            Result.success()
        } catch (_: java.io.IOException) { Result.retry() }
    }
}

object Notifications {
    fun createChannels(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("kin_gentle", "Gentle reminders", NotificationManager.IMPORTANCE_DEFAULT))
    }
    fun show(context: Context, id: Int, title: String, message: String, route: String) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
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
