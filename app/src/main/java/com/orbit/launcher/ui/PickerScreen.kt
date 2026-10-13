package com.orbit.launcher.ui

import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbit.launcher.R
import com.orbit.launcher.animation.WheelMotionState
import com.orbit.launcher.ui.theme.OrbitColors
import com.orbit.launcher.viewmodel.PickerViewModel

/**
 * Lightweight picker UI: just the circular wheel plus a close action. Reuses the
 * shared [CircularAppWheel]; launches and finishes so the app is revealed.
 */
@Composable
fun PickerScreen(
    viewModel: PickerViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val focusAngleDegrees by viewModel.focusAngleDegrees.collectAsStateWithLifecycle()

    val decay = remember { exponentialDecay<Float>(frictionMultiplier = 1.5f) }
    val wheel = remember { WheelMotionState(decay) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        val msg = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearMessage()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OrbitColors.Background)
            .systemBarsPadding()
    ) {
        when {
            loading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = OrbitColors.Accent
            )

            apps.isEmpty() -> AppEmptyState(
                icon = Icons.Filled.Menu,
                title = stringResource(R.string.no_selection_title),
                body = stringResource(R.string.no_selection_body),
                modifier = Modifier.align(Alignment.Center)
            )

            else -> CircularAppWheel(
                apps = apps,
                wheel = wheel,
                iconScale = 1f,
                hapticsEnabled = true,
                focusAngleRadians = Math.toRadians((focusAngleDegrees - 90f).toDouble()).toFloat(),
                onLaunch = { app -> viewModel.launch(app, onClose) },
                modifier = Modifier.fillMaxSize()
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.quick_picker),
                color = OrbitColors.TextSecondary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 12.dp).weight(1f)
            )
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.back),
                    tint = OrbitColors.TextPrimary
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        )
    }
}
