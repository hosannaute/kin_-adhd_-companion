package app.kin.services

import android.app.*
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.content.BroadcastReceiver
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
import app.kin.data.TimerSound
import androidx.work.*
import app.kin.domain.AlertMode
import app.kin.domain.AlertPolicy
import kotlinx.coroutines.flow.first
import java.time.LocalTime

enum class TimerAlertKind(val label: String) { BREAK("Break alerts"), FOCUS("Focus complete") }

/** Android owns playback, volume, Do Not Disturb and user edits to each channel. */
object TimerAlerts {
    fun channelId(kind: TimerAlertKind, mode: AlertMode, sound: TimerSound = TimerSound()): String =
        if (mode == AlertMode.SOUND_VIBRATION && sound.revision > 0) "kin_sound_v2_${kind.name.lowercase()}_${sound.revision}"
        else "kin_timer_v1_${kind.name.lowercase()}_${mode.name.lowercase()}"
    fun channel(context: Context, kind: TimerAlertKind, mode: AlertMode, sound: TimerSound = TimerSound()): String {
        val id = channelId(kind,mode,sound)
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(id) == null) {
            manager.createNotificationChannel(NotificationChannel(id,"${kind.label} · ${mode.label}", if (mode == AlertMode.SOUND_VIBRATION) sound.importance else NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "One alert when this timer ends. Phone volume and Do Not Disturb apply."
                val resourceId = if (kind == TimerAlertKind.BREAK) R.raw.break_chime else R.raw.focus_chime
                val resource = context.resources.getResourceEntryName(resourceId)
                setSound(if (mode == AlertMode.SOUND_VIBRATION && !sound.silent) sound.uri.takeIf { it.isNotBlank() }?.toUri() ?: "android.resource://${context.packageName}/raw/$resource".toUri() else null,
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                enableVibration(mode != AlertMode.SILENT)
                if (mode != AlertMode.SILENT) vibrationPattern = longArrayOf(0,120,80,120)
                setShowBadge(false)
            })
        }
        manager.getNotificationChannel(id)?.sound?.let { grantSoundPlayer(context,it) }
        return id
    }
    /** Android 8/8.1 do not grant channel-sound URIs to their separate ringtone player. */
    // This optional framework resource identifies an OEM system component, not a
    // shrinkable app resource. Android has no public resource ID for it.
    @SuppressLint("DiscouragedApi")
    private fun grantSoundPlayer(context: Context, uri: Uri) {
        if (uri.scheme != "content" || uri.authority != "${context.packageName}.sounds") return
        val configured = runCatching {
            val resource = context.resources.getIdentifier("config_systemUIServiceComponent","string","android")
            if (resource == 0) null else ComponentName.unflattenFromString(context.getString(resource))?.packageName
        }.getOrNull()
        (listOfNotNull(configured) + "com.android.systemui").distinct().forEach { name ->
            runCatching {
                val info = context.packageManager.getApplicationInfo(name,0)
                if (info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)
                    context.grantUriPermission(name,uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }
    suspend fun builder(context: Context, kind: TimerAlertKind, pref: UserPreferences): NotificationCompat.Builder {
        val sound = (context.applicationContext as KinApplication).sounds.flow.first().forKind(kind)
        return NotificationCompat.Builder(context, channel(context,kind,pref.alertMode,sound)).setSmallIcon(R.drawable.ic_kin)
            .setSilent(AlertPolicy.silent(pref.alertMode,LocalTime.now().hour,pref.quietStart,pref.quietEnd))
            .setOnlyAlertOnce(true).setAutoCancel(true)
    }

    suspend fun focus(context: Context, value: FocusSession) {
        if (!Notifications.allowed(context)) return
        val pref = (context.applicationContext as KinApplication).preferences.flow.first()
        val open = PendingIntent.getActivity(context,0,Intent(context,ReminderActivity::class.java)
            .setData("kin-focus://${value.id}/complete".toUri()).putExtra("route","focus"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getBroadcast(context,0,Intent(context,FocusCompletionReceiver::class.java)
            .setData("kin-focus://${value.id}/dismiss".toUri()).putExtra("focusId",value.id),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val takeBreak = PendingIntent.getActivity(context,0,Intent(context,ReminderActivity::class.java)
            .setData("kin-focus://${value.id}/completed-break".toUri()).putExtra("route","focus")
            .putExtra("completedFocusId",value.id).putExtra("chooseBreakAfterFocus",true),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        context.getSystemService(NotificationManager::class.java).activeNotifications.filter {
            it.tag?.startsWith("kin.focus.") == true && it.tag != "kin.focus.${value.id}"
        }.forEach { context.getSystemService(NotificationManager::class.java).cancel(it.tag,it.id) }
        post(context,"kin.focus.${value.id}",Int.MAX_VALUE,builder(context,TimerAlertKind.FOCUS,pref)
            .setContentTitle("Congratulations! Focus complete")
            .setContentText("${value.taskTitle} · +15 XP. Stop or take a break.")
            .setContentIntent(open).addAction(0,"Stop",stop).addAction(0,"Take a break",takeBreak).build())
    }
    suspend fun preview(context: Context, kind: TimerAlertKind) {
        if (!Notifications.allowed(context)) return
        val pref = (context.applicationContext as KinApplication).preferences.flow.first()
        post(context,"kin.timer.preview",22013 + kind.ordinal,builder(context,kind,pref)
            .setOnlyAlertOnce(false).setTimeoutAfter(10_000)
            .setContentTitle("${kind.label} · test")
            .setContentText("${pref.alertMode.label}. Quiet hours, phone volume and Do Not Disturb apply.").build())
    }
    fun settings(context: Context, kind: TimerAlertKind, mode: AlertMode, sound: TimerSound = TimerSound()) = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID,channel(context,kind,mode,sound))
    fun cancelFocus(context: Context, id: Long) { context.getSystemService(NotificationManager::class.java).cancel("kin.focus.$id",Int.MAX_VALUE) }
    private fun post(context: Context, tag: String, id: Int, notification: Notification) {
        try { NotificationManagerCompat.from(context).notify(tag,id,notification) } catch (_: SecurityException) { }
    }
}

class FocusCompletionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("focusId",0);if (id <= 0) return
        TimerAlerts.cancelFocus(context,id)
        WorkManager.getInstance(context).enqueueUniqueWork("focus-completion:$id",ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<FocusCompletionWorker>().setInputData(workDataOf("focusId" to id)).build())
    }
}
class FocusCompletionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context,params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as KinApplication;val id = inputData.getLong("focusId",0)
        if (app.database.dao().session(id)?.completed == true) app.completions.dismiss(id)
        return Result.success()
    }
}
