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
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Schedule background worker every time the app opens
        WorkerScheduler.schedule(this)
        
        // Start KeepAliveService to maintain the Firebase connection
        try {
            val serviceIntent = android.content.Intent(this, com.karan.distancewidget.service.KeepAliveService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Listen for live location pings
        val myId = Prefs.getUserId(this)
        if (myId != null) {
            FirebaseHelper.listenForPings(myId) {
                lifecycleScope.launch {
                    if (ActivityCompat.checkSelfPermission(
                            this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        try {
                            val client = LocationServices.getFusedLocationProviderClient(this@MainActivity)
                            val freshLocation = withTimeoutOrNull(15_000L) {
                                val tokenSource = CancellationTokenSource()
                                client.getCurrentLocation(
                                    Priority.PRIORITY_HIGH_ACCURACY,
                                    tokenSource.token
                                ).await()
                            }
                            if (freshLocation != null) {
                                FirebaseHelper.updateMyLocation(
                                    myId,
                                    freshLocation.latitude,
                                    freshLocation.longitude
                                )
                            }
                        } catch (e: Exception) {
                            // Ignore
                        }
                    }
                }
            }
        }

        // Edge-to-edge: let Compose handle system bar insets via systemBarsPadding()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            DistanceWidgetTheme {
                var isSetup by remember { mutableStateOf(Prefs.isSetup(this)) }
                val openChat = intent.getBooleanExtra("open_chat", false)

                if (isSetup) {
                    MainScreen(initialScreen = if (openChat) "CHAT" else "HOME", onReset = { isSetup = false })
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
