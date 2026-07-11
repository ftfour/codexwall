package ru.ftfour.codexwallpaper.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.ftfour.codexwallpaper.R
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
                CodexLimitsRepository(context.applicationContext).refreshFromConfiguredServer()
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
            views.setTextViewText(
                R.id.widget_five_percent,
                context.getString(R.string.widget_percent_left, state.limits.fiveHourPercentLeft),
            )
            views.setProgressBar(R.id.widget_five_progress, 100, state.limits.fiveHourPercentLeft, false)
            views.setTextViewText(
                R.id.widget_five_reset,
                context.getString(R.string.widget_reset_in, Formatters.resetIn(Instant.now(), state.limits.fiveHourResetsAt)),
            )
            views.setTextViewText(
                R.id.widget_week_percent,
                context.getString(R.string.widget_percent_left, state.limits.weeklyPercentLeft),
            )
            views.setProgressBar(R.id.widget_week_progress, 100, state.limits.weeklyPercentLeft, false)
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
    }
}
