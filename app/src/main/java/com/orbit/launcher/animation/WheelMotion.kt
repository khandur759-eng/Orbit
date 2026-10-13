package com.orbit.launcher.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs

const val TWO_PI: Float = (2.0 * PI).toFloat()
const val HALF_PI: Float = (PI / 2.0).toFloat()

/**
 * Normalizes an angle into the (-PI, PI] range so that differences across the
 * angular wrap boundary stay small.
 */
fun normalizeAngle(radians: Float): Float {
    var a = radians % TWO_PI
    if (a <= -PI) a += TWO_PI
    if (a > PI) a -= TWO_PI
    return a
}

/** Signed shortest angular difference from [from] to [to]. */
fun angleDelta(from: Float, to: Float): Float = normalizeAngle(to - from)

/** Wraps an integer index into [0, count). Handles negative values. */
fun wrapIndex(index: Int, count: Int): Int {
    if (count <= 0) return 0
    val m = index % count
    return if (m < 0) m + count else m
}

/**
 * Holds the continuous wheel rotation offset (in item units) plus the discrete
 * angular step for the current layout, and mediates drag / fling motion.
 *
 * The offset is intentionally a single continuous float: the logical browsing
 * position is derived from it (rounded) rather than mutated directly, which is
 * what keeps icons from teleporting when the underlying index wraps.
 *
 * Drag updates are plain (non-suspending) writes so they can be called from
 * Compose's restricted pointer-gesture scope; the decay fling runs in a
 * supplied [CoroutineScope] and is cancelled whenever a new drag begins.
 */
class WheelMotionState(private val decaySpec: DecayAnimationSpec<Float>) {

    var offset by mutableFloatStateOf(0f)
        private set

    /** Radians per visible slot for the current layout; set on every layout pass. */
    var stepRadians: Float = 1f

    private var flingJob: Job? = null

    /** Applies finger-driven rotation. Positive [deltaAngle] rotates the ring with the finger. */
    fun applyDrag(deltaAngle: Float) {
        if (stepRadians <= 0f) return
        offset -= deltaAngle / stepRadians
    }

    /** Continues rotation with decay after a fast release. */
    fun startFling(scope: CoroutineScope, velocityItemsPerSecond: Float) {
        if (abs(velocityItemsPerSecond) < MIN_FLING_VELOCITY) return
        flingJob?.cancel()
        flingJob = scope.launch {
            val anim = Animatable(offset)
            anim.animateDecay(velocityItemsPerSecond, decaySpec) {
                offset = value
            }
        }
    }

    /** Cancels any in-flight animation (called when a new gesture begins). */
    fun cancelFling() {
        flingJob?.cancel()
        flingJob = null
    }

    /** Cancels motion and returns the wheel to the first slot (used on mode change). */
    fun reset() {
        cancelFling()
        offset = 0f
    }

    private companion object {
        const val MIN_FLING_VELOCITY = 0.5f
    }
}
