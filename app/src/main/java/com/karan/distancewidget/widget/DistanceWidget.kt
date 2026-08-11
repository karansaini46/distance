package com.karan.distancewidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

import com.karan.distancewidget.R
import com.karan.distancewidget.data.FirebaseHelper
import com.karan.distancewidget.data.Prefs
import com.karan.distancewidget.worker.LocationWorker

class DistanceWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.karan.distancewidget.ACTION_REFRESH"

        // Flag to indicate the next widget update should bypass Firebase cache.
        // Set when user taps refresh, consumed after the update completes.
        @Volatile
        private var forceServerFetch = false

        // Single scope shared across all widget update calls.
        // SupervisorJob: one failed coroutine doesn't cancel siblings.
        private var job   = SupervisorJob()
        private var scope = CoroutineScope(job + Dispatchers.IO)

        /** Called from LocationWorker after a successful location push. */
        fun requestUpdate(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, DistanceWidget::class.java))
            if (ids.isEmpty()) return
            val intent = Intent(context, DistanceWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }

    // Called when user removes the last widget instance from the launcher.
    // This is the correct place to cancel the shared scope.
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        job.cancel()
        // Recreate so scope is usable if widgets are re-added without process restart
        job   = SupervisorJob()
        scope = CoroutineScope(job + Dispatchers.IO)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)   // MUST come first
        if (intent.action == ACTION_REFRESH) {
            // Mark that the next Firebase read should bypass offline cache
            forceServerFetch = true
            // Run an immediate one-time sync then let the result trigger onUpdate
            val data = androidx.work.workDataOf("force_fresh" to true)
            val oneTime = OneTimeWorkRequestBuilder<LocationWorker>().setInputData(data).build()
            WorkManager.getInstance(context).enqueue(oneTime)
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
        val myId           = Prefs.getUserId(context)
        val myInitial      = Prefs.getMyInitial(context)
        val partnerInitial = Prefs.getPartnerInitial(context)

        // Build the PendingIntent that fires on widget tap (refresh)
        val refreshIntent = Intent(context, DistanceWidget::class.java).apply {
            action = ACTION_REFRESH
        }
        val refreshPi = PendingIntent.getBroadcast(
            context, 0, refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ── STATE: not set up ───────────────────────────────────────────
        if (myId == null) {
            manager.updateAppWidget(widgetId, buildViews(context, "?", "?",
                "open app first", "", refreshPi))
            return
        }

        val partnerId = Prefs.getPartnerId(context)

        // ── STATE: loading (shown immediately while Firebase fetches) ───
        manager.updateAppWidget(widgetId, buildViews(context,
            myInitial, partnerInitial, "loading…", "syncing", refreshPi))

        // ── Async: fetch both locations then update ────────────────────
        scope.launch {
            try {
                // Consume the force flag — bypass Firebase cache if user tapped refresh
                val serverFresh = forceServerFetch
                forceServerFetch = false

                val myLoc      = FirebaseHelper.getLocation(myId, forceServer = serverFresh)
                val partnerLoc = FirebaseHelper.getLocation(partnerId, forceServer = serverFresh)

                var distanceText = "loading..."
                var subtitleText = "syncing"
                var isClose = false
                var emoji = "❤️"

                if (myLoc == null) {
                    distanceText = "tap to refresh"
                    subtitleText = "your location missing"
                    emoji = "❓"
                } else if (partnerLoc == null) {
                    distanceText = "waiting for $partnerInitial…"
                    subtitleText = "partner hasn't synced yet"
                    emoji = "⏳"
                } else {
                    val km = FirebaseHelper.distanceKm(myLoc, partnerLoc)
                    isClose = km <= 0.05
                    distanceText = if (isClose) "Together!" else FirebaseHelper.formatDistance(km)
                    subtitleText = if (FirebaseHelper.isStale(partnerLoc.ts))
                        "last seen ${FirebaseHelper.timeAgo(partnerLoc.ts)}"
                    else
                        "live ♡"

                    emoji = when {
                        km <= 0.05 -> "🥰"
                        km <= 0.2  -> "😍"
                        km <= 0.35 -> "❤️"
                        km <= 0.5  -> "💖"
                        km <= 10.0 -> "🥺"
                        km <= 500.0 -> "😢"
                        else -> "😭"
                    }
                }

                manager.updateAppWidget(widgetId, buildViews(context,
                    myInitial, partnerInitial, distanceText, subtitleText, refreshPi, emoji, isClose))

            } catch (e: Exception) {
                manager.updateAppWidget(widgetId, buildViews(context,
                    myInitial, partnerInitial, "tap to retry", "no connection", refreshPi))
            }
        }
    }

    /** Builds a RemoteViews object for any widget state. */
    private fun buildViews(
        context: Context,
        myInitial: String,
        partnerInitial: String,
        distanceText: String,
        subtitle: String,
        refreshPi: PendingIntent,
        emoji: String = "❤️",
        isClose: Boolean = false
    ): RemoteViews {
        return RemoteViews(context.packageName, R.layout.widget_distance).apply {
            setTextViewText(R.id.tv_my_initial,      myInitial)
            setTextViewText(R.id.tv_partner_initial, partnerInitial)
            setTextViewText(R.id.tv_distance,        distanceText)
            setTextViewText(R.id.tv_last_updated,    subtitle)
            setOnClickPendingIntent(R.id.widget_root, refreshPi)

            // Apply Customizations
            val animEnabled = Prefs.isWidgetAnimationEnabled(context)
            val opacityPercent = Prefs.getWidgetOpacity(context)

            // 1. Transparency (using View.setAlpha which is safer in RemoteViews)
            val alphaFloat = opacityPercent / 100f
            setFloat(R.id.widget_bg_image, "setAlpha", alphaFloat)

            // 2. Emoji & Animation
            setTextViewText(R.id.tv_emoji_static, emoji)
            setTextViewText(R.id.tv_emoji_1, emoji)
            setTextViewText(R.id.tv_emoji_2, emoji)

            if (animEnabled) {
                setViewVisibility(R.id.tv_emoji_static, android.view.View.GONE)
                setViewVisibility(R.id.view_flipper_emoji, android.view.View.VISIBLE)
            } else {
                setViewVisibility(R.id.tv_emoji_static, android.view.View.VISIBLE)
                setViewVisibility(R.id.view_flipper_emoji, android.view.View.GONE)
            }
        }
    }
}
