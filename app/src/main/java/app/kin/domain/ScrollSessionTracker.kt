package app.kin.domain

data class ScrollAlert(
    val packageName: String,
    val elapsedMinutes: Int,
    val scrollCount: Int
)

/**
 * Pure monotonic tracker for offline scroll detection.
 * Measures continuous scrolling duration in monitored apps without storing history or reading screen content.
 */
class ScrollSessionTracker(
    private val thresholdMillis: Long,
    private val minScrollEvents: Int = 5,
    private val inactivityResetMillis: Long = 120_000L,
    private val cooldownMillis: Long = 10 * 60_000L
) {
    init {
        require(thresholdMillis > 0) { "Threshold must be positive." }
        require(minScrollEvents > 0) { "Minimum scroll events must be positive." }
    }

    private var activePackage: String? = null
    private var sessionStartElapsed: Long? = null
    private var lastScrollElapsed: Long? = null
    private var scrollEvents: Int = 0
    private var suppressedUntil: Long = 0L

    fun reset(nowElapsed: Long) {
        activePackage = null
        sessionStartElapsed = null
        lastScrollElapsed = null
        scrollEvents = 0
    }

    fun onWindowChanged(packageName: String, nowElapsed: Long, ignoredPackages: Set<String> = emptySet()) {
        if (packageName in ignoredPackages) {
            // Switching back to KIN or home/launcher resets active scrolling.
            if (activePackage != null) reset(nowElapsed)
            return
        }
        if (activePackage != null && activePackage != packageName) {
            reset(nowElapsed)
        }
    }

    fun onScroll(
        packageName: String,
        nowElapsed: Long,
        monitorAllApps: Boolean,
        selectedApps: Set<String>,
        ignoredPackages: Set<String> = emptySet()
    ): ScrollAlert? {
        if (packageName in ignoredPackages) return null
        if (!monitorAllApps && packageName !in selectedApps) return null

        val last = lastScrollElapsed
        val isDifferentApp = activePackage != null && activePackage != packageName
        val isStale = last != null && (nowElapsed - last) > inactivityResetMillis

        if (activePackage == null || isDifferentApp || isStale) {
            activePackage = packageName
            sessionStartElapsed = nowElapsed
            lastScrollElapsed = nowElapsed
            scrollEvents = 1
        } else {
            lastScrollElapsed = nowElapsed
            scrollEvents += 1
        }
        return checkAlert(nowElapsed)
    }

    fun checkAlert(nowElapsed: Long): ScrollAlert? {
        val pkg = activePackage ?: return null
        val start = sessionStartElapsed ?: return null
        val last = lastScrollElapsed ?: return null
        if ((nowElapsed - last) > inactivityResetMillis) {
            reset(nowElapsed)
            return null
        }
        val elapsed = (nowElapsed - start).coerceAtLeast(0L)
        if (elapsed < thresholdMillis || scrollEvents < minScrollEvents || nowElapsed < suppressedUntil) {
            return null
        }
        val minutes = (elapsed / 60_000L).toInt().coerceAtLeast((thresholdMillis / 60_000L).toInt().coerceAtLeast(1))
        val count = scrollEvents
        // Reset session and enforce cooldown so alerts never spam back-to-back.
        sessionStartElapsed = nowElapsed
        lastScrollElapsed = nowElapsed
        scrollEvents = 0
        suppressedUntil = nowElapsed + cooldownMillis
        return ScrollAlert(pkg, minutes, count)
    }

    fun snooze(nowElapsed: Long, minutes: Int = 10) {
        val snoozeMs = minutes.coerceIn(1, 60) * 60_000L
        suppressedUntil = maxOf(suppressedUntil, nowElapsed + snoozeMs)
        reset(nowElapsed)
    }

    fun activeElapsedMillis(nowElapsed: Long): Long {
        val start = sessionStartElapsed ?: return 0L
        val last = lastScrollElapsed ?: return 0L
        if ((nowElapsed - last) > inactivityResetMillis) return 0L
        return (nowElapsed - start).coerceAtLeast(0L)
    }
}
