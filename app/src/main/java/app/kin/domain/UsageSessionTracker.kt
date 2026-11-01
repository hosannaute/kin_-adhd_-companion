package app.kin.domain

/** Monotonic, foreground-only accounting. No app history is retained on disk. */
class UsageSessionTracker(private val thresholdMillis: Long) {
    init { require(thresholdMillis > 0) }
    private var foreground: String? = null
    private var sessionPackage: String? = null
    private var spent = 0L
    private var lastAt: Long? = null
    private var pausedAt = 0L
    private var suppressedUntil = 0L

    fun reset(now: Long) {
        foreground = null; sessionPackage = null; spent = 0; lastAt = now; pausedAt = now
    }
    fun foreground(packageName: String, now: Long) {
        advance(now)
        // Activity transitions within one app can briefly emit PAUSED then RESUMED.
        if (packageName != sessionPackage || (foreground == null && now - pausedAt > 2000)) spent = 0
        foreground = packageName; sessionPackage = packageName
    }
    fun background(packageName: String, now: Long) {
        advance(now)
        if (foreground == packageName) { foreground = null; pausedAt = now }
    }
    private fun advance(now: Long) {
        val previous = lastAt
        if (previous != null && foreground != null) spent += (now - previous).coerceAtLeast(0)
        lastAt = now
    }
    fun prompt(now: Long, selected: Set<String>): String? {
        advance(now)
        val app = foreground ?: return null
        if (app !in selected || spent < thresholdMillis || now < suppressedUntil) return null
        spent = 0
        suppressedUntil = now + 10 * 60_000L
        return app
    }
    fun takeBreak(now: Long, minutes: Int) {
        suppressedUntil = maxOf(suppressedUntil, now + minutes.coerceIn(1, 60) * 60_000L)
        spent = 0; lastAt = now
    }
}
