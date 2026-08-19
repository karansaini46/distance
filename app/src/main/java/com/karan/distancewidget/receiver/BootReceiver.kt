package com.karan.distancewidget.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.karan.distancewidget.util.WorkerScheduler

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            WorkerScheduler.schedule(context)
            
            try {
                val serviceIntent = Intent(context, com.karan.distancewidget.service.KeepAliveService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
