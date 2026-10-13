package com.orbit.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orbit.launcher.BuildConfig
import com.orbit.launcher.R
import com.orbit.launcher.preferences.AppOrdering
import com.orbit.launcher.preferences.OrbitSettings
import com.orbit.launcher.ui.theme.OrbitColors
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Real, persisted settings. Each adjustable control is a full-width block with a
 * title, a plain-language description, a live value, and (for the wheel) a small
 * visual — so nothing is a bare, unexplained slider.
 */
@Composable
fun SettingsScreen(
    settings: OrbitSettings,
    appCount: Int,
    onBack: () -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    onOrderingChange: (AppOrdering) -> Unit,
    onIconScaleChange: (Float) -> Unit,
    onFocusAngleChange: (Float) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showResetDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OrbitColors.Background)
            .systemBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = OrbitColors.TextPrimary
                )
            }
            Text(
                text = stringResource(R.string.settings),
                color = OrbitColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleLarge
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SettingRow(title = "Haptic feedback", subtitle = "Vibrate when you pick an app") {
                Switch(
                    checked = settings.hapticsEnabled,
                    onCheckedChange = onHapticsChange
                )
            }

            SettingRow(title = "App ordering", subtitle = "Order of apps around the wheel") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = settings.ordering == AppOrdering.ALPHABETICAL,
                        onClick = { onOrderingChange(AppOrdering.ALPHABETICAL) },
                        label = { Text("A-Z") }
                    )
                    FilterChip(
                        selected = settings.ordering == AppOrdering.RECENT,
                        onClick = { onOrderingChange(AppOrdering.RECENT) },
                        label = { Text("Recent") }
                    )
                }
            }

            SliderSetting(
                title = "Icon size",
                subtitle = "How large the app icons are on the wheel",
                valueLabel = "${(settings.iconScale * 100).roundToInt()}%",
                value = settings.iconScale,
                valueRange = 0.75f..1.35f,
                steps = 11,
                onValueChange = onIconScaleChange
            )

            CentrePositionSetting(
                degrees = settings.focusAngleDegrees,
                onChange = onFocusAngleChange
            )

            Spacer(Modifier.height(8.dp))

            SettingRow(title = "Launchable apps", subtitle = "Detected on this device") {
                Text(
                    text = appCount.toString(),
                    color = OrbitColors.TextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            SettingRow(title = "About", subtitle = "Orbit — Circular App Launcher") {
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    color = OrbitColors.TextSecondary
                )
            }

            Spacer(Modifier.height(12.dp))

            TextButton(onClick = { showResetDialog = true }) {
                Text(text = "Reset Orbit preferences", color = OrbitColors.Accent)
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset preferences?") },
            text = { Text("This clears your selection, history and settings stored by Orbit.") },
            confirmButton = {
                TextButton(onClick = {
                    onReset()
                    showResetDialog = false
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            }
        )
    }
}

/** A full-width slider with a title, description and a live value on the right. */
@Composable
private fun SliderSetting(
    title: String,
    subtitle: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = OrbitColors.TextPrimary, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, color = OrbitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = valueLabel,
                color = OrbitColors.Accent,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Centre position: four clear direction buttons, a compass preview, and an angle slider. */
@Composable
private fun CentrePositionSetting(degrees: Float, onChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Centre position", color = OrbitColors.TextPrimary, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Which spot on the ring shows its app in the centre",
                    color = OrbitColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                text = directionLabel(degrees),
                color = OrbitColors.Accent,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FocusCompass(degrees, Modifier.size(64.dp))
            Spacer(Modifier.width(20.dp))
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FocusChip("Top", 270f, degrees, onChange)
                    FocusChip("Right", 0f, degrees, onChange)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FocusChip("Bottom", 90f, degrees, onChange)
                    FocusChip("Left", 180f, degrees, onChange)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Slider(
            value = degrees,
            onValueChange = onChange,
            valueRange = 0f..360f,
            steps = 23,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Tap a direction, or drag for any angle between them.",
            color = OrbitColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/** Small compass showing where the centre's focus sits on the ring. */
@Composable
private fun FocusCompass(degrees: Float, modifier: Modifier = Modifier) {
    val rad = Math.toRadians(degrees.toDouble()).toFloat()
    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f - 6.dp.toPx()
        drawCircle(color = OrbitColors.Guide, radius = r, center = center, style = Stroke(width = 1.5.dp.toPx()))
        // top tick, so "up" is obvious
        drawLine(
            color = OrbitColors.Guide,
            start = Offset(center.x, center.y - r),
            end = Offset(center.x, center.y - r + 5.dp.toPx()),
            strokeWidth = 1.5.dp.toPx()
        )
        val marker = Offset(center.x + r * cos(rad), center.y + r * sin(rad))
        drawCircle(color = OrbitColors.Accent, radius = 6.dp.toPx(), center = marker)
        drawCircle(color = OrbitColors.TextSecondary, radius = 3.dp.toPx(), center = center)
    }
}

@Composable
private fun FocusChip(label: String, angle: Float, current: Float, onChange: (Float) -> Unit) {
    val diff = (((current - angle) % 360f) + 360f) % 360f
    FilterChip(
        selected = diff < 1f || diff > 359f,
        onClick = { onChange(angle) },
        label = { Text(label) }
    )
}

/** Human-readable name for the focus angle, plus the exact degrees. */
private fun directionLabel(degrees: Float): String {
    val names = arrayOf("Right", "Down-right", "Bottom", "Down-left", "Left", "Up-left", "Top", "Up-right")
    val idx = (((degrees / 45f).roundToInt() % 8) + 8) % 8
    return "${names[idx]} · ${degrees.roundToInt()}°"
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    control: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = OrbitColors.TextPrimary, style = MaterialTheme.typography.bodyLarge)
            Text(text = subtitle, color = OrbitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        control()
    }
}
