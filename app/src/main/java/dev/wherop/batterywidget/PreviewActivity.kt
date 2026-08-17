package dev.wherop.batterywidget

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

/**
 * On-device counterpart of the prototype's preview harness: the same six sample spans, a
 * level slider and a charging toggle, so the native drawing can be compared against
 * `design/Battery Widget.dc.html` without placing widgets on a homescreen.
 *
 * This is a development tool. The widget itself never shows these controls — it reads live
 * battery state.
 */
class PreviewActivity : Activity() {

    private val spans = listOf(
        Triple("1 × 1", 1, 1),
        Triple("2 × 1", 2, 1),
        Triple("1 × 2", 1, 2),
        Triple("2 × 2", 2, 2),
        Triple("3 × 1", 3, 1),
        Triple("2 × 3", 2, 3),
    )

    private val tiles = mutableListOf<Pair<ImageView, WidgetSizePx>>()
    private lateinit var renderer: BatteryRenderer
    private lateinit var readout: TextView

    private var level = 62
    private var charging = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val density = resources.displayMetrics.density
        renderer = BatteryRenderer(density)
        fun dp(value: Int) = (value * density).toInt()

        BatteryStatus.read(this).let {
            level = it.level
            charging = it.charging
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(48))
            setBackgroundColor(PAGE_BACKGROUND)
        }

        readout = TextView(this).apply {
            setTextColor(TEXT_COLOR)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        }
        root.addView(readout)

        root.addView(SeekBar(this).apply {
            max = 100
            progress = level
            setPadding(0, dp(16), 0, dp(8))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                    level = value
                    redraw()
                }

                override fun onStartTrackingTouch(bar: SeekBar) = Unit
                override fun onStopTrackingTouch(bar: SeekBar) = Unit
            })
        })

        root.addView(Switch(this).apply {
            text = getString(R.string.preview_charging)
            setTextColor(TEXT_COLOR)
            isChecked = charging
            setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
                charging = checked
                redraw()
            }
        })

        // Prototype grid metrics: ~96dp cells with a 16dp gap.
        for ((label, columns, rows) in spans) {
            val size = WidgetSizePx(
                width = dp(columns * CELL_DP + (columns - 1) * GAP_DP),
                height = dp(rows * CELL_DP + (rows - 1) * GAP_DP),
            )
            val image = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(size.width, size.height)
            }
            tiles += image to size

            root.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(24), 0, 0)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                addView(image)
                addView(TextView(context).apply {
                    text = label
                    setTextColor(MUTED_COLOR)
                    setPadding(0, dp(8), 0, 0)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                })
            })
        }

        setContentView(ScrollView(this).apply {
            setBackgroundColor(PAGE_BACKGROUND)
            addView(root)
            // targetSdk 35+ lays the activity out edge to edge, so without this the readout
            // and the level slider sit under the status bar and the action bar.
            applySystemBarInsets(this)
        })

        redraw()
    }

    /**
     * Keeps the scrolling content clear of the status bar, the navigation bar and the action
     * bar, which all draw over it once the activity is edge to edge. A plain [Activity] has
     * no AndroidX insets helper, so this reads the platform insets directly.
     */
    private fun applySystemBarInsets(view: View) {
        val value = TypedValue()
        val actionBarHeight = if (theme.resolveAttribute(android.R.attr.actionBarSize, value, true)) {
            TypedValue.complexToDimensionPixelSize(value.data, resources.displayMetrics)
        } else {
            0
        }

        @Suppress("DEPRECATION")
        view.setOnApplyWindowInsetsListener { target, insets ->
            val left: Int
            val top: Int
            val right: Int
            val bottom: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
                )
                left = bars.left
                top = bars.top
                right = bars.right
                bottom = bars.bottom
            } else {
                left = insets.systemWindowInsetLeft
                top = insets.systemWindowInsetTop
                right = insets.systemWindowInsetRight
                bottom = insets.systemWindowInsetBottom
            }
            target.setPadding(left, top + actionBarHeight, right, bottom)
            insets
        }
        view.requestApplyInsets()
    }

    private fun redraw() {
        readout.text = getString(
            if (charging) R.string.battery_charging else R.string.battery_level,
            level,
        )
        val fillColor = BatteryDesign.fillColor(level)
        for ((image, size) in tiles) {
            image.setImageBitmap(
                renderer.render(
                    widthPx = size.width,
                    heightPx = size.height,
                    fraction = level / 100f,
                    fillColor = fillColor,
                    label = "$level%",
                    charging = charging,
                ),
            )
        }
    }

    private companion object {
        const val CELL_DP = 96
        const val GAP_DP = 16

        // The prototype's default showcase background and its derived text colours.
        val PAGE_BACKGROUND = 0xFF1C2029.toInt()
        val TEXT_COLOR = 0xFFF3F4F6.toInt()
        val MUTED_COLOR = 0xFFA8ADB6.toInt()
    }
}
