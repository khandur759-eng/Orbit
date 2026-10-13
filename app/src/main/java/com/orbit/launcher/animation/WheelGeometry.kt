package com.orbit.launcher.animation

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Pure-Kotlin wheel geometry, shared by the Compose wheel and the widget
 * renderer so the two can never drift apart. No Compose or Android imports.
 *
 * Coordinates are relative to a [size] x [size] square whose origin is top-left.
 */
class WheelGeometry(val size: Float, iconScale: Float = 1f, val count: Int) {

    val radius: Float = size * 0.355f

    val iconPx: Float = (size * 0.15f * iconScale).coerceAtLeast(size * 0.085f)

    val centerIconPx: Float = size * 0.17f

    val maxSlots: Int = floor((TWO_PI * radius) / (iconPx * 1.35f)).toInt().coerceIn(6, 12)

    val visibleCount: Int = if (count in 1..maxSlots) count else maxSlots

    val step: Float = if (visibleCount > 0) TWO_PI / visibleCount else TWO_PI

    /** Centre (x, y) of slot [j]; slot 0 sits at 12 o'clock. */
    fun slotCenter(j: Int, frac: Float = 0f): Pair<Float, Float> {
        val angle = -HALF_PI + (j - frac) * step
        val c = size / 2f
        return (c + radius * cos(angle)) to (c + radius * sin(angle))
    }
}
