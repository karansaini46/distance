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
        private const val PREFS_NAME   = "photo_widget_slideshow"

        private val job   = SupervisorJob()
        private val scope = CoroutineScope(job + Dispatchers.IO)

        /** Get the current slideshow index for a widget. */
        private fun getIndex(context: Context, widgetId: Int): Int {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt("idx_$widgetId", 0)
        }

        /** Save and advance the slideshow index, wrapping around. */
        private fun advanceIndex(context: Context, widgetId: Int, photoCount: Int): Int {
            if (photoCount <= 0) return 0
            val current = getIndex(context, widgetId)
            val next = (current + 1) % photoCount
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putInt("idx_$widgetId", next).apply()
            return current
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        job.cancel()
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        // Clean up slideshow prefs for removed widgets
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        appWidgetIds.forEach { prefs.remove("idx_$it") }
        prefs.apply()
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_PHOTO_REFRESH) {
            // Immediate one-time sync, then widget redraws via PhotoSyncWorker
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

        val partnerId      = Prefs.getPartnerId(context)
        val partnerInitial = Prefs.getPartnerInitial(context)

        // Tap to open StoryActivity
        val storyIntent = Intent(context, com.karan.distancewidget.ui.StoryActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val tapPi = PendingIntent.getActivity(
            context, 1, storyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Show loading state immediately
        manager.updateAppWidget(widgetId, buildEmptyViews(context, "loading…", tapPi))

        scope.launch {
            val photoCount = StorageHelper.getCachedPhotoCount(context, partnerId)

            if (photoCount > 0) {
                // Pick the current slideshow photo and advance index
                val index  = advanceIndex(context, widgetId, photoCount)
                val bitmap = StorageHelper.loadCachedBitmap(context, partnerId, index)

                val ts      = StorageHelper.getCachedTimestamp(context, partnerId)
                val timeAgo = StorageHelper.photoTimeAgo(ts)
                val slideInfo = if (photoCount > 1) " • ${index + 1}/$photoCount" else ""
                val views   = buildPhotoViews(context, bitmap, partnerInitial, "$timeAgo$slideInfo", tapPi)
                manager.updateAppWidget(widgetId, views)
            } else {
                // No cached photos — try downloading from Firebase
                val downloaded = StorageHelper.downloadAllPartnerPhotos(context, partnerId)
                if (downloaded > 0) {
                    val bitmap  = StorageHelper.loadCachedBitmap(context, partnerId, 0)
                    val ts      = StorageHelper.getCachedTimestamp(context, partnerId)
                    val timeAgo = StorageHelper.photoTimeAgo(ts)
                    val slideInfo = if (downloaded > 1) " • 1/$downloaded" else ""
                    val views   = buildPhotoViews(context, bitmap, partnerInitial, "$timeAgo$slideInfo", tapPi)
                    manager.updateAppWidget(widgetId, views)
                } else {
                    manager.updateAppWidget(widgetId,
                        buildEmptyViews(context, "waiting for $partnerInitial…", tapPi))
                }
            }
        }
    }

    private fun buildPhotoViews(
        context: Context,
        bitmap: Bitmap?,
        partnerInitial: String,
        timeAgo: String,
        tapPi: PendingIntent
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
            setOnClickPendingIntent(R.id.widget_photo_root, tapPi)
        }
    }

    private fun buildEmptyViews(
        context: Context,
        message: String,
        tapPi: PendingIntent? = null
    ): RemoteViews {
        return RemoteViews(context.packageName, R.layout.widget_photo).apply {
            setViewVisibility(R.id.iv_photo,   android.view.View.GONE)
            setViewVisibility(R.id.ll_overlay, android.view.View.GONE)
            setViewVisibility(R.id.ll_empty,   android.view.View.VISIBLE)
            setTextViewText(R.id.tv_empty, message)
            if (tapPi != null) {
                setOnClickPendingIntent(R.id.widget_photo_root, tapPi)
            }
        }
    }
}
