package com.orbit.launcher

import com.orbit.launcher.animation.angleDelta
import com.orbit.launcher.animation.normalizeAngle
import com.orbit.launcher.animation.wrapIndex
import org.junit.Assert.assertEquals
import org.junit.Test

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
}
