package ru.ftfour.codexwallpaper

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.ftfour.codexwallpaper.sync.CodexSyncWorker

class CodexWallpaperApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            CodexSyncWorker.reschedule(this@CodexWallpaperApp)
        }
    }
}
