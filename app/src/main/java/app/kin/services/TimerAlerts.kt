package app.kin.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import app.kin.KinApplication
import app.kin.R
import app.kin.ReminderActivity
import app.kin.data.FocusSession
import app.kin.data.UserPreferences
import app.kin.domain.AlertMode
import app.kin.domain.AlertPolicy
import kotlinx.coroutines.flow.first
import java.time.LocalTime

enum class TimerAlertKind(val label: String) { BREAK("Break alerts"), FOCUS("Focus complete") }

/** Android owns playback, volume, Do Not Disturb and user edits to each channel. */
object TimerAlerts {
    fun channelId(kind: TimerAlertKind, mode: AlertMode) = "kin_timer_v1_${kind.name.lowercase()}_${mode.name.lowercase()}"
    fun channel(context: Context, kind: TimerAlertKind, mode: AlertMode): String {
        val id = channelId(kind,mode)
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(id) == null) {
            manager.createNotificationChannel(NotificationChannel(id,"${kind.label} · ${mode.label}", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "One alert when this timer ends. Phone volume and Do Not Disturb apply."
                val resourceId = if (kind == TimerAlertKind.BREAK) R.raw.break_chime else R.raw.focus_chime
                val resource = context.resources.getResourceEntryName(resourceId)
                setSound(if (mode == AlertMode.SOUND_VIBRATION) "android.resource://${context.packageName}/raw/$resource".toUri() else null,
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                enableVibration(mode != AlertMode.SILENT)
                if (mode != AlertMode.SILENT) vibrationPattern = longArrayOf(0,120,80,120)
                setShowBadge(false)
            })
        }
        return id
    }
    fun builder(context: Context, kind: TimerAlertKind, pref: UserPreferences): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel(context,kind,pref.alertMode)).setSmallIcon(R.drawable.ic_kin)
            .setSilent(AlertPolicy.silent(pref.alertMode,LocalTime.now().hour,pref.quietStart,pref.quietEnd))
            .setOnlyAlertOnce(true).setAutoCancel(true)

    suspend fun focus(context: Context, value: FocusSession) {
        if (!Notifications.allowed(context)) return
        val pref = (context.applicationContext as KinApplication).preferences.flow.first()
        val open = PendingIntent.getActivity(context,0,Intent(context,ReminderActivity::class.java)
            .setData("kin-focus://${value.id}/complete".toUri()).putExtra("route","focus"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        post(context,"kin.focus.${value.id}",Int.MAX_VALUE,builder(context,TimerAlertKind.FOCUS,pref)
            .setContentTitle("A little progress, well earned")
            .setContentText("${value.taskTitle} · Focus complete. +15 XP")
            .setContentIntent(open).build())
    }
    suspend fun preview(context: Context, kind: TimerAlertKind) {
        if (!Notifications.allowed(context)) return
        val pref = (context.applicationContext as KinApplication).preferences.flow.first()
        post(context,"kin.timer.preview",22013 + kind.ordinal,builder(context,kind,pref)
            .setOnlyAlertOnce(false).setTimeoutAfter(10_000)
            .setContentTitle("${kind.label} · test")
            .setContentText("${pref.alertMode.label}. Quiet hours, phone volume and Do Not Disturb apply.").build())
    }
    fun settings(context: Context, kind: TimerAlertKind, mode: AlertMode) = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID,channel(context,kind,mode))
    private fun post(context: Context, tag: String, id: Int, notification: Notification) {
        try { NotificationManagerCompat.from(context).notify(tag,id,notification) } catch (_: SecurityException) { }
    }
}
