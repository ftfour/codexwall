package ru.ftfour.codexwallpaper.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import ru.ftfour.codexwallpaper.data.DataMode
import ru.ftfour.codexwallpaper.data.CodexLimitsRepository
import ru.ftfour.codexwallpaper.data.RefreshInterval
import ru.ftfour.codexwallpaper.data.SettingsRepository
import ru.ftfour.codexwallpaper.data.UrlValidator
import java.util.concurrent.TimeUnit

class CodexSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val repository = CodexLimitsRepository(applicationContext)
        return repository.refreshFromConfiguredServer().fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "codex_limits_periodic_sync"
        private const val UNIQUE_ONE_TIME_WORK_NAME = "codex_limits_manual_sync"

        suspend fun reschedule(context: Context) {
            val settings = SettingsRepository(context).settingsFlow.first()
            val manager = WorkManager.getInstance(context)
            val canSchedule = settings.dataMode == DataMode.SERVER &&
                settings.refreshInterval != RefreshInterval.MANUAL &&
                UrlValidator.isAllowedEndpoint(settings.endpointUrl, ru.ftfour.codexwallpaper.BuildConfig.DEBUG)
            if (!canSchedule) {
                manager.cancelUniqueWork(UNIQUE_WORK_NAME)
                return
            }

            val minutes = when (settings.refreshInterval) {
                RefreshInterval.FIFTEEN_MINUTES -> 15L
                RefreshInterval.THIRTY_MINUTES -> 30L
                RefreshInterval.ONE_HOUR -> 60L
                RefreshInterval.MANUAL -> 15L
            }
            val request = PeriodicWorkRequestBuilder<CodexSyncWorker>(minutes, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            manager.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun runOnce(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<CodexSyncWorker>()
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .build(),
            )
        }
    }
}
