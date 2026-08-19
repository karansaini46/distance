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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import kotlinx.coroutines.delay
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.karan.distancewidget.data.FirebaseHelper
import com.karan.distancewidget.data.LocationData
import com.karan.distancewidget.data.Prefs
import com.karan.distancewidget.ui.components.GlassCard
import com.karan.distancewidget.ui.theme.*
import kotlinx.coroutines.launch

sealed class LocationButtonState {
    object Idle : LocationButtonState()
    object Loading : LocationButtonState()
    data class Success(val lat: Double, val lng: Double, val ts: Long) : LocationButtonState()
    data class Error(val message: String) : LocationButtonState()
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onReset: () -> Unit) {
    val context = LocalContext.current
    val userId = Prefs.getUserId(context) ?: return

    var widgetOpacity by remember { mutableStateOf(Prefs.getWidgetOpacity(context).toFloat()) }
    var widgetAnim by remember { mutableStateOf(Prefs.isWidgetAnimationEnabled(context)) }

    // Location status
    val settingsScope = rememberCoroutineScope()
    var myLocation by remember { mutableStateOf<LocationData?>(null) }
    var partnerLocation by remember { mutableStateOf<LocationData?>(null) }
    val partnerId = Prefs.getPartnerId(context)
    var locationState by remember { 
        mutableStateOf<LocationButtonState>(LocationButtonState.Idle) 
    }

    LaunchedEffect(Unit) {
        myLocation = FirebaseHelper.getLocation(userId)
        partnerLocation = FirebaseHelper.getLocation(partnerId)
    }

    fun notifyWidget() {
        val mgr = android.appwidget.AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, com.karan.distancewidget.widget.DistanceWidget::class.java))
        if (ids.isNotEmpty()) {
            val intent = Intent(context, com.karan.distancewidget.widget.DistanceWidget::class.java).apply {
                action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var fineGranted by remember { mutableStateOf(false) }
    var bgGranted by remember { mutableStateOf(false) }
    var batteryIgnored by remember { mutableStateOf(false) }

    fun refreshPermissionStates() {
        fineGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        bgGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        else true
        batteryIgnored = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(context.packageName)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshPermissionStates()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Column {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Settings",
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)
            }

            Spacer(Modifier.height(8.dp))

            // Identity
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("RUNNING AS", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp)
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(12.dp).clip(CircleShape).background(StatusOk))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        userId.replaceFirstChar { it.uppercase() },
                        color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Location Status
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val activeLoc = partnerLocation ?: myLocation
                    IconButton(
                        onClick = {
                            activeLoc?.let { loc ->
                                val uri = Uri.parse("https://www.google.com/maps?q=${loc.lat},${loc.lng}")
                                val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                                    setPackage("com.google.android.apps.maps")
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                val finalIntent = if (mapIntent.resolveActivity(context.packageManager) != null) {
                                    mapIntent
                                } else {
                                    Intent(Intent.ACTION_VIEW, uri).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                }
                                try { context.startActivity(finalIntent) } catch (_: Exception) {}
                            }
                        },
                        enabled = activeLoc != null,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = "Open location in Google Maps",
                            tint = if (myLocation != null) StatusOk else StatusWarn,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Location Sync", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        val statusText = if (myLocation != null) {
                            if (partnerLocation != null) {
                                val km = FirebaseHelper.distanceKm(myLocation!!, partnerLocation!!)
                                "${FirebaseHelper.formatDistance(km)} • ${FirebaseHelper.timeAgo(partnerLocation!!.ts)}"
                            } else {
                                "Your location is live · partner not synced yet"
                            }
                        } else {
                            "Location not synced yet"
                        }
                        Text(statusText, color = TextMuted, fontSize = 12.sp, lineHeight = 22.sp)
                        
                        if (locationState is LocationButtonState.Error) {
                            Text((locationState as LocationButtonState.Error).message, color = TextMuted, fontSize = 12.sp, lineHeight = 22.sp)
                        }
                    }
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (partnerLocation != null) {
                            IconButton(onClick = {
                                val uri = android.net.Uri.parse(
                                    "https://www.google.com/maps?q=${partnerLocation!!.lat},${partnerLocation!!.lng}"
                                )
                                val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                                    setPackage("com.google.android.apps.maps")
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                val finalIntent = if (mapIntent.resolveActivity(context.packageManager) != null) {
                                    mapIntent
                                } else {
                                    Intent(Intent.ACTION_VIEW, uri).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                }
                                try { context.startActivity(finalIntent) } catch (e: Exception) {}
                            }) {
                                Icon(Icons.Default.Map, contentDescription = "Open Map", tint = TextSecondary)
                            }
                        }
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            when (locationState) {
                                is LocationButtonState.Loading -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = AccentRose,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text("Pinging partner...", color = TextMuted, fontSize = 13.sp)
                                }
                                is LocationButtonState.Success -> {
                                    IconButton(onClick = { /* keep same padding as regular button */ }) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = "Success", tint = StatusOk)
                                    }
                                }
                                else -> {
                                    IconButton(onClick = {
                                        if (locationState is LocationButtonState.Loading) return@IconButton
                                        if (partnerId.isNullOrEmpty()) return@IconButton
                                        locationState = LocationButtonState.Loading
                                        
                                        val pingTime = System.currentTimeMillis()
                                        
                                        settingsScope.launch {
                                            // Step 1: ping the partner
                                            FirebaseHelper.pingPartnerForLocation(partnerId)
                                            
                                            // Step 2: poll Firebase every 2 seconds for up to 20 seconds
                                            // waiting for a location with timestamp NEWER than pingTime
                                            var found = false
                                            repeat(10) { attempt ->
                                                if (found) return@repeat
                                                
                                                delay(2000L)
                                                
                                                val loc = FirebaseHelper.getLocation(partnerId, forceServer = true)
                                                if (loc != null && loc.ts > pingTime) {
                                                    found = true
                                                    locationState = LocationButtonState.Success(loc.lat, loc.lng, loc.ts)
                                                    
                                                    // Step 3: open Google Maps with the live coordinates immediately
                                                    val uri = android.net.Uri.parse(
                                                        "https://www.google.com/maps?q=${loc.lat},${loc.lng}"
                                                    )
                                                    val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                                                        setPackage("com.google.android.apps.maps")
                                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                    }
                                                    // Fallback to browser if Maps not installed
                                                    val finalIntent = if (mapIntent.resolveActivity(context.packageManager) != null) {
                                                        mapIntent
                                                    } else {
                                                        Intent(Intent.ACTION_VIEW, uri).apply {
                                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                        }
                                                    }
                                                    try { context.startActivity(finalIntent) } catch (e: Exception) {}
                                                    
                                                    // Reset button after 3 seconds
                                                    delay(3000L)
                                                    locationState = LocationButtonState.Idle
                                                }
                                            }
                                            
                                            if (!found) {
                                                locationState = LocationButtonState.Error("Couldn't get live location. Partner's app may be closed.")
                                                delay(4000L)
                                                locationState = LocationButtonState.Idle
                                            }
                                        }
                                    }) {
                                        Icon(Icons.Default.MyLocation, contentDescription = "Ping Partner", tint = AccentRose)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Widget Customization
            GlassCard {
                Text("WIDGET UI CUSTOMIZATION", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp)
                Spacer(Modifier.height(16.dp))

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

                Spacer(Modifier.height(24.dp))

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

            // Permissions
            GlassCard {
                Text("PERMISSIONS", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp)
                Spacer(Modifier.height(12.dp))

                PermissionRow(
                    label = "Location (foreground)",
                    granted = fineGranted,
                    onFix = {
                        finePermLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                )

                Spacer(Modifier.height(12.dp))

                PermissionRow(
                    label = "Location (background / always)",
                    granted = bgGranted,
                    onFix = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            bgPermLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }
                    }
                )
            }

            // Battery Warning
            if (!batteryIgnored) {
                GlassCard(modifier = Modifier.border(0.5.dp, StatusWarn.copy(alpha = 0.4f), RoundedCornerShape(16.dp))) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⚡", fontSize = 18.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Battery optimisation is ON", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text("This will pause location syncing. Tap Fix.", color = TextSecondary, fontSize = 12.sp, lineHeight = 22.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { openBatterySettings(context) }) {
                            Text("Fix", color = AccentRose, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            TextButton(
                onClick = {
                    Prefs.clear(context)
                    onReset()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Reset Initial / Setup again", color = TextSecondary)
            }
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onFix: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (granted) StatusOk else StatusWarn,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(label, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (!granted) {
            TextButton(onClick = onFix, contentPadding = PaddingValues(0.dp)) {
                Text("Fix", color = AccentRose, fontSize = 13.sp)
            }
        }
    }
}

private fun openBatterySettings(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:${context.packageName}"))
        )
        return
    } catch (_: Exception) {}

    try {
        context.startActivity(
            Intent().setComponent(
                ComponentName(
                    "com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                )
            ).putExtra("package_name", context.packageName)
                .putExtra("package_label", "Distance")
        )
        return
    } catch (_: Exception) {}

    try {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}"))
        )
    } catch (_: Exception) {}
}
