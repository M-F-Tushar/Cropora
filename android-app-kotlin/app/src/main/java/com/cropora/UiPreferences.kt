package com.cropora

import android.content.Context

object UiPreferences {
    fun isAdvanced(context: Context): Boolean =
        context.getSharedPreferences("cropora_settings", Context.MODE_PRIVATE)
            .getBoolean("advanced_ui_enabled", false)

    fun setAdvanced(context: Context, enabled: Boolean) {
        context.getSharedPreferences("cropora_settings", Context.MODE_PRIVATE)
            .edit().putBoolean("advanced_ui_enabled", enabled).apply()
    }
}