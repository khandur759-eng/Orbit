package com.orbit.launcher.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Discovers launchable applications via [PackageManager], loads their labels and
 * real system icons off the main thread, and caches decoded icons in memory.
 */
class AppRepository(private val context: Context) {

    private val iconCache = ConcurrentHashMap<String, ImageBitmap?>()

    /**
     * Returns the sorted list of user-facing, launchable apps. Orbit itself is
     * excluded so it never appears as a redundant self-launch entry.
     */
    suspend fun loadLaunchableApps(selfPackage: String): List<InstalledApp> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val resolveInfos: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }

            val seen = HashSet<String>()
            val result = ArrayList<InstalledApp>(resolveInfos.size)

            for (info in resolveInfos) {
                val activity = info.activityInfo ?: continue
                val pkg = activity.packageName ?: continue
                if (pkg == selfPackage) continue

                val activityName = activity.name
                val id = "$pkg/$activityName"
                // Guard against repeated query results for the same component.
                if (!seen.add(id)) continue

                val label = info.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: pkg
                val icon = iconCache.getOrPut(id) { loadIcon(info, pm) }

                result.add(
                    InstalledApp(
                        id = id,
                        packageName = pkg,
                        activityName = activityName,
                        label = label,
                        icon = icon
                    )
                )
            }

            result.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            result
        }

    /**
     * Resolves a list of stable ids (`pkg/activity`) to app entries, loading only
     * those icons at [iconPx]. Ids whose component no longer resolves (uninstalled)
     * are skipped. Used by the widget path so it never scans the whole inventory.
     */
    suspend fun loadSelectedApps(ids: List<String>, iconPx: Int): List<InstalledApp> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val out = ArrayList<InstalledApp>(ids.size)
            for (id in ids) {
                val parts = id.split("/")
                if (parts.size != 2) continue
                val cn = ComponentName(parts[0], parts[1])
                try {
                    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getActivityInfo(cn, PackageManager.ComponentInfoFlags.of(0L))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getActivityInfo(cn, 0)
                    }
                    val label = info.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: cn.packageName
                    val icon = info.loadIcon(pm)?.toBitmap(iconPx, iconPx)?.asImageBitmap()
                    out.add(
                        InstalledApp(
                            id = id,
                            packageName = cn.packageName,
                            activityName = cn.className,
                            label = label,
                            icon = icon
                        )
                    )
                } catch (t: Throwable) {
                    // Uninstalled or no longer launchable: skip safely.
                }
            }
            out
        }

    private fun loadIcon(info: ResolveInfo, pm: PackageManager): ImageBitmap? = try {
        info.loadIcon(pm)?.toImageBitmap()
    } catch (t: Throwable) {
        null
    }

    private fun Drawable.toImageBitmap(): ImageBitmap? = try {
        val w = intrinsicWidth.takeIf { it > 0 } ?: DEFAULT_ICON_PX
        val h = intrinsicHeight.takeIf { it > 0 } ?: DEFAULT_ICON_PX
        toBitmap(width = w, height = h).asImageBitmap()
    } catch (t: Throwable) {
        null
    }

    /** Clears the in-memory icon cache, e.g. after a package change. */
    fun invalidateIconCache() {
        iconCache.clear()
    }

    private companion object {
        const val DEFAULT_ICON_PX = 144
    }
}
