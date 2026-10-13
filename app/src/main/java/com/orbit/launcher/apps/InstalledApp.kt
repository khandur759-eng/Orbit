package com.orbit.launcher.apps

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Immutable description of a single launchable application entry.
 *
 * [id] is a stable identity derived from the component (package + activity) so
 * that recents, the curated selection and list ordering survive refreshes.
 */
data class InstalledApp(
    val id: String,
    val packageName: String,
    val activityName: String?,
    val label: String,
    val icon: ImageBitmap?
)
