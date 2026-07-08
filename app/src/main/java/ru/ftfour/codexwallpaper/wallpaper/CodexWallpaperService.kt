package ru.ftfour.codexwallpaper.wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import ru.ftfour.codexwallpaper.data.CodexLimitsRepository

class CodexWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = CodexEngine()

    inner class CodexEngine : Engine() {
        private val renderer = WallpaperRenderer()
        private val handler = Handler(Looper.getMainLooper())
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val repository by lazy { CodexLimitsRepository(applicationContext) }
        private var visible = false
        private var lastWidth = 0
        private var lastHeight = 0
        private var state: ru.ftfour.codexwallpaper.data.WallpaperState? = null

        private val redrawRunnable = object : Runnable {
            override fun run() {
                draw()
                if (visible) handler.postDelayed(this, 60_000L)
            }
        }

        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                draw()
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            val filter = IntentFilter(CodexLimitsRepository.ACTION_CODEX_DATA_CHANGED)
            ContextCompat.registerReceiver(
                this@CodexWallpaperService,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            scope.launch {
                repository.wallpaperStateFlow.collect {
                    state = it
                    draw()
                }
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            handler.removeCallbacks(redrawRunnable)
            if (visible) {
                draw()
                handler.postDelayed(redrawRunnable, 60_000L)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            lastWidth = width
            lastHeight = height
            draw()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(redrawRunnable)
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            handler.removeCallbacksAndMessages(null)
            runCatching { unregisterReceiver(receiver) }
            scope.cancel()
            super.onDestroy()
        }

        private fun draw() {
            val current = state ?: return
            val holder = surfaceHolder ?: return
            val canvas = runCatching { holder.lockCanvas() }.getOrNull() ?: return
            try {
                val width = if (lastWidth > 0) lastWidth else canvas.width
                val height = if (lastHeight > 0) lastHeight else canvas.height
                renderer.draw(canvas, width, height, current)
            } finally {
                runCatching { holder.unlockCanvasAndPost(canvas) }
            }
        }
    }
}
