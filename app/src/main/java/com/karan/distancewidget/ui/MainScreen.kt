package com.karan.distancewidget.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.karan.distancewidget.data.Prefs
import com.karan.distancewidget.ui.components.GlassCard
import com.karan.distancewidget.ui.theme.AccentRose
import com.karan.distancewidget.ui.theme.BgPrimary
import com.karan.distancewidget.ui.theme.BgSurface
import com.karan.distancewidget.ui.theme.StatusOk
import com.karan.distancewidget.ui.theme.StatusWarn
import com.karan.distancewidget.ui.theme.TextMuted
import com.karan.distancewidget.ui.theme.TextPrimary
import com.karan.distancewidget.ui.theme.TextSecondary
import com.karan.distancewidget.worker.LocationWorker
import kotlinx.coroutines.delay
import androidx.activity.result.PickVisualMediaRequest
import com.google.firebase.Firebase
import com.google.firebase.database.database
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import com.karan.distancewidget.data.StorageHelper
import com.karan.distancewidget.ui.theme.AccentViolet
import com.karan.distancewidget.data.FirebaseHelper
import com.karan.distancewidget.data.LocationData

@Composable
fun MainScreen(onReset: () -> Unit = {}) {
    val context = LocalContext.current
    val userId  = Prefs.getUserId(context) ?: return
    val partnerId = Prefs.getPartnerId(context)
    var partnerLocation by remember { mutableStateOf<LocationData?>(null) }

    var widgetOpacity by remember { mutableStateOf(Prefs.getWidgetOpacity(context).toFloat()) }
    var widgetAnim by remember { mutableStateOf(Prefs.isWidgetAnimationEnabled(context)) }

    fun notifyWidget() {
        val mgr = android.appwidget.AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(android.content.ComponentName(context, com.karan.distancewidget.widget.DistanceWidget::class.java))
        if (ids.isNotEmpty()) {
            val intent = Intent(context, com.karan.distancewidget.widget.DistanceWidget::class.java).apply {
                action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }

    LaunchedEffect(Unit) {
        if (partnerId != null) {
            partnerLocation = FirebaseHelper.getLocation(partnerId)
        }
    }

    // Live permission states — recheck whenever the screen is resumed
    val lifecycleOwner = LocalLifecycleOwner.current
    var fineGranted       by remember { mutableStateOf(false) }
    var bgGranted         by remember { mutableStateOf(false) }
    var batteryIgnored    by remember { mutableStateOf(false) }

    fun refreshPermissionStates() {
        fineGranted    = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        bgGranted      = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        else true
        batteryIgnored = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(context.packageName)
    }

    // Refresh whenever the lifecycle hits ON_RESUME (user returns from settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshPermissionStates()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Launchers
    val bgPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshPermissionStates() }

    val finePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshPermissionStates() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgPrimary, BgSurface)))
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Header ─────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Favorite, null, tint = AccentRose,
                    modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("Distance", color = TextPrimary, fontSize = 22.sp,
                    fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(4.dp))

            // ── Identity Card ──────────────────────────────────────────
            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Running as", color = TextMuted, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(StatusOk))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                userId.replaceFirstChar { it.uppercase() },
                                color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            val loc = partnerLocation
                            if (loc != null) {
                                val uri = Uri.parse("geo:${loc.lat},${loc.lng}?q=${loc.lat},${loc.lng}(${partnerId.replaceFirstChar { it.uppercase() }})")
                                val intent = Intent(Intent.ACTION_VIEW, uri)
                                intent.setPackage("com.google.android.apps.maps")
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    intent.setPackage(null)
                                    try { context.startActivity(intent) } catch (_: Exception) {}
                                }
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(BgSurface)
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = "Partner location", tint = AccentRose, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Widget updates every ~15 min in background.",
                    color = TextSecondary, fontSize = 13.sp)
            }

            // ── Widget Customization ──────────────────────────────────────────
            GlassCard {
                Text("Widget UI Customization", color = TextMuted, fontSize = 12.sp)
                Spacer(Modifier.height(16.dp))

                // Animation Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Cute Beating Animation", color = TextPrimary, fontSize = 15.sp)
                    Switch(
                        checked = widgetAnim,
                        onCheckedChange = { 
                            widgetAnim = it
                            Prefs.setWidgetAnimationEnabled(context, it)
                            notifyWidget()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AccentRose,
                            checkedTrackColor = AccentRose.copy(alpha = 0.5f)
                        )
                    )
                }

                Spacer(Modifier.height(20.dp))

                // Transparency Slider
                Text("Widget Transparency (${widgetOpacity.toInt()}%)", color = TextPrimary, fontSize = 15.sp)
                Slider(
                    value = widgetOpacity,
                    onValueChange = { widgetOpacity = it },
                    onValueChangeFinished = {
                        Prefs.setWidgetOpacity(context, widgetOpacity.toInt())
                        notifyWidget()
                    },
                    valueRange = 10f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentRose,
                        activeTrackColor = AccentRose,
                        inactiveTrackColor = TextSecondary.copy(alpha = 0.3f)
                    )
                )
            }

            // ── Permissions Card ───────────────────────────────────────
            GlassCard {
                Text("Permissions", color = TextMuted, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))

                PermissionRow(
                    label    = "Location (foreground)",
                    granted  = fineGranted,
                    onFix    = {
                        finePermLauncher.launch(arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ))
                    }
                )

                Spacer(Modifier.height(10.dp))

                PermissionRow(
                    label   = "Location (background / always)",
                    granted = bgGranted,
                    onFix   = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            bgPermLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }
                    }
                )

                if (!bgGranted) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "⚠ In the permission dialog, choose \"Allow all the time\" — " +
                        "any other option stops background syncing.",
                        color = StatusWarn, fontSize = 12.sp
                    )
                }
            }

            // ── Battery Optimisation Warning ───────────────────────────
            if (!batteryIgnored) {
                GlassCard(modifier = Modifier.border(
                    1.dp, StatusWarn.copy(alpha = 0.4f), RoundedCornerShape(20.dp))) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⚡", fontSize = 18.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Battery optimisation is ON",
                                color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("This will pause location syncing. Tap Fix.",
                                color = TextSecondary, fontSize = 12.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { openBatterySettings(context) }) {
                            Text("Fix", color = AccentRose, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ── Photo Section Card ────────────────────────────────────────────────────
            var uploading    by remember { mutableStateOf(false) }
            var uploadMsg    by remember { mutableStateOf("") }
            var uploadProgress by remember { mutableStateOf("") }
            var lastSentTs   by remember { mutableStateOf(0L) }
            var sentCount    by remember { mutableStateOf(0) }

            // Load last sent metadata from Firebase on first composition
            LaunchedEffect(Unit) {
                val myId = Prefs.getUserId(context) ?: return@LaunchedEffect
                try {
                    val tsSnap = Firebase.database.reference
                        .child("photos").child("${myId}_ts").get().await()
                    lastSentTs = tsSnap.getValue(Long::class.java) ?: 0L

                    val countSnap = Firebase.database.reference
                        .child("photos").child("${myId}_count").get().await()
                    sentCount = countSnap.getValue(Int::class.java) ?: 0
                } catch (_: Exception) {}
            }

            // Multi-photo picker — select up to 10 photos
            val photoPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.PickMultipleVisualMedia(10)
            ) { uris ->
                if (uris.isEmpty()) return@rememberLauncherForActivityResult
                uploading  = true
                uploadMsg  = ""
                uploadProgress = "Sending ${uris.size} photo${if (uris.size > 1) "s" else ""}…"
                val myId   = Prefs.getUserId(context) ?: return@rememberLauncherForActivityResult

                CoroutineScope(Dispatchers.IO).launch {
                    val success = StorageHelper.uploadMultiplePhotos(context, uris, myId)
                    withContext(Dispatchers.Main) {
                        uploading  = false
                        uploadProgress = ""
                        if (success) {
                            uploadMsg  = "Sent ${uris.size} photo${if (uris.size > 1) "s" else ""}! ♡"
                            lastSentTs = System.currentTimeMillis()
                            sentCount  = uris.size
                        } else {
                            uploadMsg  = "Upload failed — check internet"
                        }
                    }
                }
            }

            GlassCard {
                Text("Send photos", color = TextMuted, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))

                Row(
                    verticalAlignment    = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text       = if (sentCount > 0) "$sentCount photo${if (sentCount > 1) "s" else ""} sent"
                                         else "No photos sent yet",
                            color      = TextSecondary,
                            fontSize   = 13.sp
                        )
                        Text(
                            text       = StorageHelper.photoTimeAgo(lastSentTs),
                            color      = TextPrimary,
                            fontSize   = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick  = {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled  = !uploading,
                        shape    = RoundedCornerShape(14.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = AccentViolet)
                    ) {
                        if (uploading) {
                            CircularProgressIndicator(
                                color       = Color.White,
                                modifier    = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(uploadProgress.ifEmpty { "Sending…" })
                        } else {
                            Icon(Icons.Default.AddAPhoto, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Send Photos")
                        }
                    }
                }

                // Success / error message
                if (uploadMsg.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text     = uploadMsg,
                        color    = if (uploadMsg.startsWith("Sent")) StatusOk else StatusWarn,
                        fontSize = 13.sp
                    )
                }
            }

            // ── Sync Now ───────────────────────────────────────────────
            var syncing by remember { mutableStateOf(false) }

            // Compose-safe 3-second reset (replaces Handler/postDelayed)
            LaunchedEffect(syncing) {
                if (syncing) {
                    delay(3000)
                    syncing = false
                }
            }

            Button(
                onClick = {
                    if (!syncing) {
                        syncing = true
                        WorkManager.getInstance(context)
                            .enqueue(OneTimeWorkRequestBuilder<LocationWorker>()
                                .setInputData(androidx.work.workDataOf("force_fresh" to true))
                                .build())
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape  = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentRose)
            ) {
                if (syncing) {
                    CircularProgressIndicator(
                        color     = Color.White,
                        modifier  = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Syncing…", fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sync Now", fontWeight = FontWeight.SemiBold)
                }
            }

            // ── Footer hint ────────────────────────────────────────────
            Text(
                "Long-press your home screen → Widgets → drag Distance widget",
                color     = TextMuted,
                fontSize  = 12.sp,
                textAlign = TextAlign.Center,
                modifier  = Modifier.fillMaxWidth().padding(top = 8.dp)
            )

            TextButton(
                onClick = {
                    Prefs.clear(context)
                    onReset()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Edit Initials / Setup again", color = TextSecondary)
            }
        }
    }
}

// ── Reusable permission row ────────────────────────────────────────────────
@Composable
private fun PermissionRow(label: String, granted: Boolean, onFix: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector        = if (granted) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint               = if (granted) StatusOk else StatusWarn,
            modifier           = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(label, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (!granted) {
            TextButton(onClick = onFix, contentPadding = PaddingValues(0.dp)) {
                Text("Fix", color = AccentRose, fontSize = 13.sp)
            }
        }
    }
}

// ── OEM-aware battery settings deep link ──────────────────────────────────
private fun openBatterySettings(context: Context) {
    // 1. Standard Android
    try {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:${context.packageName}"))
        )
        return
    } catch (_: Exception) {}

    // 2. Xiaomi (MIUI)
    try {
        context.startActivity(Intent().setComponent(ComponentName(
            "com.miui.powerkeeper",
            "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
        )).putExtra("package_name", context.packageName)
         .putExtra("package_label", "Distance"))
        return
    } catch (_: Exception) {}

    // 3. Samsung / OnePlus / all others — open app details as fallback
    try {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}"))
        )
    } catch (_: Exception) {}
}
