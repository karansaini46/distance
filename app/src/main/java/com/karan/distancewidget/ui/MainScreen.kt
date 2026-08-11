package com.karan.distancewidget.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

private enum class Screen { HOME, CHAT, SETTINGS }

@Composable
fun MainScreen(onReset: () -> Unit = {}) {
    var currentScreen by remember { mutableStateOf(Screen.HOME) }

    // Intercept system back button if we are not on the HOME screen
    BackHandler(enabled = currentScreen != Screen.HOME) {
        currentScreen = Screen.HOME
    }

    when (currentScreen) {
        Screen.HOME -> HomeScreen(
            onOpenChat = { currentScreen = Screen.CHAT },
            onOpenSettings = { currentScreen = Screen.SETTINGS }
        )
        Screen.CHAT -> ChatScreen(
            onOpenSettings = { currentScreen = Screen.SETTINGS },
            onBack = { currentScreen = Screen.HOME }
        )
        Screen.SETTINGS -> SettingsScreen(
            onBack = { currentScreen = Screen.HOME },
            onReset = onReset
        )
    }
}
