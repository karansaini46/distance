package com.karan.distancewidget.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkColorScheme = darkColorScheme(
    primary          = AccentRose,
    secondary        = AccentViolet,
    background       = BgPrimary,
    surface          = BgSurface,
    onPrimary        = Color.White,
    onSecondary      = Color.White,
    onBackground     = TextPrimary,
    onSurface        = TextPrimary,
    surfaceVariant   = BgCard,
    onSurfaceVariant = TextSecondary,
)

@Composable
fun DistanceWidgetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content     = content
    )
}
