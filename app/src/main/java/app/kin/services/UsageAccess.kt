package app.kin.services

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import app.kin.domain.UsageSessionTracker
import kotlin.math.abs

data class LaunchableApp(val packageName: String, val label: String)

object UsageAccess {
    @Suppress("DEPRECATION")
    fun granted(context: Context): Boolean = try {
        context.getSystemService(AppOpsManager::class.java).checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    } catch (_: SecurityException) { false }

    // The package: deep link isn't supported by every Android/OEM version. The standard
    // list is supported on our minimum API; the user explicitly chooses KIN there.
    fun settingsIntent() = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    @Suppress("DEPRECATION")
    fun apps(context: Context): List<LaunchableApp> = context.packageManager.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .map { LaunchableApp(it.activityInfo.packageName, it.loadLabel(context.packageManager).toString()) }
        .filter { it.packageName != context.packageName }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
}

/** Queries at most once per service tick (15 seconds); app time, never content/scroll gestures. */
class UsagePoller(private val context: Context, private val tracker: UsageSessionTracker) {
    private var wall: Long? = null
    private var elapsed: Long? = null
    fun reset() { wall = null; elapsed = null; tracker.reset(SystemClock.elapsedRealtime()) }

    @Suppress("DEPRECATION") // MOVE_* also works on API 26–28; newer ACTIVITY_* names have the same event values.
    fun poll(selected: Set<String>): String? {
        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        if (!UsageAccess.granted(context)) { reset(); return null }
        val oldWall = wall
        val oldElapsed = elapsed
        val clockChanged = oldWall != null && oldElapsed != null &&
            abs((nowWall - oldWall) - (nowElapsed - oldElapsed)) > 5000
        if (clockChanged) reset()
        val initial = wall == null
        val begin = wall ?: (nowWall - 24 * 60 * 60_000L)
        val events = context.getSystemService(UsageStatsManager::class.java).queryEvents(begin, nowWall)
            ?: run { reset(); return null }
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            // On first use infer the foreground app, but start counting only from now.
            val at = if (initial) nowElapsed else
                nowElapsed - (nowWall - event.timeStamp).coerceIn(0, nowWall - begin)
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> event.packageName?.let { tracker.foreground(it, at) }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> event.packageName?.let { tracker.background(it, at) }
            }
        }
        wall = nowWall; elapsed = nowElapsed
        return tracker.prompt(nowElapsed, selected)
    }
}
