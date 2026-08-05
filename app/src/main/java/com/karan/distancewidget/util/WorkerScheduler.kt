package com.karan.distancewidget.util

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.karan.distancewidget.worker.LocationWorker
import java.util.concurrent.TimeUnit

object WorkerScheduler {
    const val WORK_NAME = "distance_location_sync"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Existing location worker (keep as-is)
        val locationRequest = PeriodicWorkRequestBuilder<LocationWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()

        // New photo sync worker
        val photoRequest = PeriodicWorkRequestBuilder<com.karan.distancewidget.worker.PhotoSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()

        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, locationRequest)
        wm.enqueueUniquePeriodicWork(
            "distance_photo_sync", ExistingPeriodicWorkPolicy.UPDATE, photoRequest)
    }
}
