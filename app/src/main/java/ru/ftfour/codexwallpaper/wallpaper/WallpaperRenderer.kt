package ru.ftfour.codexwallpaper.wallpaper

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import ru.ftfour.codexwallpaper.data.AccentColor
import ru.ftfour.codexwallpaper.data.ContentScale
import ru.ftfour.codexwallpaper.data.Formatters
import ru.ftfour.codexwallpaper.data.HorizontalAlignment
import ru.ftfour.codexwallpaper.data.VerticalPosition
import ru.ftfour.codexwallpaper.data.WallpaperState
import java.time.Instant
import kotlin.math.max
import kotlin.math.min

class WallpaperRenderer {
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        letterSpacing = 0.08f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(155, 155, 155)
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        letterSpacing = 0.1f
    }
    private val percentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
    }
    private val secondaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(180, 180, 180)
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
    }
    private val ringTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(42, 42, 42)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val ringAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    fun draw(canvas: Canvas, width: Int, height: Int, state: WallpaperState, now: Instant = Instant.now()) {
        canvas.drawColor(Color.BLACK)
        if (width <= 0 || height <= 0) return

        val scale = when (state.settings.contentScale) {
            ContentScale.SMALL -> 0.88f
            ContentScale.NORMAL -> 1.0f
            ContentScale.LARGE -> 1.16f
        }
        val base = min(width, height) / 390f * scale
        titlePaint.textSize = 18f * base
        labelPaint.textSize = 11f * base
        percentPaint.textSize = 42f * base
        secondaryPaint.textSize = 12f * base
        ringAccentPaint.color = accent(state.settings.accentColor)

        val sidePadding = max(24f * base, width * 0.07f)
        val safeTop = max(32f * base, height * 0.06f)
        val safeBottom = max(36f * base, height * 0.07f)
        val availableWidth = max(1f, width - sidePadding * 2f)
        val contentWidth = min(availableWidth, 360f * base)
        val blockHeight = 324f * base
        val left = when (state.settings.horizontalAlignment) {
            HorizontalAlignment.LEFT -> sidePadding
            HorizontalAlignment.CENTER -> (width - contentWidth) / 2f
            HorizontalAlignment.RIGHT -> width - sidePadding - contentWidth
        }
        val top = when (state.settings.verticalPosition) {
            VerticalPosition.TOP -> safeTop + height * 0.08f
            VerticalPosition.CENTER -> (height - blockHeight) / 2f
            VerticalPosition.BOTTOM -> height - safeBottom - blockHeight
        }.coerceIn(safeTop, max(safeTop, height - safeBottom - blockHeight))

        var y = top
        canvas.drawText("CODEX", left, y + titlePaint.textSize, titlePaint)
        y += 44f * base
        y = drawLimit(canvas, left, y, contentWidth, "5 HOURS", state.limits.fiveHourPercentLeft, "RESET IN ${Formatters.resetIn(now, state.limits.fiveHourResetsAt)}", base)
        y += 22f * base
        y = drawLimit(canvas, left, y, contentWidth, "WEEK", state.limits.weeklyPercentLeft, "RESET ${Formatters.resetDate(state.limits.weeklyResetsAt, state.settings.use24HourFormat)}", base)
        y += 24f * base
        if (state.settings.showLastUpdated) {
            val updated = "UPDATED ${Formatters.time(state.limits.updatedAt, state.settings.use24HourFormat)}"
            canvas.drawText(updated, left, y, secondaryPaint)
            if (!state.hasFreshData) {
                canvas.drawText("NO FRESH DATA", left, y + 20f * base, labelPaint)
            }
        }
    }

    private fun drawLimit(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        label: String,
        percent: Int,
        resetText: String,
        scale: Float,
    ): Float {
        val diameter = min(88f * scale, width * 0.34f)
        val stroke = max(6f * scale, 4f)
        ringTrackPaint.strokeWidth = stroke
        ringAccentPaint.strokeWidth = stroke

        val ringLeft = left
        val ringTop = top
        val bounds = RectF(
            ringLeft + stroke / 2f,
            ringTop + stroke / 2f,
            ringLeft + diameter - stroke / 2f,
            ringTop + diameter - stroke / 2f,
        )
        canvas.drawArc(bounds, -90f, 360f, false, ringTrackPaint)
        canvas.drawArc(bounds, -90f, 360f * percent.coerceIn(0, 100) / 100f, false, ringAccentPaint)

        val percentText = "$percent%"
        val centerY = ringTop + diameter / 2f - (percentPaint.descent() + percentPaint.ascent()) / 2f
        percentPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(percentText, ringLeft + diameter / 2f, centerY, percentPaint)
        percentPaint.textAlign = Paint.Align.LEFT

        val textLeft = ringLeft + diameter + 22f * scale
        val textTop = ringTop + 18f * scale
        canvas.drawText(label, textLeft, textTop + labelPaint.textSize, labelPaint)
        canvas.drawText("LEFT", textLeft, textTop + labelPaint.textSize + 28f * scale, secondaryPaint)
        canvas.drawText(resetText, textLeft, textTop + labelPaint.textSize + 54f * scale, secondaryPaint)
        return top + diameter
    }

    private fun accent(color: AccentColor): Int = when (color) {
        AccentColor.RED -> Color.rgb(229, 57, 53)
        AccentColor.BLUE -> Color.rgb(66, 165, 245)
        AccentColor.GREEN -> Color.rgb(102, 187, 106)
        AccentColor.WHITE -> Color.WHITE
    }
}
