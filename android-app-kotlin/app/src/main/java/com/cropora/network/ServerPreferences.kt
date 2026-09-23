package com.cropora.network

import android.content.Context
import com.cropora.BuildConfig

/**
 * Stores the user-configurable backend server URL (Settings screen) so the app
 * can connect to a different server (e.g. a 24/7 cloud deployment) without a rebuild.
 */
object ServerPreferences {
    private const val PREFS_NAME = "cropora_settings"
    private const val KEY_SERVER_BASE_URL = "server_base_url"

    fun getBaseUrl(context: Context): String {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SERVER_BASE_URL, null)
            ?.trim()
        return if (stored.isNullOrEmpty()) BuildConfig.API_BASE_URL else stored
    }

    fun setBaseUrl(context: Context, baseUrl: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SERVER_BASE_URL, baseUrl.trim())
            .apply()
    }
}
