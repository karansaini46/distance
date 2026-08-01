package com.karan.distancewidget

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

class DistanceApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Guard against double-init crash (hot reload, instant run, etc.)
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }
        try {
            Firebase.database.setPersistenceEnabled(true)
            Firebase.database.setPersistenceCacheSizeBytes(5 * 1024 * 1024L)
        } catch (_: Exception) {
            // Already called — safe to ignore
        }
    }
}
