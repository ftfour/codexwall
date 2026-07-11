package ru.ftfour.codexwallpaper.ui

import android.app.WallpaperManager
import android.app.DownloadManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.ftfour.codexwallpaper.BuildConfig
import ru.ftfour.codexwallpaper.R
import ru.ftfour.codexwallpaper.data.AccentColor
import ru.ftfour.codexwallpaper.data.AppUpdate
import ru.ftfour.codexwallpaper.data.AppUpdateRepository
import ru.ftfour.codexwallpaper.data.CodexLimits
import ru.ftfour.codexwallpaper.data.CodexLimitsRepository
import ru.ftfour.codexwallpaper.data.ContentScale
import ru.ftfour.codexwallpaper.data.DataMode
import ru.ftfour.codexwallpaper.data.Formatters
import ru.ftfour.codexwallpaper.data.HorizontalAlignment
import ru.ftfour.codexwallpaper.data.RefreshInterval
import ru.ftfour.codexwallpaper.data.SettingsRepository
import ru.ftfour.codexwallpaper.data.VerticalPosition
import ru.ftfour.codexwallpaper.data.WallpaperSettings
import ru.ftfour.codexwallpaper.sync.CodexSyncWorker
import ru.ftfour.codexwallpaper.wallpaper.CodexWallpaperService
import ru.ftfour.codexwallpaper.wallpaper.WallpaperPreviewView
import ru.ftfour.codexwallpaper.widget.CodexLimitsWidgetProvider
import java.time.Instant
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var limitsRepository: CodexLimitsRepository
    private lateinit var appUpdateRepository: AppUpdateRepository
    private lateinit var preview: WallpaperPreviewView
    private lateinit var endpointInput: EditText
    private lateinit var refreshTokenInput: EditText
    private lateinit var errorText: TextView
    private lateinit var lastUpdatedText: TextView
    private lateinit var updateStatusText: TextView
    private lateinit var installUpdateButton: Button
    private lateinit var fivePercentInput: EditText
    private lateinit var fiveResetInput: EditText
    private lateinit var weekPercentInput: EditText
    private lateinit var weekResetInput: EditText
    private lateinit var use24HourSwitch: SwitchCompat
    private lateinit var showLastUpdatedSwitch: SwitchCompat
    private var currentSettings = WallpaperSettings()
    private var bindingControls = true
    private var latestAppUpdate: AppUpdate? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsRepository = SettingsRepository(applicationContext)
        limitsRepository = CodexLimitsRepository(applicationContext, settingsRepository)
        appUpdateRepository = AppUpdateRepository()
        setContentView(createContent())
        bindState()
    }

    private fun createContent(): View {
        val root = ScrollView(this)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(32))
        }
        root.addView(column)

        column.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 24f
        })

        preview = WallpaperPreviewView(this)
        column.addView(preview, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(420)).apply {
            topMargin = dp(16)
            bottomMargin = dp(16)
        })

        column.addView(button(R.string.set_live_wallpaper) { openLiveWallpaperPicker() })
        column.addView(label(R.string.data_mode))
        val modeSpinner = spinner(DataMode.entries, arrayOf(getString(R.string.mode_demo), getString(R.string.mode_server))) {
            updateSettings(currentSettings.copy(dataMode = DataMode.entries[it]), reschedule = true)
        }
        column.addView(modeSpinner)

        column.addView(label(R.string.server_url))
        endpointInput = EditText(this).apply {
            hint = "https://example.com/api/codex-limits"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
            onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) updateSettings(currentSettings.copy(endpointUrl = text.toString().trim()))
            }
        }
        column.addView(endpointInput)
        column.addView(label(R.string.refresh_token))
        refreshTokenInput = EditText(this).apply {
            hint = getString(R.string.refresh_token_hint)
            setSingleLine(true)
            onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) updateSettings(currentSettings.copy(refreshToken = text.toString().trim()))
            }
        }
        column.addView(refreshTokenInput)
        column.addView(button(R.string.test_connection) {
            updateSettings(currentSettings.copy(
                endpointUrl = endpointInput.text.toString().trim(),
                refreshToken = refreshTokenInput.text.toString().trim(),
            ))
            lifecycleScope.launch {
                showStatus(getString(R.string.checking))
                limitsRepository.testConnection(endpointInput.text.toString(), refreshTokenInput.text.toString()).fold(
                    onSuccess = { showStatus(getString(R.string.connection_ok)) },
                    onFailure = { showError(it.localizedMessage ?: getString(R.string.connection_failed)) },
                )
            }
        })

        column.addView(label(R.string.demo_data))
        fivePercentInput = edit("63")
        fiveResetInput = edit("2026-07-06T18:27:00+03:00")
        weekPercentInput = edit("28")
        weekResetInput = edit("2026-07-10T14:20:00+03:00")
        column.addView(label(R.string.five_hour_percent))
        column.addView(fivePercentInput)
        column.addView(label(R.string.five_hour_reset))
        column.addView(fiveResetInput)
        column.addView(label(R.string.weekly_percent))
        column.addView(weekPercentInput)
        column.addView(label(R.string.weekly_reset))
        column.addView(weekResetInput)
        column.addView(button(R.string.save_demo_data) { saveDemoData() })

        column.addView(label(R.string.accent_color))
        val accentSpinner = spinner(AccentColor.entries, arrayOf(getString(R.string.accent_red), getString(R.string.accent_blue), getString(R.string.accent_green), getString(R.string.accent_white))) {
            updateSettings(currentSettings.copy(accentColor = AccentColor.entries[it]))
        }
        column.addView(accentSpinner)

        column.addView(label(R.string.position))
        val positionSpinner = spinner(VerticalPosition.entries, arrayOf(getString(R.string.position_top), getString(R.string.position_center), getString(R.string.position_bottom))) {
            updateSettings(currentSettings.copy(verticalPosition = VerticalPosition.entries[it]))
        }
        column.addView(positionSpinner)

        column.addView(label(R.string.alignment))
        val alignmentSpinner = spinner(HorizontalAlignment.entries, arrayOf(getString(R.string.align_left), getString(R.string.align_center), getString(R.string.align_right))) {
            updateSettings(currentSettings.copy(horizontalAlignment = HorizontalAlignment.entries[it]))
        }
        column.addView(alignmentSpinner)

        column.addView(label(R.string.size))
        val scaleSpinner = spinner(ContentScale.entries, arrayOf(getString(R.string.size_small), getString(R.string.size_normal), getString(R.string.size_large))) {
            updateSettings(currentSettings.copy(contentScale = ContentScale.entries[it]))
        }
        column.addView(scaleSpinner)

        column.addView(label(R.string.refresh_interval))
        val refreshSpinner = spinner(RefreshInterval.entries, arrayOf(getString(R.string.refresh_15), getString(R.string.refresh_30), getString(R.string.refresh_60), getString(R.string.refresh_manual))) {
            updateSettings(currentSettings.copy(refreshInterval = RefreshInterval.entries[it]), reschedule = true)
        }
        column.addView(refreshSpinner)

        showLastUpdatedSwitch = SwitchCompat(this).apply {
            text = getString(R.string.show_last_updated)
            isChecked = true
            setOnCheckedChangeListener { _, checked -> updateSettings(currentSettings.copy(showLastUpdated = checked)) }
        }
        column.addView(showLastUpdatedSwitch)

        use24HourSwitch = SwitchCompat(this).apply {
            text = getString(R.string.use_24_hour)
            isChecked = true
            setOnCheckedChangeListener { _, checked -> updateSettings(currentSettings.copy(use24HourFormat = checked)) }
        }
        column.addView(use24HourSwitch)

        column.addView(button(R.string.refresh_now) {
            lifecycleScope.launch {
                val settings = currentSettings.copy(
                    endpointUrl = endpointInput.text.toString().trim(),
                    refreshToken = refreshTokenInput.text.toString().trim(),
                )
                currentSettings = settings
                settingsRepository.updateSettings(settings)
                CodexSyncWorker.reschedule(applicationContext)
                showStatus(getString(R.string.updating))
                limitsRepository.refreshFromConfiguredServer().fold(
                    onSuccess = {
                        CodexLimitsWidgetProvider.updateAll(applicationContext)
                        showStatus(getString(R.string.updated))
                    },
                    onFailure = {
                        CodexLimitsWidgetProvider.updateAll(applicationContext)
                        showError(it.localizedMessage ?: getString(R.string.connection_failed))
                    },
                )
            }
        })

        lastUpdatedText = label(R.string.last_successful_update)
        errorText = TextView(this).apply { setTextColor(0xffb00020.toInt()) }
        column.addView(lastUpdatedText)
        column.addView(errorText)
        column.addView(button(R.string.battery_settings) { openBatterySettings() })
        column.addView(button(R.string.check_app_update) { checkAppUpdate() })
        updateStatusText = TextView(this).apply { setTextColor(0xff5f6368.toInt()) }
        column.addView(updateStatusText)
        installUpdateButton = button(R.string.app_update_button) { openAppUpdate() }.apply {
            visibility = View.GONE
        }
        column.addView(installUpdateButton)

        lifecycleScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            bindingControls = true
            currentSettings = settings
            modeSpinner.setSelection(settings.dataMode.ordinal)
            endpointInput.setText(settings.endpointUrl)
            refreshTokenInput.setText(settings.refreshToken)
            accentSpinner.setSelection(settings.accentColor.ordinal)
            positionSpinner.setSelection(settings.verticalPosition.ordinal)
            alignmentSpinner.setSelection(settings.horizontalAlignment.ordinal)
            scaleSpinner.setSelection(settings.contentScale.ordinal)
            refreshSpinner.setSelection(settings.refreshInterval.ordinal)
            showLastUpdatedSwitch.isChecked = settings.showLastUpdated
            use24HourSwitch.isChecked = settings.use24HourFormat
            bindingControls = false
        }
        return root
    }

    private fun bindState() {
        lifecycleScope.launch {
            limitsRepository.wallpaperStateFlow.collectLatest { state ->
                currentSettings = state.settings
                preview.state = state
                lastUpdatedText.text = getString(R.string.last_update_value, Formatters.time(state.limits.updatedAt, state.settings.use24HourFormat))
                errorText.text = state.lastError.orEmpty()
            }
        }
        lifecycleScope.launch {
            val demo = settingsRepository.demoLimitsFlow.first()
            fivePercentInput.setText(String.format(Locale.US, "%d", demo.fiveHourPercentLeft))
            fiveResetInput.setText(demo.fiveHourResetsAt.toString())
            weekPercentInput.setText(String.format(Locale.US, "%d", demo.weeklyPercentLeft))
            weekResetInput.setText(demo.weeklyResetsAt.toString())
        }
    }

    private fun saveDemoData() {
        lifecycleScope.launch {
            val limits = runCatching {
                CodexLimits(
                    fiveHourPercentLeft = fivePercentInput.text.toString().toInt().coerceIn(0, 100),
                    fiveHourResetsAt = Instant.parse(fiveResetInput.text.toString().trim()),
                    weeklyPercentLeft = weekPercentInput.text.toString().toInt().coerceIn(0, 100),
                    weeklyResetsAt = Instant.parse(weekResetInput.text.toString().trim()),
                    updatedAt = Instant.now(),
                )
            }.getOrElse {
                showError(getString(R.string.invalid_demo_data))
                return@launch
            }
            settingsRepository.saveDemoLimits(limits)
            updateSettings(currentSettings.copy(dataMode = DataMode.DEMO), reschedule = true)
            CodexLimitsWidgetProvider.updateAll(applicationContext)
            showStatus(getString(R.string.saved))
        }
    }

    private fun updateSettings(settings: WallpaperSettings, reschedule: Boolean = false) {
        if (bindingControls) return
        currentSettings = settings
        lifecycleScope.launch {
            settingsRepository.updateSettings(settings)
            if (reschedule) CodexSyncWorker.reschedule(applicationContext)
            CodexLimitsWidgetProvider.updateAll(applicationContext)
        }
    }

    private fun checkAppUpdate() {
        lifecycleScope.launch {
            updateStatusText.text = getString(R.string.checking)
            installUpdateButton.visibility = View.GONE
            latestAppUpdate = null
            appUpdateRepository.checkLatestRelease(BuildConfig.VERSION_NAME, currentSettings.endpointUrl).fold(
                onSuccess = { update ->
                    latestAppUpdate = update
                    if (update == null) {
                        updateStatusText.text = getString(R.string.app_update_current)
                    } else {
                        updateStatusText.text = getString(R.string.app_update_available, update.version)
                        installUpdateButton.visibility = View.VISIBLE
                    }
                },
                onFailure = {
                    updateStatusText.text = getString(R.string.app_update_failed)
                },
            )
        }
    }

    private fun openAppUpdate() {
        val update = latestAppUpdate ?: return
        runCatching {
            val request = DownloadManager.Request(Uri.parse(update.downloadUrl))
                .setTitle(getString(R.string.app_update_download_title, update.version))
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, "codex-limits-wallpaper-${update.version}.apk")
            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            updateStatusText.text = getString(R.string.app_update_downloading)
        }.getOrElse {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.pageUrl)))
        }
    }

    private fun openLiveWallpaperPicker() {
        val component = ComponentName(this, CodexWallpaperService::class.java)
        val direct = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
        runCatching { startActivity(direct) }.getOrElse {
            startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        }
    }

    private fun openBatterySettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    private fun showStatus(message: String) {
        errorText.setTextColor(0xff5f6368.toInt())
        errorText.text = message
    }

    private fun showError(message: String) {
        errorText.setTextColor(0xffb00020.toInt())
        errorText.text = message
    }

    private fun label(resId: Int): TextView = TextView(this).apply {
        text = getString(resId)
        textSize = 14f
        setPadding(0, dp(14), 0, dp(6))
    }

    private fun edit(value: String): EditText = EditText(this).apply {
        setText(value)
        setSingleLine(true)
    }

    private fun button(resId: Int, action: () -> Unit): Button = Button(this).apply {
        text = getString(resId)
        setOnClickListener { action() }
    }

    private fun <T> spinner(values: List<T>, labels: Array<String>, onSelected: (Int) -> Unit): Spinner =
        Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, labels)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (position in values.indices) onSelected(position)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
