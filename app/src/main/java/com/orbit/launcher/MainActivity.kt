package com.orbit.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.orbit.launcher.ui.OrbitScreen
import com.orbit.launcher.ui.theme.OrbitTheme
import com.orbit.launcher.viewmodel.OrbitViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: OrbitViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OrbitTheme {
                OrbitScreen(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Reflect installs/uninstalls whenever Orbit returns to the foreground.
        viewModel.refresh()
    }
}
