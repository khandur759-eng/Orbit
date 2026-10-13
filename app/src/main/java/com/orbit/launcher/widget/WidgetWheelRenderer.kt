package com.orbit.launcher.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.asAndroidBitmap
import com.orbit.launcher.animation.WheelGeometry
import com.orbit.launcher.animation.wrapIndex
import com.orbit.launcher.apps.InstalledApp

/**
 * Draws the whole wheel — guide rings, ring icons, centre icon + label — into one
 * bitmap using the shared [WheelGeometry], so the widget is visually identical to
 * the in-app wheel.
 */
object WidgetWheelRenderer {

    private const val GUIDE = 0xFF292930.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    /** RemoteViews hosts cap per-update bitmap memory; keep the side modest. */
    private const val MAX_SIDE = 640

    fun render(
        apps: List<InstalledApp>,
        offset: Int,
        sizePx: Int,
        iconScale: Float,
        density: Float
    ): Bitmap? {
        if (apps.isEmpty() || sizePx <= 0) return null
        val side = sizePx.coerceAtMost(MAX_SIDE)
        val size = side.toFloat()
        val geo = WheelGeometry(size, iconScale, apps.size)
        val count = apps.size
        val c = side / 2f

        val bmp = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Guide rings.
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = GUIDE
        }
        stroke.strokeWidth = 1.5f * density
        canvas.drawCircle(c, c, geo.radius, stroke)
        stroke.strokeWidth = 1f * density
        stroke.alpha = (255 * 0.4f).toInt()
        canvas.drawCircle(c, c, geo.radius * 0.6f, stroke)

        // Ring icons.
        for (k in 0 until geo.visibleCount) {
            val j = k - geo.visibleCount / 2
            val idx = wrapIndex(offset + j, count)
            val app = apps[idx]
            val (x, y) = geo.slotCenter(j, 0f)
            val focused = j == 0
            val s = if (focused) geo.iconPx * 1.14f else geo.iconPx
            val bitmap = app.icon?.asAndroidBitmap() ?: continue
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                alpha = if (focused) 255 else (255 * 0.82f).toInt()
            }
            canvas.drawBitmap(bitmap, null, RectF(x - s / 2f, y - s / 2f, x + s / 2f, y + s / 2f), paint)
        }

        // Centre: focused app icon + label.
        val focusedApp = apps[wrapIndex(offset, count)]
        val ci = geo.centerIconPx
        focusedApp.icon?.asAndroidBitmap()?.let { bitmap ->
            val top = c - ci * 0.9f
            canvas.drawBitmap(
                bitmap,
                null,
                RectF(c - ci / 2f, top, c + ci / 2f, top + ci),
                Paint(Paint.ANTI_ALIAS_FLAG)
            )
        }

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = WHITE
            textSize = size * 0.045f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val layout = StaticLayout.Builder
            .obtain(focusedApp.label, 0, focusedApp.label.length, textPaint, (geo.radius * 1.1f).toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(2)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(c, c + ci * 0.25f)
        layout.draw(canvas)
        canvas.restore()

        return bmp
    }
}
