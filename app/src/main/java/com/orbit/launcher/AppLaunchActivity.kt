package com.orbit.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.orbit.launcher.apps.AppLauncher
import com.orbit.launcher.apps.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Invisible trampoline that a widget icon's PendingIntent targets. It validates
 * the requested id against the live inventory, launches that app with an
 * explicit intent, and finishes immediately — no UI, no picker, no Orbit screen.
 */
class AppLaunchActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appId = intent.getStringExtra(EXTRA_APP_ID)
        if (appId.isNullOrBlank()) {
            finish()
            return
        }

        lifecycleScope.launch {
            val app = withContext(Dispatchers.IO) {
                AppRepository(applicationContext)
                    .loadLaunchableApps(packageName)
                    .firstOrNull { it.id == appId }
            }
            if (app != null) {
                AppLauncher.launch(this@AppLaunchActivity, app)
            }
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    companion object {
        const val EXTRA_APP_ID = "com.orbit.launcher.extra.APP_ID"
    }
}
