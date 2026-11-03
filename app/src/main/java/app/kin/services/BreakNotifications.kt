package app.kin.services

import android.app.*
import android.content.*
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.work.*
import app.kin.KinApplication
import app.kin.R
import app.kin.ReminderActivity
import app.kin.data.BreakSession
import app.kin.domain.BreakPolicy
import app.kin.domain.ReminderPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.LocalTime

object BreakNotifications {
    const val FOREGROUND_ID = 22010
    const val DUE_ID = 22011
    private var configured = false
    private fun tag(id: Long) = "kin.break.$id"
    fun intent(context: Context, value: BreakSession, chooseFocus: Boolean) = Intent(context, ReminderActivity::class.java)
        .setData("kin-break://session/${value.id}/${value.revision}/${if (chooseFocus) "start" else "open"}".toUri())
        .putExtra("route", "focus").putExtra("breakId", value.id).putExtra("breakRevision", value.revision)
        .putExtra("chooseBreakFocus", chooseFocus)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    private fun open(context: Context, value: BreakSession, choose: Boolean) = PendingIntent.getActivity(context, 0,
        intent(context, value, choose), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun action(context: Context, value: BreakSession, name: String) = PendingIntent.getBroadcast(context, 0,
        Intent(context, BreakActionReceiver::class.java).setAction("app.kin.BREAK_$name")
            .setData("kin-break://action/${value.id}/${value.revision}/$name".toUri())
            .putExtra("breakId", value.id).putExtra("revision", value.revision).putExtra("operation", name),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun channels(context: Context) {
        if (configured) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(
            NotificationChannel("kin_break_timer", "Timer controls", NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null) },
            NotificationChannel("kin_break_due", "Return to your task", NotificationManager.IMPORTANCE_HIGH)))
        configured = true
    }
    fun foreground(context: Context, value: BreakSession?, remaining: Long = 0): Notification {
        channels(context)
        val text = when (value?.phase) {
            "RUNNING" -> "${format(remaining)} · ${value.taskTitle}"
            "PAUSED" -> "Paused · ${value.taskTitle}"
            "DUE" -> "Break over · ${value.taskTitle}"
            else -> "Preparing your break timer"
        }
        val builder = NotificationCompat.Builder(context, "kin_break_timer").setSmallIcon(R.drawable.ic_kin)
            .setContentTitle("KIN break timer").setContentText(text).setOngoing(true).setSilent(true).setOnlyAlertOnce(true)
        if (value != null) {
            builder.setContentIntent(open(context, value, false))
            when (value.phase) {
                "RUNNING" -> builder.addAction(0, "Pause break", action(context, value, "PAUSE"))
                "PAUSED" -> builder.addAction(0, "Resume break", action(context, value, "RESUME"))
                "DUE" -> builder.addAction(0, "Start task", open(context, value, true)).addAction(0, "Snooze 5 min", action(context, value, "SNOOZE"))
            }
            builder.addAction(0, "Stop break", action(context, value, "STOP"))
        }
        return builder.build()
    }
    suspend fun due(app: KinApplication, value: BreakSession) {
        if (KinVisibility.resumed.value || !Notifications.allowed(app) || !app.breaks.claimReminder(value.id, value.revision)) return
        channels(app)
        val pref = app.preferences.flow.first()
        val quiet = ReminderPolicy.isQuiet(LocalTime.now().hour, pref.quietStart, pref.quietEnd)
        val message = BreakPolicy.message(value.taskTitle, value.goalTitle, value.snoozeCount)
        val notification = NotificationCompat.Builder(app, "kin_break_due").setSmallIcon(R.drawable.ic_kin)
            .setContentTitle("Your break is over").setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(open(app, value, true)).setAutoCancel(true).setSilent(quiet)
            .addAction(0, "Start task", open(app, value, true))
            .addAction(0, "Snooze 5 min", action(app, value, "SNOOZE"))
            .addAction(0, "Stop break", action(app, value, "STOP")).build()
        try { NotificationManagerCompat.from(app).notify(tag(value.id), DUE_ID, notification) } catch (_: SecurityException) { }
    }
    fun snoozed(context: Context, value: BreakSession) {
        if (!Notifications.allowed(context)) return
        channels(context)
        val message = BreakPolicy.message(value.taskTitle, value.goalTitle, value.snoozeCount)
        try { NotificationManagerCompat.from(context).notify(tag(value.id), DUE_ID,
            NotificationCompat.Builder(context, "kin_break_timer").setSmallIcon(R.drawable.ic_kin)
                .setContentTitle("Five more minutes").setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(open(context, value, false)).setAutoCancel(true).setSilent(true).build())
        } catch (_: SecurityException) { }
    }
    fun cancel(context: Context, id: Long) { context.getSystemService(NotificationManager::class.java).cancel(tag(id), DUE_ID) }
    fun format(millis: Long): String { val seconds = (millis.coerceAtLeast(0) + 999) / 1000; return "%02d:%02d".format(seconds / 60, seconds % 60) }
}

class BreakActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("breakId", 0)
        val revision = intent.getIntExtra("revision", -1)
        val operation = intent.getStringExtra("operation") ?: return
        if (id <= 0 || revision < 0 || operation !in listOf("PAUSE", "RESUME", "SNOOZE", "STOP")) return
        WorkManager.getInstance(context).enqueueUniqueWork("break-action:$id:$revision", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<BreakActionWorker>().setInputData(workDataOf("breakId" to id, "revision" to revision, "operation" to operation)).build())
    }
}
class BreakActionWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        val app = applicationContext as KinApplication
        val id = inputData.getLong("breakId", 0); val revision = inputData.getInt("revision", -1)
        val changed = when (inputData.getString("operation")) {
            "PAUSE" -> app.breaks.pause(id, revision)
            "RESUME" -> app.breaks.resume(id, revision)
            "SNOOZE" -> app.breaks.snooze(id, revision)
            "STOP" -> app.breaks.stop(id, revision)
            else -> false
        }
        if (changed && inputData.getString("operation") == "SNOOZE") app.database.dao().breakSession(id)?.let { BreakNotifications.snoozed(app, it) }
        Result.success()
    } catch (e: CancellationException) { throw e
    } catch (_: java.io.IOException) { Result.retry() }
}
class BreakReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        val app = applicationContext as KinApplication
        val value = app.breaks.refresh(inputData.getLong("breakId", 0))
        if (value != null && value.revision == inputData.getInt("revision", -1)) {
            if (value.phase == "DUE" && !BreakTimerService.status.value.running) BreakNotifications.due(app, value)
            else if (value.phase == "RUNNING") app.scheduler.breakTimer(value, app.breaks.remaining(value))
        }
        Result.success()
    } catch (e: CancellationException) { throw e
    } catch (_: java.io.IOException) { Result.retry() }
}
