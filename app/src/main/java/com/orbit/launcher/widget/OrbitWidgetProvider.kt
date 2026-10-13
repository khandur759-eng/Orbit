package com.orbit.launcher.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.asAndroidBitmap
import com.orbit.launcher.AppLaunchActivity
import com.orbit.launcher.MainActivity
import com.orbit.launcher.R
import com.orbit.launcher.animation.WheelGeometry
import com.orbit.launcher.animation.wrapIndex
import com.orbit.launcher.apps.AppRepository
import com.orbit.launcher.apps.InstalledApp
import com.orbit.launcher.apps.WheelApps
import com.orbit.launcher.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "OrbitWidget"
private const val ACTION_PREV = "com.orbit.launcher.widget.ACTION_PREV"
private const val ACTION_NEXT = "com.orbit.launcher.widget.ACTION_NEXT"
private const val PREFS = "orbit_widget_prefs"
private const val HIT_COUNT = 12
private const val LEGACY_SLOTS = 6
private const val ICON_PX = 144
private const val MIN_SIDE_DP = 120f

private val HIT_IDS = intArrayOf(
    R.id.hit_0, R.id.hit_1, R.id.hit_2, R.id.hit_3,
    R.id.hit_4, R.id.hit_5, R.id.hit_6, R.id.hit_7,
    R.id.hit_8, R.id.hit_9, R.id.hit_10, R.id.hit_11
)

private val LEGACY_ICON_IDS = intArrayOf(
    R.id.widget_icon_0, R.id.widget_icon_1, R.id.widget_icon_2,
    R.id.widget_icon_3, R.id.widget_icon_4, R.id.widget_icon_5
)

/**
 * Home-screen widget that IS the circular wheel: one bitmap drawn with the shared
 * geometry, plus transparent per-icon hit zones that launch each app directly.
 *
 * A widget is RemoteViews, so it cannot receive drag/fling — rotation is by the
 * two chevron buttons, one slot per tap. On API 26–30 RemoteViews cannot position
 * views, so a fixed 6-slot legacy layout is used instead.
 */
class OrbitWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        val manager = AppWidgetManager.getInstance(context)
        try {
            when (val action = intent.action) {
                AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                    val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS) ?: return
                    val pending = goAsync()
                    CoroutineScope(Dispatchers.Default).launch {
                        try {
                            for (id in ids) {
                                resetOffset(context, id)
                                safeRender(context, manager, id)
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
                            shiftOffset(context, manager, id, delta)
                        } finally {
                            pending.finish()
                        }
                    }
                }

                AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED -> {
                    val id = intent.getIntExtra(
                        AppWidgetManager.EXTRA_APPWIDGET_ID,
                        AppWidgetManager.INVALID_APPWIDGET_ID
                    )
                    if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                        val pending = goAsync()
                        CoroutineScope(Dispatchers.Default).launch {
                            try {
                                safeRender(context, manager, id)
                            } finally {
                                pending.finish()
                            }
                        }
                    }
                }

                AppWidgetManager.ACTION_APPWIDGET_DELETED -> {
                    val id = intent.getIntExtra(
                        AppWidgetManager.EXTRA_APPWIDGET_ID,
                        AppWidgetManager.INVALID_APPWIDGET_ID
                    )
                    if (id != AppWidgetManager.INVALID_APPWIDGET_ID) clearPrefs(context, id)
                }

                Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REMOVED -> updateAll(context)

                else -> super.onReceive(context, intent)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "onReceive failed", t)
        }
    }

    companion object {
        /** Re-renders every placed widget and resets rotation (selection changed). */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, OrbitWidgetProvider::class.java))
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.Default).launch {
                for (id in ids) {
                    resetOffset(context, id)
                    safeRender(context, manager, id)
                }
            }
        }
    }
}

// --- rotation state ---

private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
private fun getOffset(context: Context, widgetId: Int): Int = prefs(context).getInt("offset_$widgetId", 0)
private fun setOffset(context: Context, widgetId: Int, value: Int) {
    prefs(context).edit().putInt("offset_$widgetId", value).apply()
}
private fun resetOffset(context: Context, widgetId: Int) {
    prefs(context).edit().remove("offset_$widgetId").apply()
}
private fun clearPrefs(context: Context, widgetId: Int) {
    prefs(context).edit().remove("offset_$widgetId").apply()
}

// --- rendering ---

private suspend fun resolveApps(context: Context): List<InstalledApp> {
    val settings = AppPreferences(context).settings.first()
    val repo = AppRepository(context)
    return if (settings.selectionInitialized) {
        // Cheap path: only the selected ids, icons decoded at target size.
        WheelApps.order(repo.loadSelectedApps(settings.selectedApps.toList(), ICON_PX), settings)
    } else {
        WheelApps.resolve(repo.loadLaunchableApps(context.packageName), settings)
    }
}

private suspend fun safeRender(context: Context, manager: AppWidgetManager, widgetId: Int) {
    try {
        render(context, manager, widgetId)
    } catch (t: Throwable) {
        // Never leave the initialLayout showing: fall back to a visible error state.
        Log.e(TAG, "render failed for $widgetId", t)
        val views = RemoteViews(context.packageName, R.layout.orbit_widget)
        views.setViewVisibility(R.id.wheel_box, View.GONE)
        views.setViewVisibility(R.id.widget_pager, View.GONE)
        views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
        views.setTextViewText(R.id.widget_empty_text, context.getString(R.string.widget_error_text))
        views.setOnClickPendingIntent(R.id.widget_edit, openMainPendingIntent(context, widgetId))
        manager.updateAppWidget(widgetId, views)
    }
}

