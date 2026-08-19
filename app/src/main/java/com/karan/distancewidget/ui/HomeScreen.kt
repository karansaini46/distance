package com.karan.distancewidget.ui

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karan.distancewidget.data.FirebaseHelper
import com.karan.distancewidget.data.LocationData
import com.karan.distancewidget.data.Prefs
import com.karan.distancewidget.ui.theme.*

@Composable
fun HomeScreen(
    onOpenChat: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val myId = Prefs.getUserId(context) ?: return
    val partnerId = Prefs.getPartnerId(context)
    val partnerInitial = Prefs.getPartnerInitial(context)
    val myInitial = Prefs.getMyInitial(context)

    var myLocation by remember { mutableStateOf<LocationData?>(null) }
    var partnerLocation by remember { mutableStateOf<LocationData?>(null) }

    LaunchedEffect(Unit) {
        myLocation = FirebaseHelper.getLocation(myId)
        partnerLocation = FirebaseHelper.getLocation(partnerId)
    }

    // Pulsing heart animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val heartScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heartScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgPrimary, BgSurface)))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        // ─── TOP HEADER ────────────────────────────────────────────
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Distance",
                        color = TextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        "Stay close, no matter the miles",
                        color = TextMuted,
                        fontSize = 13.sp,
                        lineHeight = 22.sp
                    )
                }
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, "Settings", tint = TextSecondary)
                    }
                }
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)
        }

        Spacer(Modifier.height(8.dp))

        // ─── DISTANCE HERO CARD ────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            AccentViolet.copy(alpha = 0.3f),
                            AccentRose.copy(alpha = 0.25f)
                        )
                    )
                )
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Avatars row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // My avatar
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(AccentViolet)
                            .border(1.5.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            myInitial,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    }

                    Spacer(Modifier.width(16.dp))

                    // Heart / Emoji based on distance
                    val displayEmoji = if (myLocation != null && partnerLocation != null) {
                        val km = FirebaseHelper.distanceKm(myLocation!!, partnerLocation!!)
                        val partnerTs = partnerLocation!!.ts
                        if (FirebaseHelper.isStale(partnerTs)) {
                            "⏳"
                        } else {
                            when {
                                km <= 0.05 -> "🥰"
                                km <= 0.2  -> "😍"
                                km <= 0.35 -> "❤️"
                                km <= 0.5  -> "💖"
                                km <= 10.0 -> "🥺"
                                km <= 500.0 -> "😢"
                                else -> "😭"
                            }
                        }
                    } else {
                        "❓"
                    }

                    Box(
                        modifier = Modifier.size(64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(AccentRose.copy(alpha = 0.15f), Color.Transparent)
                                    )
                                )
                        )
                        Text(
                            displayEmoji,
                            fontSize = 28.sp,
                            modifier = Modifier.scale(heartScale)
                        )
                    }

                    Spacer(Modifier.width(16.dp))

                    // Partner avatar
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(AccentRose)
                            .border(1.5.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            partnerInitial,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Distance display
                if (myLocation != null && partnerLocation != null) {
                    val km = FirebaseHelper.distanceKm(myLocation!!, partnerLocation!!)
                    val isClose = km <= 0.05

                    if (isClose) {
                        Text(
                            "Together! 💕",
                            color = TextPrimary,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            FirebaseHelper.formatDistance(km),
                            color = TextPrimary,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    // Last updated
                    val partnerTs = partnerLocation?.ts ?: 0L
                    val lastSeen = if (FirebaseHelper.isStale(partnerTs)) {
                        "last seen ${FirebaseHelper.timeAgo(partnerTs)}"
                    } else {
                        "live 💚"
                    }
                    Text(lastSeen, color = TextMuted, fontSize = 13.sp)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Syncing...",
                            color = TextMuted,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.width(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = TextMuted,
                            strokeWidth = 1.5.dp
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Waiting for location data",
                        color = TextMuted,
                        fontSize = 13.sp,
                        lineHeight = 22.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        // ─── QUICK ACTIONS ─────────────────────────────────────────
        Text(
            "QUICK ACTIONS",
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Chat
            QuickActionCard(
                icon = Icons.Outlined.Chat,
                label = "Chat",
                sublabel = "Send a message",
                gradient = listOf(AccentViolet.copy(alpha = 0.2f), AccentViolet.copy(alpha = 0.08f)),
                iconTint = AccentViolet,
                modifier = Modifier.weight(1f),
                onClick = onOpenChat
            )

            // Shared Photos
            QuickActionCard(
                icon = Icons.Outlined.PhotoLibrary,
                label = "Photos",
                sublabel = "View & share",
                gradient = listOf(AccentRose.copy(alpha = 0.2f), AccentRose.copy(alpha = 0.08f)),
                iconTint = AccentRose,
                modifier = Modifier.weight(1f),
                onClick = {
                    context.startActivity(Intent(context, StoryActivity::class.java))
                }
            )
        }

        Spacer(Modifier.height(32.dp))

        // ─── PARTNER STATUS CARD ───────────────────────────────────
        Text(
            "CONNECTION",
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                .clickable { onOpenChat() }
                .animateContentSize()
                .padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Status indicator
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(AccentRose.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(partnerInitial, color = AccentRose, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        partnerId.replaceFirstChar { it.uppercase() },
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    val statusText = if (partnerLocation != null) {
                        val ts = partnerLocation!!.ts
                        if (FirebaseHelper.isStale(ts)) {
                            "Last active ${FirebaseHelper.timeAgo(ts)}"
                        } else {
                            "Online now"
                        }
                    } else {
                        "Waiting for sync..."
                    }

                    Text(statusText, color = TextMuted, fontSize = 12.sp)
                }

                // Online dot
                val isOnline = partnerLocation != null && !FirebaseHelper.isStale(partnerLocation!!.ts)
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isOnline) StatusOk else StatusWarn)
                )

                Spacer(Modifier.width(8.dp))

                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "Open chat",
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun QuickActionCard(
    icon: ImageVector,
    label: String,
    sublabel: String,
    gradient: List<Color>,
    iconTint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(gradient))
            .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .animateContentSize()
            .padding(24.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, label, tint = iconTint, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(label, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(sublabel, color = TextMuted, fontSize = 12.sp)
        }
    }
}
