package dev.wherop.batterywidget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Draws the battery silhouette into a bitmap, which is what the widget's `ImageView`
 * ultimately shows. Everything here is a direct translation of the CSS in
 * `design/Battery Widget.dc.html`: the layer order below is the prototype's DOM order.
 *
 * Not thread-safe — the paints and paths are reused between frames. Call from one thread.
 */
class BatteryRenderer(private val density: Float) {

    private val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.LEFT
        typeface = robotoBold()
    }
    private val boltFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = BatteryDesign.BOLT_FILL
    }
    private val boltStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = BatteryDesign.BOLT_STROKE
    }

    private val silhouettePath = Path()
    private val bumpPath = Path()
    private val bodyPath = Path()
    private val boltPath = Path()
    private val rect = RectF()

    /**
     * @param fraction  charge as `0f..1f`; drives the fill and the leading-edge bend
     * @param fillColor already-resolved fill colour, so callers can cross-fade it between
     *                  thresholds the way the CSS `transition` does
     * @param label     text drawn inside the battery, e.g. `"62%"`
     */
    fun render(
        widthPx: Int,
        heightPx: Int,
        fraction: Float,
        fillColor: Int,
        label: String,
        charging: Boolean,
    ): Bitmap {
        val w = max(widthPx, 1)
        val h = max(heightPx, 1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val g = BatteryGeometry.of(w.toFloat(), h.toFloat(), density)

        drawTrack(canvas, g)

        // `overflow: hidden` on the body — the fill, the bend and the overlay are all
        // clipped to the rounded body.
        canvas.save()
        bodyPath.reset()
        bodyPath.addRoundRect(g.body.into(rect), g.bodyRadius, g.bodyRadius, Path.Direction.CW)
        canvas.clipPath(bodyPath)

        drawFill(canvas, g, fraction, fillColor)
        drawOverlay(canvas, g, label, charging)

        canvas.restore()
        return bitmap
    }

    /** Bump + body: one flat grey silhouette carrying the shadow, then the 60° sheen. */
    private fun drawTrack(canvas: Canvas, g: BatteryGeometry) {
        silhouettePath.reset()
        silhouettePath.addRoundRect(g.body.into(rect), g.bodyRadius, g.bodyRadius, Path.Direction.CW)
        silhouettePath.addRoundRect(g.bump.into(rect), g.bumpRadii, Path.Direction.CW)

        bumpPath.reset()
        bumpPath.addRoundRect(g.bump.into(rect), g.bumpRadii, Path.Direction.CW)

        // Drawn as one path so bump and body fuse without a seam, and so the drop shadow is
        // cast by the whole silhouette rather than by each part. The bitmap ends at the
        // widget bounds, so at high densities the faintest tail of the shadow is clipped by
        // the 8dp padding — invisible in practice, and there is nowhere else to draw it.
        shapePaint.shader = null
        shapePaint.color = BatteryDesign.TRACK_FLAT
        shapePaint.setShadowLayer(
            blurRadiusForCssBlur(dp(BatteryDesign.SHADOW_BLUR_DP)),
            0f,
            dp(BatteryDesign.SHADOW_DY_DP),
            BatteryDesign.SHADOW_COLOR,
        )
        canvas.drawPath(silhouettePath, shapePaint)
        shapePaint.clearShadowLayer()

        // The sheen is opaque, so it replaces the flat grey; the flat pass above exists to
        // carry the shadow. Body and bump each get their own gradient box, as in CSS.
        val sheen = intArrayOf(BatteryDesign.TRACK_SHEEN_START, BatteryDesign.TRACK_SHEEN_END)
        shapePaint.shader = cssLinearGradient(g.body.into(rect), sheen, null)
        canvas.drawPath(bodyPathFor(g), shapePaint)
        shapePaint.shader = cssLinearGradient(g.bump.into(rect), sheen, null)
        canvas.drawPath(bumpPath, shapePaint)
        shapePaint.shader = null
    }

    private fun bodyPathFor(g: BatteryGeometry): Path {
        bodyPath.reset()
        bodyPath.addRoundRect(g.body.into(rect), g.bodyRadius, g.bodyRadius, Path.Direction.CW)
        return bodyPath
    }

    private fun drawFill(canvas: Canvas, g: BatteryGeometry, fraction: Float, fillColor: Int) {
        val fill = g.fillBox(fraction)
        if (fill.width > 0f && fill.height > 0f) {
            shapePaint.shader = null
            shapePaint.color = fillColor
            canvas.drawRect(fill.into(rect), shapePaint)

            shapePaint.shader = cssLinearGradient(
                fill.into(rect),
                BatteryDesign.FILL_SHEEN_COLORS,
                BatteryDesign.FILL_SHEEN_STOPS,
            )
            canvas.drawRect(fill.into(rect), shapePaint)
            shapePaint.shader = null
        }

        // Leading-edge bend: a flat ellipse in the fill colour straddling the boundary, so
        // the cut between filled and empty reads as a soft edge instead of a hard line.
        shapePaint.color = fillColor
        canvas.drawOval(g.bendBox(fraction).into(rect), shapePaint)
    }

    /** Percentage, with the charging bolt stacked above it (vertical) or left of it (horizontal). */
    private fun drawOverlay(canvas: Canvas, g: BatteryGeometry, label: String, charging: Boolean) {
        textPaint.textSize = g.fontSize
        textPaint.setShadowLayer(
            blurRadiusForCssBlur(dp(BatteryDesign.GLYPH_SHADOW_BLUR_DP)),
            0f,
            dp(BatteryDesign.GLYPH_SHADOW_DY_DP),
            BatteryDesign.GLYPH_SHADOW_COLOR,
        )
        val metrics = textPaint.fontMetrics
        val textWidth = textPaint.measureText(label)
        // `line-height: 1` — the text box is exactly one font size tall.
        val boxHeight = g.fontSize
        val halfLeading = (boxHeight - (metrics.descent - metrics.ascent)) / 2f

        if (g.orientation == Orientation.VERTICAL) {
            val contentHeight = if (charging) g.boltSize + g.overlayGap + boxHeight else boxHeight
            var top = g.body.centerY - contentHeight / 2f
            if (charging) {
                drawBolt(canvas, g.body.centerX - g.boltSize / 2f, top, g.boltSize)
                top += g.boltSize + g.overlayGap
            }
            canvas.drawText(label, g.body.centerX - textWidth / 2f, top + halfLeading - metrics.ascent, textPaint)
        } else {
            val contentWidth = if (charging) g.boltSize + g.overlayGap + textWidth else textWidth
            var left = g.body.centerX - contentWidth / 2f
            if (charging) {
                drawBolt(canvas, left, g.body.centerY - g.boltSize / 2f, g.boltSize)
                left += g.boltSize + g.overlayGap
            }
            val top = g.body.centerY - boxHeight / 2f
            canvas.drawText(label, left, top + halfLeading - metrics.ascent, textPaint)
        }
    }

    private fun drawBolt(canvas: Canvas, left: Float, top: Float, size: Float) {
        val scale = size / BatteryDesign.BOLT_VIEWBOX
        val points = BatteryDesign.BOLT_POINTS
        boltPath.reset()
        boltPath.moveTo(left + points[0] * scale, top + points[1] * scale)
        var i = 2
        while (i < points.size) {
            boltPath.lineTo(left + points[i] * scale, top + points[i + 1] * scale)
            i += 2
        }
        boltPath.close()

        boltFillPaint.setShadowLayer(
            blurRadiusForCssBlur(dp(BatteryDesign.GLYPH_SHADOW_BLUR_DP)),
            0f,
            dp(BatteryDesign.GLYPH_SHADOW_DY_DP),
            BatteryDesign.GLYPH_SHADOW_COLOR,
        )
        canvas.drawPath(boltPath, boltFillPaint)
        boltFillPaint.clearShadowLayer()

        boltStrokePaint.strokeWidth = BatteryDesign.BOLT_STROKE_WIDTH * scale
        canvas.drawPath(boltPath, boltStrokePaint)
    }

    private fun dp(value: Float) = value * density

    /**
     * CSS `linear-gradient(60deg, …)`: 0deg points up and the angle turns clockwise, so the
     * gradient line runs along `(sin a, -cos a)` in screen coordinates and is
     * `|w·sin a| + |h·cos a|` long, centred on the box.
     */
    private fun cssLinearGradient(box: RectF, colors: IntArray, stops: FloatArray?): LinearGradient {
        val angle = Math.toRadians(BatteryDesign.GRADIENT_ANGLE_DEG.toDouble())
        val dx = sin(angle).toFloat()
        val dy = -cos(angle).toFloat()
        val length = max(
            (abs(box.width() * sin(angle)) + abs(box.height() * cos(angle))).toFloat(),
            1f,
        )
        val cx = box.centerX()
        val cy = box.centerY()
        return LinearGradient(
            cx - dx * length / 2f,
            cy - dy * length / 2f,
            cx + dx * length / 2f,
            cy + dy * length / 2f,
            colors,
            stops,
            Shader.TileMode.CLAMP,
        )
    }

    private fun Box.into(out: RectF): RectF {
        out.set(left, top, right, bottom)
        return out
    }

    private companion object {

        /**
         * A CSS shadow blur is 2σ, while Android's shadow/blur radius `r` means
         * `σ = 0.57735·r + 0.5`. Convert so the shadow reads as designed at any density.
         */
        fun blurRadiusForCssBlur(cssBlurPx: Float): Float =
            max((cssBlurPx / 2f - 0.5f) / 0.57735f, 0.1f)

        /** Roboto is the platform sans-serif; weight 700 per the spec. */
        fun robotoBold(): Typeface =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Typeface.create(Typeface.SANS_SERIF, 700, false)
            } else {
                Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
    }
}
