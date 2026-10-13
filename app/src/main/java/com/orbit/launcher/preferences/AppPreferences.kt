package com.orbit.launcher.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "orbit_prefs")

enum class AppOrdering { ALPHABETICAL, RECENT }

/** Persisted, Orbit-specific user state. */
data class OrbitSettings(
    val hapticsEnabled: Boolean = true,
    val ordering: AppOrdering = AppOrdering.ALPHABETICAL,
    val iconScale: Float = 1.0f,
    val recents: List<String> = emptyList(),
    /** Manually curated wheel selection (stable component ids). */
    val selectedApps: Set<String> = emptySet(),
    /**
     * Distinguishes "never saved a selection" from "saved an empty selection".
     * An empty [selectedApps] alone cannot represent both states.
     */
    val selectionInitialized: Boolean = false
)

/**
 * Local persistence for settings, launch history and the curated wheel
 * selection. Nothing here leaves the device.
 */
class AppPreferences(private val context: Context) {

    private object Keys {
        val HAPTICS = booleanPreferencesKey("haptics_enabled")
        val ORDERING = stringPreferencesKey("ordering")
        val ICON_SCALE = floatPreferencesKey("icon_scale")
        val RECENTS = stringPreferencesKey("recents")
        val SELECTED = stringSetPreferencesKey("selected_apps")
        val SELECTION_INITIALIZED = booleanPreferencesKey("selection_initialized")
    }

    val settings: Flow<OrbitSettings> = context.dataStore.data.map { p ->
        OrbitSettings(
            hapticsEnabled = p[Keys.HAPTICS] ?: true,
            ordering = runCatching {
                AppOrdering.valueOf(p[Keys.ORDERING] ?: AppOrdering.ALPHABETICAL.name)
            }.getOrDefault(AppOrdering.ALPHABETICAL),
            iconScale = p[Keys.ICON_SCALE] ?: 1.0f,
            recents = (p[Keys.RECENTS] ?: "").split("\n").filter { it.isNotBlank() },
            selectedApps = p[Keys.SELECTED] ?: emptySet(),
            selectionInitialized = p[Keys.SELECTION_INITIALIZED] ?: false
        )
    }

    suspend fun setHaptics(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HAPTICS] = enabled }
    }

    suspend fun setOrdering(ordering: AppOrdering) {
        context.dataStore.edit { it[Keys.ORDERING] = ordering.name }
    }

    suspend fun setIconScale(scale: Float) {
        context.dataStore.edit { it[Keys.ICON_SCALE] = scale.coerceIn(MIN_ICON_SCALE, MAX_ICON_SCALE) }
    }

    /**
     * Persists a curated selection. Marking [selectionInitialized] is what makes
     * "Selected Apps" the default view from then on, even for an empty selection.
     */
    suspend fun setSelectedApps(ids: Set<String>) {
        context.dataStore.edit { p ->
            p[Keys.SELECTED] = ids
            p[Keys.SELECTION_INITIALIZED] = true
        }
    }

    /** Records an Orbit-initiated launch, most-recent-first, capped at [MAX_RECENTS]. */
    suspend fun recordLaunch(id: String) {
        context.dataStore.edit { p ->
            val current = (p[Keys.RECENTS] ?: "")
                .split("\n")
                .filter { it.isNotBlank() && it != id }
            p[Keys.RECENTS] = (listOf(id) + current).take(MAX_RECENTS).joinToString("\n")
        }
    }

    suspend fun reset() {
        context.dataStore.edit { it.clear() }
    }

    companion object {
        const val MIN_ICON_SCALE = 0.75f
        const val MAX_ICON_SCALE = 1.35f
        const val MAX_RECENTS = 30
    }
}
