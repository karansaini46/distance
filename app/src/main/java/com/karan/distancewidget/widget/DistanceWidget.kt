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
            // Run an immediate one-time sync then let the result trigger onUpdate
            val oneTime = OneTimeWorkRequestBuilder<LocationWorker>().build()
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
                val myLoc      = FirebaseHelper.getLocation(myId)
                val partnerLoc = FirebaseHelper.getLocation(partnerId)

                val (distance, subtitle, isClose) = when {
                    myLoc == null -> Triple("tap to refresh", "your location missing", false)

                    partnerLoc == null -> Triple(
                        "waiting for $partnerInitial…",
                        "partner hasn't synced yet",
                        false
                    )

                    else -> {
                        val km       = FirebaseHelper.distanceKm(myLoc, partnerLoc)
                        val isClose  = km <= 0.05
                        val distText = if (isClose) "Together! ❤️" else FirebaseHelper.formatDistance(km)
                        val subtitle = if (FirebaseHelper.isStale(partnerLoc.ts))
                            "last seen ${FirebaseHelper.timeAgo(partnerLoc.ts)}"
                        else
                            "live ♡"
                        Triple(distText, subtitle, isClose)
                    }
                }

                manager.updateAppWidget(widgetId, buildViews(context,
                    myInitial, partnerInitial, distance, subtitle, refreshPi, isClose))

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
        isClose: Boolean = false
    ): RemoteViews {
        return RemoteViews(context.packageName, R.layout.widget_distance).apply {
            setTextViewText(R.id.tv_my_initial,      myInitial)
            setTextViewText(R.id.tv_partner_initial, partnerInitial)
            setTextViewText(R.id.tv_distance,        distanceText)
            setTextViewText(R.id.tv_last_updated,    subtitle)
            setOnClickPendingIntent(R.id.widget_root, refreshPi)

            // Hide dashes when close so initials merge together near the heart
            val dashVisibility = if (isClose) android.view.View.GONE else android.view.View.VISIBLE
            setViewVisibility(R.id.tv_dash_left, dashVisibility)
            setViewVisibility(R.id.tv_dash_right, dashVisibility)
        }
    }
}
