package dev.wherop.batterywidget

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Which way the battery lies inside the widget. */
enum class Orientation { VERTICAL, HORIZONTAL }

/** A plain rectangle, so this file stays pure Kotlin and unit-testable off-device. */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

/**
 * The full layout of one battery instance, in device pixels, derived from the widget's
 * allotted box. This is a 1:1 port of `renderVals()` in `design/Battery Widget.dc.html`:
 * every ratio and every minimum comes from there, with the prototype's CSS pixels read
 * as dp and scaled by [density].
 *
 * Everything is recomputed per size, so the shape is resolution- and span-independent.
 */
class BatteryGeometry private constructor(
    val orientation: Orientation,
    /** Bump + body together — what the drop shadow is cast from. */
    val silhouette: Box,
    val body: Box,
    val bump: Box,
    val bodyRadius: Float,
    val bumpRadius: Float,
    val boltSize: Float,
    val fontSize: Float,
    val bendSize: Float,
    val overlayGap: Float,
) {

    /**
     * Per-corner radii for the bump in `drawRoundRect`/`Path.addRoundRect` order
     * (top-left x/y, top-right x/y, bottom-right x/y, bottom-left x/y). Only the corners
     * facing away from the body are rounded, giving the pill-cap terminal nub.
     */
    val bumpRadii: FloatArray
        get() = when (orientation) {
            // Bump sits on top: round the top corners.
            Orientation.VERTICAL -> floatArrayOf(
                bumpRadius, bumpRadius, bumpRadius, bumpRadius, 0f, 0f, 0f, 0f,
            )
            // Bump sits on the right: round the right-hand corners.
            Orientation.HORIZONTAL -> floatArrayOf(
                0f, 0f, bumpRadius, bumpRadius, bumpRadius, bumpRadius, 0f, 0f,
            )
        }

    /** The filled portion of the body for a charge [fraction] in `0f..1f`. */
    fun fillBox(fraction: Float): Box {
        val f = fraction.coerceIn(0f, 1f)
        return when (orientation) {
            Orientation.VERTICAL -> Box(body.left, body.bottom - body.height * f, body.right, body.bottom)
            Orientation.HORIZONTAL -> Box(body.left, body.top, body.left + body.width * f, body.bottom)
        }
    }

    /**
     * The soft convex bulge that sits on the boundary between filled and empty: an ellipse
     * spanning the body, [bendSize] thick, centred on the fill edge.
     */
    fun bendBox(fraction: Float): Box {
        val f = fraction.coerceIn(0f, 1f)
        val half = bendSize / 2f
        return when (orientation) {
            Orientation.VERTICAL -> {
                val edge = body.bottom - body.height * f
                Box(body.left, edge - half, body.right, edge + half)
            }
            Orientation.HORIZONTAL -> {
                val edge = body.left + body.width * f
                Box(edge - half, body.top, edge + half, body.bottom)
            }
        }
    }

    companion object {

        /**
         * @param widthPx  widget width in device pixels
         * @param heightPx widget height in device pixels
         * @param density  `DisplayMetrics.density` (dp -> px)
         */
        fun of(widthPx: Float, heightPx: Float, density: Float): BatteryGeometry {
            fun dp(value: Float) = value * density
            fun round(value: Float) = value.roundToInt().toFloat()

            val pad = dp(BatteryDesign.PADDING_DP)
            val availW = max(widthPx - pad * 2f, 1f)
            val availH = max(heightPx - pad * 2f, 1f)

            // The prototype picks the orientation from the grid span (rows >= cols ->
            // vertical). Android exposes the widget's size, not its span, and for square-ish
            // launcher cells the two rules agree — so we read it off the box we were given,
            // which is also what a resize gesture actually changes.
            val orientation = if (widthPx > heightPx) Orientation.HORIZONTAL else Orientation.VERTICAL

            var bw: Float
            var bh: Float
            if (orientation == Orientation.VERTICAL) {
                val ratio = BatteryDesign.VERTICAL_ASPECT
                bh = availH
                bw = bh * ratio
                if (bw > availW) {
                    bw = availW
                    bh = bw / ratio
                }
            } else {
                val ratio = 1f / BatteryDesign.VERTICAL_ASPECT
                bw = availW
                bh = bw / ratio
                if (bh > availH) {
                    bh = availH
                    bw = bh * ratio
                }
            }
            val minSide = dp(BatteryDesign.MIN_SILHOUETTE_SIDE_DP)
            bw = max(bw, minSide)
            bh = max(bh, minSide)

            val bMin = min(bw, bh)
            val bumpThickness = max(dp(BatteryDesign.MIN_BUMP_THICKNESS_DP), round(bMin * BatteryDesign.BUMP_THICKNESS_RATIO))
            val bodyRadius = round(bMin * BatteryDesign.BODY_RADIUS_RATIO)
            val boltSize = max(dp(BatteryDesign.MIN_BOLT_SIZE_DP), round(bMin * BatteryDesign.BOLT_SIZE_RATIO))
            val fontSize = round(bMin * BatteryDesign.FONT_SIZE_RATIO)
                .coerceIn(dp(BatteryDesign.MIN_FONT_SIZE_DP), dp(BatteryDesign.MAX_FONT_SIZE_DP))
            val bendSize = max(dp(BatteryDesign.MIN_BEND_SIZE_DP), round(bMin * BatteryDesign.BEND_SIZE_RATIO))
            val overlayGap = max(dp(BatteryDesign.MIN_OVERLAY_GAP_DP), round(bMin * BatteryDesign.OVERLAY_GAP_RATIO))

            // The silhouette is centred in the widget; bump and body split it end to end.
            val left = (widthPx - bw) / 2f
            val top = (heightPx - bh) / 2f
            val silhouette = Box(left, top, left + bw, top + bh)

            val body: Box
            val bump: Box
            if (orientation == Orientation.VERTICAL) {
                val bumpLen = round(bw * BatteryDesign.BUMP_LENGTH_RATIO)
                bump = Box(
                    silhouette.centerX - bumpLen / 2f,
                    silhouette.top,
                    silhouette.centerX + bumpLen / 2f,
                    silhouette.top + bumpThickness,
                )
                body = Box(silhouette.left, silhouette.top + bumpThickness, silhouette.right, silhouette.bottom)
            } else {
                val bumpLen = round(bh * BatteryDesign.BUMP_LENGTH_RATIO)
                bump = Box(
                    silhouette.right - bumpThickness,
                    silhouette.centerY - bumpLen / 2f,
                    silhouette.right,
                    silhouette.centerY + bumpLen / 2f,
                )
                body = Box(silhouette.left, silhouette.top, silhouette.right - bumpThickness, silhouette.bottom)
            }

            return BatteryGeometry(
                orientation = orientation,
                silhouette = silhouette,
                body = body,
                bump = bump,
                bodyRadius = bodyRadius,
                bumpRadius = round(bumpThickness / 2f),
                boltSize = boltSize,
                fontSize = fontSize,
                bendSize = bendSize,
                overlayGap = overlayGap,
            )
        }
    }
}
