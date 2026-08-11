package com.karan.distancewidget.worker

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.karan.distancewidget.data.FirebaseHelper
import com.karan.distancewidget.data.Prefs
import com.karan.distancewidget.widget.DistanceWidget
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class LocationWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        // 1. Bail early if the user hasn't completed setup
        if (!Prefs.isSetup(applicationContext)) return Result.failure()

        // 2. Bail if permission revoked
        if (ActivityCompat.checkSelfPermission(
                applicationContext, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return Result.failure()

        return try {
            val location = getBestLocation() ?: return Result.retry()

            val myId = Prefs.getUserId(applicationContext) ?: return Result.failure()
            FirebaseHelper.updateMyLocation(myId, location.latitude, location.longitude)

            // Trigger widget redraw immediately
            triggerWidgetUpdate()

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    /**
     * Gets the best available location.
     * Strategy:
     *   1. getCurrentLocation (HIGH_ACCURACY) → always try a fresh GPS fix first
     *   2. lastLocation fallback → only if fresh fix fails AND cache is < 60s old
     *   3. null → caller returns Result.retry()
     *
     * This guarantees that every update pushes a genuinely current position
     * rather than a potentially 15-minute-stale cached one.
     */
    private suspend fun getBestLocation(): android.location.Location? {
        val client = LocationServices.getFusedLocationProviderClient(applicationContext)
        val forceFresh = inputData.getBoolean("force_fresh", false)

        // ── Attempt 1: fresh GPS fix (always HIGH_ACCURACY for real-time precision) ──
        val freshLocation = try {
            withTimeoutOrNull(15_000L) {
                val tokenSource = CancellationTokenSource()
                val result = client.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    tokenSource.token
                ).await()
                result  // may be null — documented behaviour
            }
        } catch (e: Exception) {
            null
        }

        if (freshLocation != null) return freshLocation

        // ── Attempt 2: cached location fallback ──────────────────────────
        // Only accept cached location if it's very recent (< 60 seconds).
        // For force_fresh (widget tap), skip cache entirely — return null to retry.
        if (forceFresh) return null

        val last = try { client.lastLocation.await() } catch (e: Exception) { null }
        if (last != null && System.currentTimeMillis() - last.time < 60_000L) {
            return last
        }

        return null
    }

    private fun triggerWidgetUpdate() {
        val manager   = AppWidgetManager.getInstance(applicationContext)
        val component = ComponentName(applicationContext, DistanceWidget::class.java)
        val ids       = manager.getAppWidgetIds(component)
        if (ids.isEmpty()) return

        val intent = Intent(applicationContext, DistanceWidget::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        applicationContext.sendBroadcast(intent)
    }
}
