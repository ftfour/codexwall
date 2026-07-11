package ru.ftfour.codexwallpaper.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.ftfour.codexwallpaper.R
import ru.ftfour.codexwallpaper.data.AccentColor
import ru.ftfour.codexwallpaper.data.CodexLimitsRepository
import ru.ftfour.codexwallpaper.data.Formatters
import java.time.Instant

class CodexLimitsWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REFRESH -> refreshFromWidget(context)
            AppWidgetManager.ACTION_APPWIDGET_UPDATE,
            CodexLimitsRepository.ACTION_CODEX_DATA_CHANGED,
            -> updateFromBroadcast(context)
            else -> super.onReceive(context, intent)
        }
    }

    private fun refreshFromWidget(context: Context) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                updateAllNow(context.applicationContext, context.getString(R.string.widget_updating))
                val result = CodexLimitsRepository(context.applicationContext).refreshFromConfiguredServer()
                if (result.isFailure) {
                    val message = result.exceptionOrNull()?.localizedMessage
                        ?: context.getString(R.string.connection_failed)
                    updateAllNow(context.applicationContext, message)
                    return@launch
                }
                updateAllNow(context.applicationContext)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun updateFromBroadcast(context: Context) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                updateAllNow(context.applicationContext)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "ru.ftfour.codexwallpaper.CODEX_WIDGET_REFRESH"

        fun updateAll(context: Context, transientFooter: String? = null) {
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                updateAllNow(context.applicationContext, transientFooter)
            }
        }

        private suspend fun updateAllNow(context: Context, transientFooter: String? = null) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CodexLimitsWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val repository = CodexLimitsRepository(context.applicationContext)
            val state = repository.wallpaperStateFlow.first()
            val views = createViews(context, state, transientFooter)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun createViews(
            context: Context,
            state: ru.ftfour.codexwallpaper.data.WallpaperState,
            transientFooter: String?,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.codex_limits_widget)
            val refreshIntent = Intent(context, CodexLimitsWidgetProvider::class.java)
                .setAction(ACTION_REFRESH)
                .setPackage(context.packageName)
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            views.setOnClickPendingIntent(R.id.widget_refresh_button, refreshPendingIntent)
            views.setImageViewBitmap(
                R.id.widget_five_ring,
                ringBitmap(context, state.limits.fiveHourPercentLeft, state.settings.accentColor),
            )
            views.setTextViewText(
                R.id.widget_five_reset,
                context.getString(R.string.widget_reset_in, Formatters.resetIn(Instant.now(), state.limits.fiveHourResetsAt)),
            )
            views.setImageViewBitmap(
                R.id.widget_week_ring,
                ringBitmap(context, state.limits.weeklyPercentLeft, state.settings.accentColor),
            )
            views.setTextViewText(
                R.id.widget_week_reset,
                context.getString(R.string.widget_reset_at, Formatters.resetDate(state.limits.weeklyResetsAt, state.settings.use24HourFormat)),
            )

            val footer = transientFooter
                ?: if (state.hasFreshData) {
                    context.getString(R.string.widget_updated, Formatters.time(state.limits.updatedAt, state.settings.use24HourFormat))
                } else {
                    state.lastError?.takeIf { it.isNotBlank() } ?: context.getString(R.string.widget_no_fresh_data)
                }
            views.setTextViewText(R.id.widget_footer, footer)
            return views
        }

        private fun ringBitmap(context: Context, percent: Int, accentColor: AccentColor): Bitmap {
            val density = context.resources.displayMetrics.density
            val size = (96f * density).toInt().coerceAtLeast(96)
            val stroke = 7f * density
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val bounds = RectF(stroke / 2f, stroke / 2f, size - stroke / 2f, size - stroke / 2f)
            val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(42, 42, 42)
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeCap = Paint.Cap.ROUND
            }
            val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accent(accentColor)
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeCap = Paint.Cap.ROUND
            }
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
                textSize = 24f * density
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            }
            canvas.drawArc(bounds, -90f, 360f, false, trackPaint)
            canvas.drawArc(bounds, -90f, 360f * percent.coerceIn(0, 100) / 100f, false, accentPaint)
            val text = "$percent%"
            val y = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(text, size / 2f, y, textPaint)
            return bitmap
        }

        private fun accent(color: AccentColor): Int = when (color) {
            AccentColor.RED -> Color.rgb(229, 57, 53)
            AccentColor.BLUE -> Color.rgb(66, 165, 245)
            AccentColor.GREEN -> Color.rgb(102, 187, 106)
            AccentColor.WHITE -> Color.WHITE
        }
    }
}
