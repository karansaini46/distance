package com.karan.distancewidget.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.karan.distancewidget.util.WorkerScheduler

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            WorkerScheduler.schedule(context)
        }
    }
}
