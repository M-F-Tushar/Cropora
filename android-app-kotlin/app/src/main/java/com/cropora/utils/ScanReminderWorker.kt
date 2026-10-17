package com.cropora.utils

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.cropora.R

class ScanReminderWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : Worker(appContext, workerParameters) {
    override fun doWork(): Result {
        NotificationHelper.showReminder(
            applicationContext,
            applicationContext.getString(R.string.notification_reminder_title),
            applicationContext.getString(R.string.notification_reminder_message)
        )
        return Result.success()
    }
}
