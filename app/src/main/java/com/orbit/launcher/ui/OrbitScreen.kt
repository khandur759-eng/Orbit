package com.orbit.launcher.ui

import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbit.launcher.R
import com.orbit.launcher.animation.WheelMotionState
import com.orbit.launcher.ui.theme.OrbitColors
import com.orbit.launcher.viewmodel.BrowseMode
import com.orbit.launcher.viewmodel.OrbitUiState
import com.orbit.launcher.viewmodel.OrbitViewModel

@Composable
fun OrbitScreen(viewModel: OrbitViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }

    val decay = remember { exponentialDecay<Float>(frictionMultiplier = 1.5f) }
    val wheel = remember { WheelMotionState(decay) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Reconcile the wheel whenever the active data set changes, so the first
    // item, centre label and arrangement stay consistent across modes.
    LaunchedEffect(state.mode, state.settings.selectedApps) {
        wheel.reset()
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearMessage()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OrbitColors.Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
        ) {
            OrbitTopBar(
                onEdit = { showEdit = true },
                onSettings = { showSettings = true }
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val apps = state.filteredApps
                when {
                    state.selectedIsEmpty -> EmptySelectionState(
                        onEdit = { showEdit = true },
                        modifier = Modifier.align(Alignment.Center)
                    )

                    apps.isEmpty() && state.query.isNotBlank() -> AppEmptyState(
                        icon = Icons.Filled.Search,
                        title = stringResource(R.string.no_results_title),
                        body = stringResource(R.string.no_results_body),
                        modifier = Modifier.align(Alignment.Center)
                    )

                    else -> CircularAppWheel(
                        apps = apps,
                        wheel = wheel,
                        iconScale = state.settings.iconScale,
                        hapticsEnabled = state.settings.hapticsEnabled,
                        focusAngleRadians = Math.toRadians((state.settings.focusAngleDegrees - 90f).toDouble()).toFloat(),
                        onLaunch = viewModel::launch,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                OrbitSearchBar(
                    query = state.query,
                    onQueryChange = viewModel::onQueryChange,
                    onClear = viewModel::clearQuery
                )
                Spacer(Modifier.height(10.dp))
                OrbitFooter(state = state, onModeChange = viewModel::setMode)
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        )

        if (showEdit) {
            EditAppsScreen(
                allApps = state.allApps,
                initialSelection = state.settings.selectedApps,
                onSave = { ids ->
                    viewModel.saveSelection(ids)
                    showEdit = false
                },
                onCancel = { showEdit = false },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (showSettings) {
            SettingsScreen(
                settings = state.settings,
                appCount = state.allApps.size,
                onBack = { showSettings = false },
                onHapticsChange = viewModel::setHaptics,
                onOrderingChange = viewModel::setOrdering,
                onIconScaleChange = viewModel::setIconScale,
                onFocusAngleChange = viewModel::setFocusAngle,
                onReset = viewModel::resetPreferences,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun OrbitTopBar(onEdit: () -> Unit, onSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Orbit",
                color = OrbitColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = stringResource(R.string.brand_subtitle),
                color = OrbitColors.TextSecondary,
                style = MaterialTheme.typography.labelSmall
            )
        }
        IconButton(onClick = onEdit) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = stringResource(R.string.edit_apps),
                tint = OrbitColors.TextPrimary
            )
        }
        IconButton(onClick = onSettings) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = stringResource(R.string.settings),
                tint = OrbitColors.TextPrimary
            )
        }
    }
}

@Composable
private fun OrbitFooter(state: OrbitUiState, onModeChange: (BrowseMode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${state.filteredApps.size} apps",
            color = OrbitColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(end = 8.dp)
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModePill(stringResource(R.string.selected_apps), state.mode == BrowseMode.SELECTED) {
                onModeChange(BrowseMode.SELECTED)
            }
            Spacer(Modifier.width(6.dp))
            ModePill(stringResource(R.string.all_apps), state.mode == BrowseMode.ALL) {
                onModeChange(BrowseMode.ALL)
            }
            Spacer(Modifier.width(6.dp))
            ModePill(stringResource(R.string.recent), state.mode == BrowseMode.RECENT) {
                onModeChange(BrowseMode.RECENT)
            }
        }
    }
}

@Composable
private fun ModePill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) OrbitColors.Accent else OrbitColors.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (selected) OrbitColors.TextPrimary else OrbitColors.TextSecondary,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun EmptySelectionState(onEdit: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Menu,
            contentDescription = null,
            tint = OrbitColors.TextSecondary,
            modifier = Modifier.size(44.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.empty_selection_title),
            color = OrbitColors.TextPrimary,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.empty_selection_body),
            color = OrbitColors.TextSecondary,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onEdit) {
            Text(stringResource(R.string.edit_selection_action))
        }
    }
}
