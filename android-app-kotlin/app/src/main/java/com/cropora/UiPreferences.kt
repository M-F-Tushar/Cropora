package com.cropora

import android.content.Context
import android.content.res.Configuration
import android.app.LocaleManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object UiPreferences {
    fun setLanguage(context: Context, language: String) {
        require(language == "en" || language == "bn")
        context.getSharedPreferences("cropora_settings", Context.MODE_PRIVATE)
            .edit().putString("app_language", language).apply()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
    }

    fun localizedContext(context: Context): Context {
        val language = if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales
                ?.let { locales -> if (locales.isEmpty) null else locales[0].toLanguageTag() }
        } else {
            context.getSharedPreferences("cropora_settings", Context.MODE_PRIVATE)
                .getString("app_language", null)
        } ?: return context
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(language))
        return context.createConfigurationContext(configuration)
    }

    fun isSetupComplete(context: Context): Boolean =
        context.getSharedPreferences("cropora_settings", Context.MODE_PRIVATE)
            .getBoolean("setup_complete", false)

    fun completeSetup(context: Context) {
        context.getSharedPreferences("cropora_settings", Context.MODE_PRIVATE)
            .edit().putBoolean("setup_complete", true).apply()
    }

    fun isAdvanced(context: Context): Boolean =
        context.getSharedPreferences("cropora_settings", Context.MODE_PRIVATE)
            .getBoolean("advanced_ui_enabled", false)

    fun setAdvanced(context: Context, enabled: Boolean) {
        context.getSharedPreferences("cropora_settings", Context.MODE_PRIVATE)
            .edit().putBoolean("advanced_ui_enabled", enabled).apply()
    }
}