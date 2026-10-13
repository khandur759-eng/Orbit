package com.orbit.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.orbit.launcher.R
import com.orbit.launcher.animation.HALF_PI
import com.orbit.launcher.animation.WheelGeometry
import com.orbit.launcher.animation.WheelMotionState
import com.orbit.launcher.animation.angleDelta
import com.orbit.launcher.animation.wrapIndex
import com.orbit.launcher.apps.InstalledApp
import com.orbit.launcher.ui.theme.OrbitColors
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Virtualized radial carousel. A continuous floating-point [WheelMotionState.offset]
 * (in item units) drives icon positions; only the visible window of slots is
 * composed, so large inventories stay smooth and wrapping never teleports icons.
 */
@Composable
fun CircularAppWheel(
    apps: List<InstalledApp>,
    wheel: WheelMotionState,
    iconScale: Float,
    hapticsEnabled: Boolean,
    focusAngleRadians: Float = -HALF_PI,
    onLaunch: (InstalledApp) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = modifier.pointerInput(apps.size) {
            val w = size.width.toFloat()
            val h = size.height.toFloat()
            val center = Offset(w / 2f, h / 2f)
            val minDim = min(w, h)
            val tracker = VelocityTracker()

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var lastAngle = atan2(down.position.y - center.y, down.position.x - center.x)
                var dragging = false
                tracker.resetTracking()
                tracker.addPosition(down.uptimeMillis, Offset(wheel.offset, 0f))

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break

                    if (!change.pressed) {
                        if (dragging) {
                            change.consume()
                            wheel.startFling(scope, tracker.calculateVelocity().x)
                        }
                        break
                    }

                    val dist = hypot(change.position.x - center.x, change.position.y - center.y)
                    val angle = atan2(change.position.y - center.y, change.position.x - center.x)

                    if (!dragging) {
                        val moved = (change.position - down.position).getDistance()
                        // Ignore the unstable region near the centre, and only
                        // begin dragging past touch slop so taps stay taps.
                        if (moved > viewConfiguration.touchSlop && dist > minDim * 0.15f) {
                            dragging = true
                            wheel.cancelFling()
                            lastAngle = angle
                        }
                    }

                    if (dragging) {
                        change.consume()
                        val delta = angleDelta(lastAngle, angle)
                        lastAngle = angle
                        wheel.applyDrag(delta)
                        tracker.addPosition(change.uptimeMillis, Offset(wheel.offset, 0f))
                    }
                }
            }
        }
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val minDim = min(widthPx, heightPx)
        val count = apps.size
        val center = Offset(widthPx / 2f, heightPx / 2f)

        // Shared geometry — identical to the widget renderer by construction.
        val geo = WheelGeometry(minDim, iconScale, count, focusAngleRadians)
        val radius = geo.radius
        val iconPx = geo.iconPx
        val visibleCount = geo.visibleCount
        val step = geo.step
        wheel.stepRadians = step

        val offsetValue = wheel.offset
        val baseF = floor(offsetValue)
        val base = baseF.toInt()
        val frac = offsetValue - baseF
        val focusIdx = if (count > 0) wrapIndex(offsetValue.roundToInt(), count) else 0

        // Subtle guide rings behind the icons.
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                color = OrbitColors.Guide,
                radius = radius,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )
            drawCircle(
                color = OrbitColors.Guide.copy(alpha = 0.4f),
                radius = radius * 0.6f,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
        }

        if (count == 0) {
            AppEmptyState(
                icon = Icons.Filled.Menu,
                title = stringResource(R.string.no_selection_title),
                body = stringResource(R.string.no_selection_body),
                modifier = Modifier.align(Alignment.Center)
            )
            return@BoxWithConstraints
        }

        // Central focal area (informational): focused app icon + name.
        val focusedApp = apps[focusIdx]
        val centerIconDp = with(density) { (minDim * 0.17f).toDp() }
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.size(centerIconDp)) {
                val icon = focusedApp.icon
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = focusedApp.label,
                color = OrbitColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(with(density) { (radius * 1.1f).toDp() })
            )
        }

        // Visible ring icons.
        val iconDp = with(density) { iconPx.toDp() }
        for (k in 0 until visibleCount) {
            val j = k - visibleCount / 2
            val idx = wrapIndex(base + j, count)
            val app = apps[idx]
            val angle = focusAngleRadians + (j - frac) * step
            val x = center.x + radius * cos(angle)
            val y = center.y + radius * sin(angle)
            val focused = idx == focusIdx

            AppIcon(
                app = app,
                size = iconDp,
                focused = focused,
                onClick = {
                    if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLaunch(app)
                },
                modifier = Modifier.offset {
                    IntOffset(
                        (x - iconPx / 2f).roundToInt(),
                        (y - iconPx / 2f).roundToInt()
                    )
                }
            )
        }
    }
}
