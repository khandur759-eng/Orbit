package com.orbit.launcher.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.asAndroidBitmap
import com.orbit.launcher.AppLaunchActivity
import com.orbit.launcher.MainActivity
import com.orbit.launcher.R
import com.orbit.launcher.apps.AppRepository
import com.orbit.launcher.apps.InstalledApp
import com.orbit.launcher.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val ACTION_PREV = "com.orbit.launcher.widget.ACTION_PREV"
private const val ACTION_NEXT = "com.orbit.launcher.widget.ACTION_NEXT"

private const val SLOT_COUNT = 6
private const val PREFS = "orbit_widget_prefs"
private const val MAX_ICON_PX = 128

private val SLOT_IDS = intArrayOf(
    R.id.widget_icon_0,
    R.id.widget_icon_1,
    R.id.widget_icon_2,
    R.id.widget_icon_3,
    R.id.widget_icon_4,
    R.id.widget_icon_5
)

/**
 * Home-screen widget that shows the user's saved apps in a circular arrangement.
 * Each icon carries its own direct-launch PendingIntent (via [AppLaunchActivity]);
 * tapping an icon opens that app immediately. Paging (prev/next) happens entirely
 * inside the widget through broadcasts — it never opens an activity.
 */
class OrbitWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        val manager = AppWidgetManager.getInstance(context)
        when (val action = intent.action) {
            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS) ?: return
                val pending = goAsync()
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        for (id in ids) {
                            resetPage(context, id)
                            renderWidget(context, manager, id)
                        }
                    } finally {
                        pending.finish()
                    }
                }
            }

            ACTION_PREV, ACTION_NEXT -> {
                val id = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
                if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
                val delta = if (action == ACTION_NEXT) 1 else -1
                val pending = goAsync()
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        shiftPage(context, manager, id, delta)
                    } finally {
                        pending.finish()
                    }
                }
            }

            AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED,
            AppWidgetManager.ACTION_APPWIDGET_DELETED -> {
                val id = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val pending = goAsync()
                    CoroutineScope(Dispatchers.Default).launch {
                        try {
                            renderWidget(context, manager, id)
                        } finally {
                            pending.finish()
                        }
                    }
                }
            }

            Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REMOVED -> {
                updateAll(context)
            }

            else -> super.onReceive(context, intent)
        }
    }

    companion object {
        /** Re-renders every placed widget and resets paging (selection changed). */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(
                ComponentName(context, OrbitWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.Default).launch {
                for (id in ids) {
                    resetPage(context, id)
                    renderWidget(context, manager, id)
                }
            }
        }
    }
}

private fun widgetPrefs(context: Context) =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

private fun getPage(context: Context, widgetId: Int): Int =
    widgetPrefs(context).getInt("page_$widgetId", 0)

private fun setPage(context: Context, widgetId: Int, page: Int) {
    widgetPrefs(context).edit().putInt("page_$widgetId", page).apply()
}

private fun resetPage(context: Context, widgetId: Int) {
    widgetPrefs(context).edit().remove("page_$widgetId").apply()
}

private suspend fun resolveWidgetApps(context: Context): List<InstalledApp> {
    val settings = AppPreferences(context).settings.first()
    val all = AppRepository(context).loadLaunchableApps(context.packageName)
    return when {
        !settings.selectionInitialized -> all
        settings.selectedApps.isEmpty() -> emptyList()
        else -> all.filter { it.id in settings.selectedApps }
    }
}

private fun pageCount(apps: List<InstalledApp>): Int =
    if (apps.isEmpty()) 0 else (apps.size + SLOT_COUNT - 1) / SLOT_COUNT

private suspend fun shiftPage(
    context: Context,
    manager: AppWidgetManager,
    widgetId: Int,
    delta: Int
) {
    val apps = resolveWidgetApps(context)
    val pages = pageCount(apps)
    if (pages > 1) {
        val next = ((getPage(context, widgetId) + delta) % pages + pages) % pages
        setPage(context, widgetId, next)
    }
    renderWidget(context, manager, widgetId)
}

private suspend fun renderWidget(
    context: Context,
    manager: AppWidgetManager,
    widgetId: Int
) {
    val apps = resolveWidgetApps(context)
    val views = RemoteViews(context.packageName, R.layout.orbit_widget)

    if (apps.isEmpty()) {
        views.setViewVisibility(R.id.widget_circle, View.GONE)
        views.setViewVisibility(R.id.widget_pager, View.GONE)
        views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
        views.setOnClickPendingIntent(R.id.widget_edit, openMainPendingIntent(context, widgetId))
        manager.updateAppWidget(widgetId, views)
        return
    }

    views.setViewVisibility(R.id.widget_empty, View.GONE)
    views.setViewVisibility(R.id.widget_circle, View.VISIBLE)

    val pages = pageCount(apps)
    val page = getPage(context, widgetId).coerceIn(0, pages - 1)
    val pageApps = apps.drop(page * SLOT_COUNT).take(SLOT_COUNT)

    for (i in 0 until SLOT_COUNT) {
        val slotId = SLOT_IDS[i]
        val app = pageApps.getOrNull(i)
        if (app == null) {
            views.setViewVisibility(slotId, View.GONE)
            continue
        }
        views.setViewVisibility(slotId, View.VISIBLE)

        val bitmap = app.icon?.asAndroidBitmap()?.let { bmp ->
            if (bmp.width > MAX_ICON_PX || bmp.height > MAX_ICON_PX) {
                Bitmap.createScaledBitmap(bmp, MAX_ICON_PX, MAX_ICON_PX, true)
            } else {
                bmp
            }
        }
        if (bitmap != null) {
            views.setImageViewBitmap(slotId, bitmap)
        } else {
            views.setImageViewResource(slotId, R.drawable.ic_launcher_foreground)
        }
        views.setContentDescription(slotId, app.label)
        views.setOnClickPendingIntent(slotId, launchPendingIntent(context, widgetId, i, app.id))
    }

    if (pages > 1) {
        views.setViewVisibility(R.id.widget_pager, View.VISIBLE)
        views.setOnClickPendingIntent(
            R.id.widget_prev,
            pagePendingIntent(context, widgetId, ACTION_PREV, 90)
        )
        views.setOnClickPendingIntent(
            R.id.widget_next,
            pagePendingIntent(context, widgetId, ACTION_NEXT, 91)
        )
    } else {
        views.setViewVisibility(R.id.widget_pager, View.GONE)
    }

    manager.updateAppWidget(widgetId, views)
}

private fun launchPendingIntent(
    context: Context,
    widgetId: Int,
    slot: Int,
    appId: String
): PendingIntent {
    val intent = Intent(context, AppLaunchActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        putExtra(AppLaunchActivity.EXTRA_APP_ID, appId)
    }
    // Distinct identity per (widget, slot): request code plus the id in extras.
    return PendingIntent.getActivity(
        context,
        widgetId * 100 + slot,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

private fun pagePendingIntent(
    context: Context,
    widgetId: Int,
    action: String,
    offset: Int
): PendingIntent {
    val intent = Intent(context, OrbitWidgetProvider::class.java).apply {
        this.action = action
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
    }
    return PendingIntent.getBroadcast(
        context,
        widgetId * 100 + offset,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

private fun openMainPendingIntent(context: Context, widgetId: Int): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    return PendingIntent.getActivity(
        context,
        widgetId * 100 + 92,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
