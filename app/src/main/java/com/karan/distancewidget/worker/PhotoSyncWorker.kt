package com.karan.distancewidget.worker

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.appwidget.AppWidgetManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.karan.distancewidget.data.Prefs
import com.karan.distancewidget.data.StorageHelper
import com.karan.distancewidget.widget.PhotoWidget

class PhotoSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        if (!Prefs.isSetup(applicationContext)) return Result.failure()

        val partnerId = Prefs.getPartnerId(applicationContext) ?: return Result.failure()

        return try {
            // Download partner's photo (skips if already cached and up to date)
            StorageHelper.downloadPartnerPhoto(applicationContext, partnerId)

            // Tell PhotoWidget to redraw
            triggerPhotoWidgetUpdate()

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun triggerPhotoWidgetUpdate() {
        val manager   = AppWidgetManager.getInstance(applicationContext)
        val component = ComponentName(applicationContext, PhotoWidget::class.java)
        val ids       = manager.getAppWidgetIds(component)
        if (ids.isEmpty()) return

        val intent = Intent(applicationContext, PhotoWidget::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        applicationContext.sendBroadcast(intent)
    }
}
