package com.orbit.launcher

import com.orbit.launcher.animation.WheelGeometry
import com.orbit.launcher.animation.angleDelta
import com.orbit.launcher.animation.normalizeAngle
import com.orbit.launcher.animation.wrapIndex
import com.orbit.launcher.apps.InstalledApp
import com.orbit.launcher.apps.WheelApps
import com.orbit.launcher.preferences.AppOrdering
import com.orbit.launcher.preferences.OrbitSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2

/**
 * Pure-logic tests for the wheel math. These run on the JVM (no device needed)
 * and pin down index wrapping, angle normalization and shortest-path deltas.
 */
class WheelMathTest {

    @Test
    fun wrapIndex_handlesNegativeAndOverflow() {
        assertEquals(0, wrapIndex(0, 5))
        assertEquals(2, wrapIndex(2, 5))
        assertEquals(0, wrapIndex(5, 5))
        assertEquals(1, wrapIndex(11, 5))
        assertEquals(4, wrapIndex(-1, 5))
        assertEquals(3, wrapIndex(-7, 5))
        assertEquals(0, wrapIndex(3, 0))
    }

    @Test
    fun normalizeAngle_staysWithinRange() {
        assertEquals(0f, normalizeAngle(0f), 1e-4f)
        assertEquals(1f, normalizeAngle(1f), 1e-4f)
        // A full turn collapses to zero.
        assertEquals(0f, normalizeAngle(6.2831855f), 1e-3f)
        // Values just above PI wrap to just above -PI.
        val wrapped = normalizeAngle(3.5f)
        assertEquals(-2.7831855f, wrapped, 1e-3f)
    }

    @Test
    fun angleDelta_takesShortestPathAcrossWrapBoundary() {
        // Crossing the +/-PI seam must not produce a near-full-circle jump.
        val across = angleDelta(3.0f, -3.0f)
        assertEquals(0.2831853f, across, 1e-3f)

        val backAcross = angleDelta(-3.0f, 3.0f)
        assertEquals(-0.2831853f, backAcross, 1e-3f)

        // Ordinary movement is unchanged.
        assertEquals(0.5f, angleDelta(1.0f, 1.5f), 1e-4f)
    }

    @Test
    fun angleDelta_isAntisymmetric() {
        val a = 2.7f
        val b = -2.9f
        assertEquals(-angleDelta(b, a), angleDelta(a, b), 1e-4f)
    }

    // --- shared geometry ---

    @Test
    fun wheelGeometry_slotZeroAtTopAndEvenlySpaced() {
        val g = WheelGeometry(size = 400f, iconScale = 1f, count = 12)
        val (x0, y0) = g.slotCenter(0)
        assertEquals(200f, x0, 0.5f)          // centred horizontally
        assertTrue(y0 < 200f)                  // above centre => 12 o'clock

        val (x1, y1) = g.slotCenter(1)
        val a0 = atan2(y0 - 200f, x0 - 200f)
        val a1 = atan2(y1 - 200f, x1 - 200f)
        assertEquals(g.step, normalizeAngle(a1 - a0), 1e-3f)
    }

    @Test
    fun wheelGeometry_visibleCountClamps() {
        assertTrue(WheelGeometry(400f, 1f, 40).visibleCount in 6..12)
        assertTrue(WheelGeometry(120f, 1f, 40).visibleCount in 6..12)
        assertEquals(3, WheelGeometry(400f, 1f, 3).visibleCount)
        assertEquals(1, WheelGeometry(400f, 1f, 1).visibleCount)
    }

    // --- shared app resolver ---

    private fun app(id: String, label: String) =
        InstalledApp(id = id, packageName = "p", activityName = "a", label = label, icon = null)

    @Test
    fun wheelApps_handlesThreeSelectionStates() {
        val all = listOf(app("p/B", "Beta"), app("p/A", "Alpha"))

        assertEquals(2, WheelApps.resolve(all, OrbitSettings()).size)

        val empty = OrbitSettings(selectionInitialized = true, selectedApps = emptySet())
        assertTrue(WheelApps.resolve(all, empty).isEmpty())

        val picked = OrbitSettings(selectionInitialized = true, selectedApps = setOf("p/A"))
        assertEquals(listOf("Alpha"), WheelApps.resolve(all, picked).map { it.label })
    }

    @Test
    fun wheelApps_ordering() {
        val all = listOf(app("p/B", "Beta"), app("p/A", "Alpha"))

        val az = OrbitSettings(ordering = AppOrdering.ALPHABETICAL)
        assertEquals(listOf("Alpha", "Beta"), WheelApps.order(all, az).map { it.label })

        val recent = OrbitSettings(ordering = AppOrdering.RECENT, recents = listOf("p/B", "p/A"))
        assertEquals(listOf("Beta", "Alpha"), WheelApps.order(all, recent).map { it.label })
    }
}
