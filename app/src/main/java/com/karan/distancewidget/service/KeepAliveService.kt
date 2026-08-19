package com.karan.distancewidget.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.karan.distancewidget.R

class KeepAliveService : Service() {

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val syncIntervalMs = 8 * 60 * 1000L // 8 minutes

    private val syncRunnable = object : Runnable {
        override fun run() {
            try {
                val req = androidx.work.OneTimeWorkRequestBuilder<com.karan.distancewidget.worker.LocationWorker>().build()
                androidx.work.WorkManager.getInstance(this@KeepAliveService).enqueue(req)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            handler.postDelayed(this, syncIntervalMs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1998, createNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1998, createNotification())
        }
        handler.postDelayed(syncRunnable, syncIntervalMs)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(syncRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // STICKY ensures the service restarts if killed by the system
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "keep_alive_channel",
                "Background Sync",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Keeps connection open for instant location updates"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "keep_alive_channel")
            .setContentTitle("Distance App is active")
            .setContentText("Ready for instant location sync")
            .setSmallIcon(R.drawable.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }
}