private suspend fun shiftOffset(context: Context, manager: AppWidgetManager, widgetId: Int, delta: Int) {
    val apps = resolveApps(context)
    val count = apps.size
    if (count > 1) setOffset(context, widgetId, ((getOffset(context, widgetId) + delta) % count + count) % count)
    safeRender(context, manager, widgetId)
}

private suspend fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
    val settings = AppPreferences(context).settings.first()
    val apps = resolveApps(context)
    val iconScale = settings.iconScale

    val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val views = RemoteViews(context.packageName, if (modern) R.layout.orbit_widget else R.layout.orbit_widget_legacy)

    if (apps.isEmpty()) {
        views.setViewVisibility(if (modern) R.id.wheel_box else R.id.widget_circle, View.GONE)
        views.setViewVisibility(R.id.widget_pager, View.GONE)
        views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
        views.setTextViewText(R.id.widget_empty_text, context.getString(R.string.widget_empty_text))
        views.setOnClickPendingIntent(R.id.widget_edit, openMainPendingIntent(context, widgetId))
        manager.updateAppWidget(widgetId, views)
        return
    }

    views.setViewVisibility(R.id.widget_empty, View.GONE)

    val count = apps.size
    val offset = getOffset(context, widgetId) % count

    if (modern) {
        renderModern(context, views, widgetId, apps, offset, iconScale)
    } else {
        renderLegacy(context, views, widgetId, apps, offset)
    }

    if (count > 1) {
        views.setViewVisibility(R.id.widget_pager, View.VISIBLE)
        views.setOnClickPendingIntent(R.id.widget_prev, pagePendingIntent(context, widgetId, ACTION_PREV, 90))
        views.setOnClickPendingIntent(R.id.widget_next, pagePendingIntent(context, widgetId, ACTION_NEXT, 91))
    } else {
        views.setViewVisibility(R.id.widget_pager, View.GONE)
    }

    manager.updateAppWidget(widgetId, views)
}

private fun renderModern(
    context: Context,
    views: RemoteViews,
    widgetId: Int,
    apps: List<InstalledApp>,
    offset: Int,
    iconScale: Float
) {
    val count = apps.size
    val density = context.resources.displayMetrics.density
    val opts = AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId)
    val sideDp = minOf(
        minOf(
            opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),
            opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        ),
        minOf(
            opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH),
            opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        )
    ).coerceAtLeast(MIN_SIDE_DP.toInt()) - 12

    val geoDp = WheelGeometry(sideDp.toFloat(), iconScale, count)
    val visible = geoDp.visibleCount

    val sizePx = (sideDp * density).toInt()
    WidgetWheelRenderer.render(apps, offset, sizePx, iconScale, density)
        ?.let { views.setImageViewBitmap(R.id.wheel_image, it) }

    views.setViewLayoutWidth(R.id.wheel_box, sideDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
    views.setViewLayoutHeight(R.id.wheel_box, sideDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)

    for (k in 0 until HIT_COUNT) {
        val hitId = HIT_IDS[k]
        if (k >= visible) {
            views.setViewVisibility(hitId, View.GONE)
            continue
        }
        val j = k - visible / 2
        val idx = wrapIndex(offset + j, count)
        val (x, y) = geoDp.slotCenter(j, 0f)
        val hitDp = maxOf(geoDp.iconPx, 40f)

        views.setViewVisibility(hitId, View.VISIBLE)
        views.setViewLayoutWidth(hitId, hitDp, TypedValue.COMPLEX_UNIT_DIP)
        views.setViewLayoutHeight(hitId, hitDp, TypedValue.COMPLEX_UNIT_DIP)
        views.setViewLayoutMargin(hitId, RemoteViews.MARGIN_START, x - hitDp / 2f, TypedValue.COMPLEX_UNIT_DIP)
        views.setViewLayoutMargin(hitId, RemoteViews.MARGIN_TOP, y - hitDp / 2f, TypedValue.COMPLEX_UNIT_DIP)
        views.setContentDescription(hitId, apps[idx].label)
        views.setOnClickPendingIntent(hitId, launchPendingIntent(context, widgetId, k, apps[idx].id))
    }
}

private fun renderLegacy(
    context: Context,
    views: RemoteViews,
    widgetId: Int,
    apps: List<InstalledApp>,
    offset: Int
) {
    val count = apps.size
    views.setViewVisibility(R.id.widget_circle, View.VISIBLE)
    for (i in 0 until LEGACY_SLOTS) {
        val iconId = LEGACY_ICON_IDS[i]
        val idx = wrapIndex(offset + i, count)
        val app = apps[idx]
        val bitmap = app.icon?.asAndroidBitmap()
        if (bitmap != null) {
            views.setImageViewBitmap(iconId, bitmap)
        } else {
            views.setImageViewResource(iconId, R.drawable.ic_launcher_foreground)
        }
        views.setViewVisibility(iconId, View.VISIBLE)
        views.setContentDescription(iconId, app.label)
        views.setOnClickPendingIntent(iconId, launchPendingIntent(context, widgetId, i, app.id))
    }
}

// --- PendingIntents ---

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
