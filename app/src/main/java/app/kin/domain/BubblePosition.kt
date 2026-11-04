package app.kin.domain

/** Physical coordinates keep the bubble on-screen after rotation or a screen-size change. */
object BubblePosition {
    fun x(right: Boolean, screen: Int, window: Int): Int = if (right) (screen - window).coerceAtLeast(0) else 0
    fun y(fraction: Float, screen: Int, window: Int, margin: Int): Int {
        val top = margin.coerceAtMost((screen - window).coerceAtLeast(0))
        val bottom = (screen - window - margin).coerceAtLeast(top)
        return top + ((bottom - top) * fraction.coerceIn(0f,1f)).toInt()
    }
    fun fraction(y: Int, screen: Int, window: Int, margin: Int): Float {
        val top = margin.coerceAtMost((screen - window).coerceAtLeast(0))
        val space = (screen - window - margin - top).coerceAtLeast(0)
        return if (space == 0) 0f else ((y - top).toFloat() / space).coerceIn(0f,1f)
    }
    fun nearestRight(x: Int, screen: Int, window: Int) = x + window / 2 >= screen / 2
}
