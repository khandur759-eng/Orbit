package com.orbit.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.orbit.launcher.ui.PickerScreen
import com.orbit.launcher.ui.theme.OrbitTheme
import com.orbit.launcher.viewmodel.PickerViewModel

/**
 * Lightweight entry point for the circular app picker. Reached from the home
 * screen widget and the Quick Picker shortcut — never goes through the full
 * Orbit screen. Finishes itself once an app is launched.
 */
class PickerActivity : ComponentActivity() {

    private val viewModel: PickerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OrbitTheme {
                PickerScreen(
                    viewModel = viewModel,
                    onClose = { finish() }
                )
            }
        }
    }
}
