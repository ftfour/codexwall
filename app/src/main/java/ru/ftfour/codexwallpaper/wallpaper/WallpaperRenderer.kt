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
    private val barTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(42, 42, 42)
        style = Paint.Style.FILL
    }
    private val barAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
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
        barAccentPaint.color = accent(state.settings.accentColor)

        val sidePadding = max(24f * base, width * 0.07f)
        val safeTop = max(32f * base, height * 0.06f)
        val safeBottom = max(36f * base, height * 0.07f)
        val availableWidth = max(1f, width - sidePadding * 2f)
        val contentWidth = min(availableWidth, 360f * base)
        val blockHeight = 292f * base
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
        y = drawLimit(canvas, left, y, contentWidth, "5 HOURS", "${state.limits.fiveHourPercentLeft}% LEFT", state.limits.fiveHourPercentLeft, "RESET IN ${Formatters.resetIn(now, state.limits.fiveHourResetsAt)}", base)
        y += 28f * base
        y = drawLimit(canvas, left, y, contentWidth, "WEEK", "${state.limits.weeklyPercentLeft}% LEFT", state.limits.weeklyPercentLeft, "RESET ${Formatters.resetDate(state.limits.weeklyResetsAt, state.settings.use24HourFormat)}", base)
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
        percentText: String,
        percent: Int,
        resetText: String,
        scale: Float,
    ): Float {
        var y = top
        canvas.drawText(label, left, y + labelPaint.textSize, labelPaint)
        y += 42f * scale
        canvas.drawText(percentText, left, y, percentPaint)
        y += 18f * scale
        val barTop = y
        val barHeight = max(2f * scale, 2f)
        canvas.drawRoundRect(RectF(left, barTop, left + width, barTop + barHeight), barHeight, barHeight, barTrackPaint)
        canvas.drawRoundRect(RectF(left, barTop, left + width * percent.coerceIn(0, 100) / 100f, barTop + barHeight), barHeight, barHeight, barAccentPaint)
        y += 26f * scale
        canvas.drawText(resetText, left, y, secondaryPaint)
        return y
    }

    private fun accent(color: AccentColor): Int = when (color) {
        AccentColor.RED -> Color.rgb(229, 57, 53)
        AccentColor.BLUE -> Color.rgb(66, 165, 245)
        AccentColor.GREEN -> Color.rgb(102, 187, 106)
        AccentColor.WHITE -> Color.WHITE
    }
}
