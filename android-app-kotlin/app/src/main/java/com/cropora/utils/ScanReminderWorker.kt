package com.cropora.utils

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.cropora.R
import com.cropora.UiPreferences

class ScanReminderWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : Worker(appContext, workerParameters) {
    override fun doWork(): Result {
        val context = UiPreferences.localizedContext(applicationContext)
        NotificationHelper.showReminder(
            context,
            context.getString(R.string.notification_reminder_title),
            context.getString(R.string.notification_reminder_message)
        )
        return Result.success()
    }
}
