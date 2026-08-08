package com.karan.distancewidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.karan.distancewidget.R
import com.karan.distancewidget.data.Prefs
import com.karan.distancewidget.data.StorageHelper
import com.karan.distancewidget.worker.PhotoSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PhotoWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_PHOTO_REFRESH = "com.karan.distancewidget.ACTION_PHOTO_REFRESH"

        private val job   = SupervisorJob()
        private val scope = CoroutineScope(job + Dispatchers.IO)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        job.cancel()
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_PHOTO_REFRESH) {
            // Immediate one-time sync
            WorkManager.getInstance(context)
                .enqueue(OneTimeWorkRequestBuilder<PhotoSyncWorker>().build())
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id -> updateWidget(context, appWidgetManager, id) }
    }

    private fun updateWidget(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int
    ) {
        if (!Prefs.isSetup(context)) {
            manager.updateAppWidget(widgetId, buildEmptyViews(context, "open app first"))
            return
        }

        val partnerId      = Prefs.getPartnerId(context) ?: return
        val partnerInitial = Prefs.getPartnerInitial(context) ?: "?"

        // Tap to refresh PendingIntent
        val refreshIntent = Intent(context, PhotoWidget::class.java).apply {
            action = ACTION_PHOTO_REFRESH
        }
        val refreshPi = PendingIntent.getBroadcast(
            context, 1, refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Show loading state immediately
        manager.updateAppWidget(widgetId, buildEmptyViews(context, "loading…", refreshPi))

        scope.launch {
            // Try cached bitmap first (fast path — no network needed)
            val bitmap = StorageHelper.loadCachedBitmap(context, partnerId)

            if (bitmap != null) {
                val ts      = StorageHelper.getCachedTimestamp(context, partnerId)
                val timeAgo = StorageHelper.photoTimeAgo(ts)
                val views   = buildPhotoViews(context, bitmap, partnerInitial, timeAgo, refreshPi)
                manager.updateAppWidget(widgetId, views)
            } else {
                // No cached photo — check Firebase for a new one
                val downloaded = StorageHelper.downloadPartnerPhoto(context, partnerId)
                if (downloaded != null) {
                    val freshBitmap = StorageHelper.loadCachedBitmap(context, partnerId)
                    val ts          = StorageHelper.getCachedTimestamp(context, partnerId)
                    val timeAgo     = StorageHelper.photoTimeAgo(ts)
                    val views       = buildPhotoViews(context,
                        freshBitmap, partnerInitial, timeAgo, refreshPi)
                    manager.updateAppWidget(widgetId, views)
                } else {
                    manager.updateAppWidget(widgetId,
                        buildEmptyViews(context, "waiting for $partnerInitial…", refreshPi))
                }
            }
        }
    }

    private fun buildPhotoViews(
        context: Context,
        bitmap: Bitmap?,
        partnerInitial: String,
        timeAgo: String,
        refreshPi: PendingIntent
    ): RemoteViews {
        return RemoteViews(context.packageName, R.layout.widget_photo).apply {
            if (bitmap != null) {
                setImageViewBitmap(R.id.iv_photo, bitmap)
                setViewVisibility(R.id.iv_photo,    android.view.View.VISIBLE)
                setViewVisibility(R.id.ll_empty,    android.view.View.GONE)
                setViewVisibility(R.id.ll_overlay,  android.view.View.VISIBLE)
            } else {
                setViewVisibility(R.id.iv_photo,    android.view.View.GONE)
                setViewVisibility(R.id.ll_empty,    android.view.View.VISIBLE)
                setViewVisibility(R.id.ll_overlay,  android.view.View.GONE)
            }
            setTextViewText(R.id.tv_from,    "from $partnerInitial")
            setTextViewText(R.id.tv_time_ago, timeAgo)
            setTextViewText(R.id.tv_empty,    "waiting for $partnerInitial…")
            setOnClickPendingIntent(R.id.widget_photo_root, refreshPi)
        }
    }

    private fun buildEmptyViews(
        context: Context,
        message: String,
        refreshPi: PendingIntent? = null
    ): RemoteViews {
        return RemoteViews(context.packageName, R.layout.widget_photo).apply {
            setViewVisibility(R.id.iv_photo,   android.view.View.GONE)
            setViewVisibility(R.id.ll_overlay, android.view.View.GONE)
            setViewVisibility(R.id.ll_empty,   android.view.View.VISIBLE)
            setTextViewText(R.id.tv_empty, message)
            if (refreshPi != null) {
                setOnClickPendingIntent(R.id.widget_photo_root, refreshPi)
            }
        }
    }
}
