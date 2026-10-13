package com.orbit.launcher.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.orbit.launcher.apps.AppLauncher
import com.orbit.launcher.apps.AppRepository
import com.orbit.launcher.apps.InstalledApp
import com.orbit.launcher.apps.WheelApps
import com.orbit.launcher.preferences.AppOrdering
import com.orbit.launcher.preferences.AppPreferences
import com.orbit.launcher.preferences.OrbitSettings
import com.orbit.launcher.widget.OrbitWidgetProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BrowseMode { SELECTED, ALL, RECENT }

data class OrbitUiState(
    val allApps: List<InstalledApp> = emptyList(),
    val query: String = "",
    val mode: BrowseMode = BrowseMode.ALL,
    val settings: OrbitSettings = OrbitSettings(),
    val loading: Boolean = true,
    val message: String? = null
) {
    /** True when the active mode is an intentionally-empty curated selection. */
    val selectedIsEmpty: Boolean
        get() = mode == BrowseMode.SELECTED && settings.selectedApps.isEmpty()

    /** The list actually feeding the wheel: mode filter, then search, then ordering. */
    val filteredApps: List<InstalledApp>
        get() {
            val recents = settings.recents

            // Selection + ordering come from the shared resolver so the main
            // screen, picker and widget can never disagree.
            val base = when (mode) {
                BrowseMode.SELECTED -> WheelApps.resolve(allApps, settings)
                BrowseMode.ALL -> WheelApps.order(allApps, settings)
                BrowseMode.RECENT -> recents.mapNotNull { id -> allApps.firstOrNull { it.id == id } }
            }

            val q = query.trim()
            return if (q.isEmpty()) base else base.filter { it.label.contains(q, ignoreCase = true) }
        }
}

class OrbitViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application)
    private val preferences = AppPreferences(application)

    private val _state = MutableStateFlow(OrbitUiState())
    val state: StateFlow<OrbitUiState> = _state.asStateFlow()

    private var modeInitialized = false

    init {
        viewModelScope.launch {
            preferences.settings.collect { s ->
                _state.update { current ->
                    val mode = if (!modeInitialized) {
                        // First-run: All Apps. Once a selection has ever been saved,
                        // Selected Apps becomes the default view.
                        modeInitialized = true
                        if (s.selectionInitialized) BrowseMode.SELECTED else BrowseMode.ALL
                    } else {
                        current.mode
                    }
                    current.copy(settings = s, mode = mode)
                }
            }
        }
        refresh()
    }

    /** Re-queries launchable apps. Safe to call on every foreground return. */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val apps = repository.loadLaunchableApps(getApplication<Application>().packageName)
            _state.update { it.copy(allApps = apps, loading = false) }
        }
    }

    fun onQueryChange(query: String) = _state.update { it.copy(query = query) }

    fun clearQuery() = _state.update { it.copy(query = "") }

    fun setMode(mode: BrowseMode) {
        modeInitialized = true
        _state.update { it.copy(mode = mode) }
    }

    /** Persists the curated selection and refreshes the widget. */
    fun saveSelection(ids: Set<String>) {
        viewModelScope.launch {
            preferences.setSelectedApps(ids)
            OrbitWidgetProvider.updateAll(getApplication())
        }
    }

    /** Records history first so it survives the activity transition, then launches. */
    fun launch(app: InstalledApp) {
        viewModelScope.launch {
            preferences.recordLaunch(app.id)
            val result = AppLauncher.launch(getApplication(), app)
            if (result.isFailure) {
                repository.invalidateIconCache()
                _state.update { it.copy(message = "That app could not be opened.") }
                refresh()
            }
        }
    }

    fun setHaptics(enabled: Boolean) {
        viewModelScope.launch { preferences.setHaptics(enabled) }
    }

    fun setOrdering(ordering: AppOrdering) {
        viewModelScope.launch {
            preferences.setOrdering(ordering)
            OrbitWidgetProvider.updateAll(getApplication())
        }
    }

    fun setIconScale(scale: Float) {
        viewModelScope.launch {
            preferences.setIconScale(scale)
            OrbitWidgetProvider.updateAll(getApplication())
        }
    }

    fun setFocusAngle(degrees: Float) {
        viewModelScope.launch {
            preferences.setFocusAngle(degrees)
            OrbitWidgetProvider.updateAll(getApplication())
        }
    }

    fun resetPreferences() {
        viewModelScope.launch {
            preferences.reset()
            OrbitWidgetProvider.updateAll(getApplication())
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}
