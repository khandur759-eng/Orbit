package com.orbit.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.orbit.launcher.apps.AppLauncher
import com.orbit.launcher.preferences.AppPreferences
import com.orbit.launcher.widget.OrbitWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Invisible trampoline that a widget icon's PendingIntent targets. It launches the
 * app straight from the id in the extra — no repository scan, so the tap is
 * instant — records the launch for Recent ordering, and finishes immediately.
 */
class AppLaunchActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appId = intent.getStringExtra(EXTRA_APP_ID)
        if (appId.isNullOrBlank()) {
            finish()
            return
        }

        val result = AppLauncher.launchById(this, appId)
        if (result.isFailure) {
            // App removed or not launchable: refresh the widget so it drops the stale icon.
            OrbitWidgetProvider.updateAll(this)
        } else {
            val app = applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                AppPreferences(app).recordLaunch(appId)
            }
        }

        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        const val EXTRA_APP_ID = "com.orbit.launcher.extra.APP_ID"
    }
}
