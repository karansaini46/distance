package com.karan.distancewidget.ui

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karan.distancewidget.data.Prefs
import com.karan.distancewidget.ui.components.GradientButton
import com.karan.distancewidget.ui.theme.AccentRose
import com.karan.distancewidget.ui.theme.AccentViolet
import com.karan.distancewidget.ui.theme.BgPrimary
import com.karan.distancewidget.ui.theme.BgSurface
import com.karan.distancewidget.ui.theme.TextMuted
import com.karan.distancewidget.ui.theme.TextPrimary
import com.karan.distancewidget.ui.theme.TextSecondary

@Composable
fun SetupScreen(onSetupComplete: () -> Unit) {
    val context = LocalContext.current

    // Pulse animation for the heart icon
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue  = 1.1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heartScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(BgPrimary, BgSurface, BgPrimary))
            )
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            // Pulsing heart
            Icon(
                imageVector        = Icons.Default.Favorite,
                contentDescription = null,
                tint               = AccentRose,
                modifier           = Modifier
                    .size(64.dp)
                    .scale(scale)
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text       = "Whose phone is this?",
                color      = TextPrimary,
                fontSize   = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
                textAlign  = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text      = "Set once. Never asked again.",
                color     = TextMuted,
                fontSize  = 15.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            var myInitial by androidx.compose.runtime.remember { mutableStateOf("") }
            var partnerInitial by androidx.compose.runtime.remember { mutableStateOf("") }

            OutlinedTextField(
                value = myInitial,
                onValueChange = { if (it.length <= 1) myInitial = it },
                label = { Text("Your Initial", color = TextSecondary) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(0.72f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentRose,
                    unfocusedBorderColor = TextMuted,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                )
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = partnerInitial,
                onValueChange = { if (it.length <= 1) partnerInitial = it },
                label = { Text("Partner's Initial", color = TextSecondary) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(0.72f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentViolet,
                    unfocusedBorderColor = TextMuted,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                )
            )

            Spacer(Modifier.height(32.dp))

            val isComplete = myInitial.isNotBlank() && partnerInitial.isNotBlank()

            // Your button
            GradientButton(
                text           = "I am Person 1",
                gradientColors = if (isComplete) listOf(AccentRose, Color(0xFFFF6B6B)) else listOf(TextMuted, TextMuted),
                onClick        = {
                    if (isComplete) {
                        Prefs.saveUser(context, Prefs.USER_KARAN, myInitial, partnerInitial)
                        onSetupComplete()
                    }
                }
            )

            Spacer(Modifier.height(16.dp))

            // Partner button
            GradientButton(
                text           = "I am Person 2",
                gradientColors = if (isComplete) listOf(AccentViolet, Color(0xFF9C8FFF)) else listOf(TextMuted, TextMuted),
                onClick        = {
                    if (isComplete) {
                        Prefs.saveUser(context, Prefs.USER_PARTNER, myInitial, partnerInitial)
                        onSetupComplete()
                    }
                }
            )
        }
    }
}
