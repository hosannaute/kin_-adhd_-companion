package app.kin.services

import android.app.*
import android.content.*
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.work.*
import app.kin.KinApplication
import app.kin.ReminderActivity
import app.kin.R
import app.kin.data.FocusSession

object FocusTimerNotifications {
    fun token(value: FocusSession) = "${value.startedElapsed}:${value.remainingMillis}:${value.running}"
    fun foreground(context: Context, value: FocusSession, remaining: Long): Notification {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("kin_break_timer", "Timer controls", NotificationManager.IMPORTANCE_LOW).apply { setSound(null,null) })
        val open = PendingIntent.getActivity(context, 0, Intent(context, ReminderActivity::class.java)
            .setData("kin-focus://${value.id}/open".toUri()).putExtra("route", "focus"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun action(op: String) = PendingIntent.getBroadcast(context, 0, Intent(context, FocusTimerActionReceiver::class.java)
            .setData("kin-focus://${value.id}/${android.net.Uri.encode(token(value))}/$op".toUri())
            .putExtra("focusId", value.id).putExtra("token", token(value)).putExtra("operation", op),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context, "kin_break_timer").setSmallIcon(R.drawable.ic_kin)
            .setContentTitle("KIN focus timer").setContentText("${if (!value.running) "Paused" else BreakNotifications.format(remaining)} · ${value.taskTitle}")
            .setContentIntent(open).setOngoing(true).setSilent(true).setOnlyAlertOnce(true)
            .addAction(0, if (value.running) "Pause focus" else "Resume focus", action(if (value.running) "PAUSE" else "RESUME"))
            .addAction(0, "Finish focus early", action("STOP")).build()
    }
}
class FocusTimerActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("focusId",0); val token = intent.getStringExtra("token") ?: return
        val op = intent.getStringExtra("operation") ?: return
        if (id <= 0 || op !in listOf("PAUSE","RESUME","STOP")) return
        WorkManager.getInstance(context).enqueueUniqueWork("focus-action:$id:$token", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<FocusTimerActionWorker>().setInputData(workDataOf("focusId" to id,"token" to token,"operation" to op)).build())
    }
}
class FocusTimerActionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context,params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as KinApplication
        val value = app.database.dao().currentFocus() ?: return Result.success()
        if (value.id != inputData.getLong("focusId",0) || FocusTimerNotifications.token(value) != inputData.getString("token")) return Result.success()
        when(inputData.getString("operation")) {
            "PAUSE" -> app.repository.pauseFocus(value.id)
            "RESUME" -> app.repository.resumeFocus(value.id)
            "STOP" -> app.repository.stopFocus(value.id)
        }
        return Result.success()
    }
}
