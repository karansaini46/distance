package com.karan.distancewidget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.karan.distancewidget.data.Prefs
import com.karan.distancewidget.ui.MainScreen
import com.karan.distancewidget.ui.SetupScreen
import com.karan.distancewidget.ui.theme.DistanceWidgetTheme
import com.karan.distancewidget.util.WorkerScheduler
import com.karan.distancewidget.data.FirebaseHelper
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.karan.distancewidget.worker.LocationWorker

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Schedule background worker every time the app opens
        WorkerScheduler.schedule(this)

        // Listen for live location pings
        val myId = Prefs.getUserId(this)
        if (myId != null) {
            FirebaseHelper.listenForPings(myId) {
                val data = Data.Builder().putBoolean("force_fresh", true).build()
                val oneTime = OneTimeWorkRequestBuilder<LocationWorker>().setInputData(data).build()
                WorkManager.getInstance(this).enqueue(oneTime)
            }
        }

        // Edge-to-edge: let Compose handle system bar insets via systemBarsPadding()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            DistanceWidgetTheme {
                var isSetup by remember { mutableStateOf(Prefs.isSetup(this)) }

                if (isSetup) {
                    MainScreen(onReset = { isSetup = false })
                } else {
                    SetupScreen(onSetupComplete = {
                        WorkerScheduler.schedule(this)
                        isSetup = true
                    })
                }
            }
        }
    }
}
