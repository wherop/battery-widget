package dev.wherop.batterywidget

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The sizing math is the part of the port most likely to drift, so it is pinned against the
 * numbers the prototype produces for its own sample tiles (96dp cells, 16dp gaps, density 1,
 * so one CSS pixel is one device pixel and the values are directly comparable).
 */
class BatteryGeometryTest {

    private val density = 1f
    private val tolerance = 0.001f

    @Test
    fun `square widget stands vertical with the prototype's proportions`() {
        // The prototype's 1x1 tile: 96x96, 8px padding.
        val g = BatteryGeometry.of(96f, 96f, density)

        assertEquals(Orientation.VERTICAL, g.orientation)
        // 1:2 silhouette, centred: 40x80 at (28, 8).
        assertEquals(40f, g.silhouette.width, tolerance)
        assertEquals(80f, g.silhouette.height, tolerance)
        assertEquals(28f, g.silhouette.left, tolerance)
        assertEquals(8f, g.silhouette.top, tolerance)

        // bMin = 40 -> bump 14% = 6, radius 22% = 9, bend 6% = 3 (clamped up from 2.4).
        assertEquals(6f, g.bump.height, tolerance)
        assertEquals(9f, g.bodyRadius, tolerance)
        assertEquals(3f, g.bendSize, tolerance)
        // Bump length is 40% of the body width, centred on it.
        assertEquals(16f, g.bump.width, tolerance)
        assertEquals(g.silhouette.centerX, g.bump.centerX, tolerance)
        // The body takes what the bump leaves.
        assertEquals(14f, g.body.top, tolerance)
        assertEquals(88f, g.body.bottom, tolerance)

        // Minimums bite at this size: 0.2 * 40 = 8 -> 14, 0.3 * 40 = 12 -> 21.
        assertEquals(14f, g.fontSize, tolerance)
        assertEquals(21f, g.boltSize, tolerance)
    }

    @Test
    fun `wide widget lies horizontal with the bump on the right`() {
        // The prototype's 2x1 tile: 208x96.
        val g = BatteryGeometry.of(208f, 96f, density)

        assertEquals(Orientation.HORIZONTAL, g.orientation)
        // Height-bound: 2:1 silhouette clamped to the 80px of available height.
        assertEquals(160f, g.silhouette.width, tolerance)
        assertEquals(80f, g.silhouette.height, tolerance)

        // bMin = 80 -> bump 14% = 11.2 -> 11, radius 22% = 17.6 -> 18.
        assertEquals(11f, g.bump.width, tolerance)
        assertEquals(18f, g.bodyRadius, tolerance)
        assertEquals(32f, g.bump.height, tolerance)
        assertEquals(g.silhouette.right, g.bump.right, tolerance)
        assertEquals(g.silhouette.centerY, g.bump.centerY, tolerance)
        assertEquals(g.silhouette.right - 11f, g.body.right, tolerance)
        assertEquals(16f, g.fontSize, tolerance)
    }

    @Test
    fun `vertical fill grows from the bottom`() {
        val g = BatteryGeometry.of(96f, 96f, density)
        val fill = g.fillBox(0.62f)

        assertEquals(g.body.bottom, fill.bottom, tolerance)
        assertEquals(g.body.height * 0.62f, fill.height, tolerance)
        assertEquals(g.body.width, fill.width, tolerance)
        // Empty and full are the degenerate ends of the same box.
        assertEquals(0f, g.fillBox(0f).height, tolerance)
        assertEquals(g.body.height, g.fillBox(1f).height, tolerance)
    }

    @Test
    fun `horizontal fill grows from the left`() {
        val g = BatteryGeometry.of(208f, 96f, density)
        val fill = g.fillBox(0.62f)

        assertEquals(g.body.left, fill.left, tolerance)
        assertEquals(g.body.width * 0.62f, fill.width, tolerance)
        assertEquals(g.body.height, fill.height, tolerance)
    }

    @Test
    fun `bend straddles the fill edge`() {
        val g = BatteryGeometry.of(96f, 96f, density)
        val fill = g.fillBox(0.4f)
        val bend = g.bendBox(0.4f)

        assertEquals(fill.top, bend.centerY, tolerance)
        assertEquals(g.bendSize, bend.height, tolerance)
        assertEquals(g.body.width, bend.width, tolerance)
    }

    @Test
    fun `geometry scales with density`() {
        val onex = BatteryGeometry.of(96f, 96f, 1f)
        val threex = BatteryGeometry.of(288f, 288f, 3f)

        // Same widget in dp at a different density: the shape scales and the minimums scale
        // with it. Derived lengths are rounded to whole pixels at each density, so they land
        // within a pixel of a clean multiple rather than exactly on it.
        assertEquals(onex.silhouette.width * 3f, threex.silhouette.width, tolerance)
        assertEquals(onex.fontSize * 3f, threex.fontSize, tolerance)
        assertEquals(onex.body.height * 3f, threex.body.height, 1.5f)
        assertEquals(onex.bodyRadius * 3f, threex.bodyRadius, 1.5f)
    }

    @Test
    fun `tall widget clamps to the available width`() {
        // The prototype's 1x2 tile: 96x208. A 1:2 shape would need 192px of width.
        val g = BatteryGeometry.of(96f, 208f, density)

        assertEquals(Orientation.VERTICAL, g.orientation)
        assertEquals(80f, g.silhouette.width, tolerance)
        assertEquals(160f, g.silhouette.height, tolerance)
    }

    @Test
    fun `fill colour follows the thresholds`() {
        assertEquals(BatteryDesign.FILL_RED, BatteryDesign.fillColor(0))
        assertEquals(BatteryDesign.FILL_RED, BatteryDesign.fillColor(14))
        assertEquals(BatteryDesign.FILL_AMBER, BatteryDesign.fillColor(15))
        assertEquals(BatteryDesign.FILL_AMBER, BatteryDesign.fillColor(24))
        assertEquals(BatteryDesign.FILL_GREEN, BatteryDesign.fillColor(25))
        assertEquals(BatteryDesign.FILL_GREEN, BatteryDesign.fillColor(100))
    }
}
