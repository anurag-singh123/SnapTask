package com.example.work

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorkScheduler {

    const val WORK_TRIGGER_NAME = "work_screenshot_content_trigger"
    const val WORK_PERIODIC_SWEEP_NAME = "work_screenshot_periodic_sweep"

    fun scheduleScreenshotMonitoring(context: Context) {
        val workManager = WorkManager.getInstance(context)

        // 1. Content URI trigger (Primary fast detection)
        val constraints = Constraints.Builder().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                addContentUriTrigger(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true)
            }
        }.build()

        val triggerRequest = OneTimeWorkRequestBuilder<ScreenshotDetectionWorker>()
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniqueWork(
            WORK_TRIGGER_NAME,
            ExistingWorkPolicy.REPLACE,
            triggerRequest
        )

        // 2. Periodic fallback sweep (every 15 minutes)
        val periodicRequest = PeriodicWorkRequestBuilder<ScreenshotDetectionWorker>(
            15, TimeUnit.MINUTES,
            5, TimeUnit.MINUTES // 5 min flex
        ).build()

        workManager.enqueueUniquePeriodicWork(
            WORK_PERIODIC_SWEEP_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )
    }

    fun stopMonitoring(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(WORK_TRIGGER_NAME)
        workManager.cancelUniqueWork(WORK_PERIODIC_SWEEP_NAME)
    }
}
