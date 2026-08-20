package dev.wherop.batterywidget

/**
 * Design tokens for the battery widget, ported from the prototype in
 * `design/Battery Widget.dc.html` (see `design/HANDOFF.md` for the written spec).
 *
 * Absolute lengths are in dp — the prototype's CSS pixels map 1:1 to dp. Ratios are
 * fractions of the silhouette's *shorter* side, which is what keeps a 1x1 and a 2x3
 * widget looking like the same object at different scales.
 */
object BatteryDesign {

    // --- Track (the empty part of the battery) -----------------------------------------
    /** Flat body/bump colour; the sheen below is layered on top of it. */
    val TRACK_FLAT: Int = 0xFFC7C9CE.toInt()
    val TRACK_SHEEN_START: Int = 0xFFCFD1D6.toInt()
    val TRACK_SHEEN_END: Int = 0xFFBEC0C6.toInt()

    // --- Fill thresholds ----------------------------------------------------------------
    val FILL_GREEN: Int = 0xFF1EDB6A.toInt()
    val FILL_AMBER: Int = 0xFFFFB020.toInt()
    val FILL_RED: Int = 0xFFFF453A.toInt()

    const val AMBER_BELOW: Int = 25
    const val RED_BELOW: Int = 15

    /** `< 15%` red, `15..24%` amber, `>= 25%` green. */
    fun fillColor(level: Int): Int = when {
        level < RED_BELOW -> FILL_RED
        level < AMBER_BELOW -> FILL_AMBER
        else -> FILL_GREEN
    }

    // --- Gradients ----------------------------------------------------------------------
    /** Both the track sheen and the fill sheen run along a CSS `60deg` line. */
    const val GRADIENT_ANGLE_DEG: Float = 60f

    /** `rgba(255,255,255,0.16) 0%, rgba(255,255,255,0) 40%, rgba(0,0,0,0.06) 100%` */
    val FILL_SHEEN_COLORS: IntArray = intArrayOf(0x29FFFFFF, 0x00FFFFFF, 0x0F000000)
    val FILL_SHEEN_STOPS: FloatArray = floatArrayOf(0f, 0.4f, 1f)

    // --- Geometry ratios ----------------------------------------------------------------
    /** Breathing room between the widget bounds and the silhouette, per side. */
    const val PADDING_DP: Float = 8f

    /** Silhouette aspect ratio (width : height) when standing vertically. */
    const val VERTICAL_ASPECT: Float = 0.5f

    const val BUMP_THICKNESS_RATIO: Float = 0.14f
    const val BUMP_LENGTH_RATIO: Float = 0.40f
    const val BODY_RADIUS_RATIO: Float = 0.22f
    const val BOLT_SIZE_RATIO: Float = 0.30f
    const val FONT_SIZE_RATIO: Float = 0.20f
    const val BEND_SIZE_RATIO: Float = 0.06f
    const val OVERLAY_GAP_RATIO: Float = 0.04f

    const val MIN_SILHOUETTE_SIDE_DP: Float = 10f
    const val MIN_BUMP_THICKNESS_DP: Float = 3f
    const val MIN_BEND_SIZE_DP: Float = 3f
    const val MIN_BOLT_SIZE_DP: Float = 21f
    const val MIN_FONT_SIZE_DP: Float = 14f
    const val MAX_FONT_SIZE_DP: Float = 22f
    const val MIN_OVERLAY_GAP_DP: Float = 2f

    // --- Shadows ------------------------------------------------------------------------
    /** `drop-shadow(0 3px 8px rgba(0,0,0,0.18))` on the whole silhouette. */
    const val SHADOW_DY_DP: Float = 3f
    const val SHADOW_BLUR_DP: Float = 8f
    const val SHADOW_COLOR: Int = 0x2E000000

    /** `drop-shadow(0 1px 2px rgba(0,0,0,0.25))` on the percentage text and the bolt. */
    const val GLYPH_SHADOW_DY_DP: Float = 1f
    const val GLYPH_SHADOW_BLUR_DP: Float = 2f
    const val GLYPH_SHADOW_COLOR: Int = 0x40000000

    // --- Charging bolt ------------------------------------------------------------------
    /** `M13 2L4 14h6l-1 8 9-12h-6l1-8z` in a 24x24 viewBox, as absolute points. */
    const val BOLT_VIEWBOX: Float = 24f
    val BOLT_POINTS: FloatArray = floatArrayOf(
        13f, 2f,
        4f, 14f,
        10f, 14f,
        9f, 22f,
        18f, 10f,
        12f, 10f,
    )
    val BOLT_FILL: Int = 0xFFFFFFFF.toInt()
    const val BOLT_STROKE: Int = 0x26000000
    /** Stroke width in viewBox units. */
    const val BOLT_STROKE_WIDTH: Float = 0.5f
}
