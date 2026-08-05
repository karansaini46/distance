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
     *   1. lastLocation  → fast, battery-free, may be null
     *   2. getCurrentLocation → fresh fix, up to 12s, may also be null
     *   3. null → caller returns Result.retry()
     */
    private suspend fun getBestLocation(): android.location.Location? {
        val client = LocationServices.getFusedLocationProviderClient(applicationContext)

        // Attempt 1: cached location (instant, no battery)
        val last = try { client.lastLocation.await() } catch (e: Exception) { null }
        if (last != null) return last

        // Attempt 2: fresh fix (getCurrentLocation can still succeed-with-null,
        // which is documented behaviour — treat null response as failure)
        return try {
            withTimeoutOrNull(12_000L) {
                val tokenSource = CancellationTokenSource()
                val result = client.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    tokenSource.token
                ).await()
                result  // may be null — that's fine, withTimeoutOrNull returns null too
            }
        } catch (e: Exception) {
            null
        }
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
