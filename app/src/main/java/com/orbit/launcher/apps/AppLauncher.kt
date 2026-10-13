package com.orbit.launcher.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Launches a discovered application using its exact launcher component. No app
 * names are hardcoded and no package names are guessed from labels.
 */
object AppLauncher {

    /**
     * Launches directly from a stable id (`pkg/activity`) with no repository scan.
     * Used by the widget trampoline so a tap is instant.
     */
    fun launchById(context: Context, id: String): Result<Unit> {
        val parts = id.split("/")
        if (parts.size != 2) return Result.failure(IllegalArgumentException("bad app id: $id"))
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName(parts[0], parts[1])
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            context.startActivity(intent)
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /**
     * Attempts to launch [app]. Returns a [Result] so the caller can surface a
     * concise message and refresh the inventory instead of crashing.
     */
    fun launch(context: Context, app: InstalledApp): Result<Unit> = try {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            if (!app.activityName.isNullOrBlank()) {
                component = ComponentName(app.packageName, app.activityName)
            } else {
                setPackage(app.packageName)
            }
        }
        context.startActivity(intent)
        Result.success(Unit)
    } catch (t: Throwable) {
        // ActivityNotFoundException, SecurityException and similar are all
        // recoverable here: refresh + tell the user, never crash.
        Result.failure(t)
    }
}
