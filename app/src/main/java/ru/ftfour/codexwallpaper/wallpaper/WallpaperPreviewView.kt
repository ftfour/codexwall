package ru.ftfour.codexwallpaper.wallpaper

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import ru.ftfour.codexwallpaper.data.Defaults
import ru.ftfour.codexwallpaper.data.WallpaperSettings
import ru.ftfour.codexwallpaper.data.WallpaperState

class WallpaperPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val renderer = WallpaperRenderer()
    var state: WallpaperState = WallpaperState(Defaults.demoLimits, WallpaperSettings(), hasFreshData = true)
        set(value) {
            field = value
            invalidate()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val desired = (width * 16f / 9f).toInt().coerceAtLeast(360)
        setMeasuredDimension(width, resolveSize(desired, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        renderer.draw(canvas, width, height, state)
    }
}
