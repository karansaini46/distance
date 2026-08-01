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

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Schedule background worker every time the app opens
        WorkerScheduler.schedule(this)

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
