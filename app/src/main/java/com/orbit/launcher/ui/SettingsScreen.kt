package com.orbit.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orbit.launcher.BuildConfig
import com.orbit.launcher.R
import com.orbit.launcher.preferences.AppOrdering
import com.orbit.launcher.preferences.OrbitSettings
import com.orbit.launcher.ui.theme.OrbitColors

/** Real, persisted settings screen. */
@Composable
fun SettingsScreen(
    settings: OrbitSettings,
    appCount: Int,
    onBack: () -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    onOrderingChange: (AppOrdering) -> Unit,
    onIconScaleChange: (Float) -> Unit,
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
                    contentDescription = "Back",
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
            SettingRow(title = "Haptic feedback", subtitle = "Vibrate on icon selection") {
                Switch(
                    checked = settings.hapticsEnabled,
                    onCheckedChange = onHapticsChange
                )
            }

            SettingRow(title = "App ordering", subtitle = "How the wheel is ordered") {
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

            SettingRow(title = "Icon size", subtitle = "Scale ring icons") {
                Slider(
                    value = settings.iconScale,
                    onValueChange = onIconScaleChange,
                    valueRange = 0.75f..1.35f,
                    modifier = Modifier.fillMaxWidth()
                )
            }

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
