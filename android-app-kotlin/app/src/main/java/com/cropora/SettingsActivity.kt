package com.cropora

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.cropora.network.ServerPreferences
import com.cropora.utils.NotificationHelper

class SettingsActivity : ModeAwareActivity() {
    private lateinit var switchReminders: SwitchCompat

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            setRemindersEnabled(true)
        } else {
            Toast.makeText(this, R.string.notification_permission_denied, Toast.LENGTH_SHORT).show()
            // Unchecking fires the listener below, which turns the reminder off.
            switchReminders.isChecked = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(if (advanced) R.layout.activity_settings_advanced else R.layout.activity_settings)
        if (advanced) {
            findViewById<View>(R.id.advBack).setOnClickListener { finish() }
            findViewById<TextView>(R.id.advSettingsVersion).text =
                getString(R.string.adv_version, BuildConfig.VERSION_NAME)
        }

        findViewById<RadioGroup>(R.id.groupLanguage).apply {
            check(if (resources.configuration.locales[0].language == "bn") {
                R.id.radioBangla
            } else {
                R.id.radioEnglish
            })
            setOnCheckedChangeListener { _, checkedId ->
                UiPreferences.setLanguage(this@SettingsActivity, if (checkedId == R.id.radioBangla) "bn" else "en")
            }
        }

        findViewById<SwitchCompat>(R.id.switchAdvancedMode).apply {
            isChecked = UiPreferences.isAdvanced(this@SettingsActivity)
            setOnCheckedChangeListener { _, enabled ->
                UiPreferences.setAdvanced(this@SettingsActivity, enabled)
                // Switch this screen right away; screens in the back stack follow on resume.
                recreate()
            }
        }

        switchReminders = findViewById(R.id.switchScanReminders)
        switchReminders.isChecked = preferences().getBoolean(PREF_REMINDERS, false)
        switchReminders.setOnCheckedChangeListener { _, enabled ->
            if (enabled && requiresNotificationPermission()) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                setRemindersEnabled(enabled)
            }
        }

        val editServerUrl = findViewById<EditText>(R.id.editServerUrl)
        editServerUrl.setText(ServerPreferences.getBaseUrl(this))

        findViewById<Button>(R.id.buttonSaveServerUrl).setOnClickListener {
            val enteredUrl = editServerUrl.text.toString().trim()
            if (!enteredUrl.startsWith("http://") && !enteredUrl.startsWith("https://")) {
                Toast.makeText(this, R.string.server_url_invalid, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            ServerPreferences.setBaseUrl(this, enteredUrl)
            Toast.makeText(this, R.string.server_url_saved, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setRemindersEnabled(enabled: Boolean) {
        preferences().edit().putBoolean(PREF_REMINDERS, enabled).apply()
        if (enabled) {
            NotificationHelper.scheduleDailyReminder(this)
        } else {
            NotificationHelper.cancelDailyReminder(this)
        }
    }

    private fun requiresNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
    }

    // Same file as ServerPreferences so all app settings live in one place.
    private fun preferences() = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)

    companion object {
        private const val PREFERENCES_NAME = "cropora_settings"
        private const val PREF_REMINDERS = "scan_reminders_enabled"
    }
}
