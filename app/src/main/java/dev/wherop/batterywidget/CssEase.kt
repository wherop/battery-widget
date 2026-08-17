package dev.wherop.batterywidget

/**
 * CSS `ease`, i.e. `cubic-bezier(0.25, 0.1, 0.25, 1)` — the timing function the prototype's
 * `transition: … 0.5s ease` uses. Solved by bisection on x, which is plenty for the handful
 * of frames a widget update posts.
 */
internal object CssEase {

    private const val X1 = 0.25f
    private const val Y1 = 0.1f
    private const val X2 = 0.25f
    private const val Y2 = 1f
    private const val ITERATIONS = 12

    fun transform(t: Float): Float {
        if (t <= 0f) return 0f
        if (t >= 1f) return 1f

        var low = 0f
        var high = 1f
        var u = t
        repeat(ITERATIONS) {
            if (bezier(u, X1, X2) < t) low = u else high = u
            u = (low + high) / 2f
        }
        return bezier(u, Y1, Y2)
    }

    /** Cubic Bézier with implicit endpoints at 0 and 1. */
    private fun bezier(u: Float, p1: Float, p2: Float): Float {
        val inv = 1f - u
        return 3f * inv * inv * u * p1 + 3f * inv * u * u * p2 + u * u * u
    }
}
