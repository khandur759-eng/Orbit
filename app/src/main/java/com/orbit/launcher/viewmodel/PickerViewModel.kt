package com.orbit.launcher.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.orbit.launcher.apps.AppLauncher
import com.orbit.launcher.apps.AppRepository
import com.orbit.launcher.apps.InstalledApp
import com.orbit.launcher.apps.WheelApps
import com.orbit.launcher.preferences.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Lightweight state for the Quick Picker. Shares AppRepository, AppPreferences
 * and AppLauncher with the main screen — one source of truth for inventory and
 * the saved selection.
 */
class PickerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application)
    private val preferences = AppPreferences(application)

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps: StateFlow<List<InstalledApp>> = _apps.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _focusAngleDegrees = MutableStateFlow(270f)
    val focusAngleDegrees: StateFlow<Float> = _focusAngleDegrees.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            val settings = preferences.settings.first()
            val all = repository.loadLaunchableApps(getApplication<Application>().packageName)
            _apps.value = WheelApps.resolve(all, settings)
            _focusAngleDegrees.value = settings.focusAngleDegrees
            _loading.value = false
        }
    }

    fun launch(app: InstalledApp, onLaunched: () -> Unit) {
        viewModelScope.launch {
            preferences.recordLaunch(app.id)
            val result = AppLauncher.launch(getApplication(), app)
            if (result.isFailure) {
                _message.value = "That app could not be opened."
                load()
            } else {
                onLaunched()
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
