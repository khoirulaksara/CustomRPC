package com.example.customrpc.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import com.example.customrpc.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.customrpc.MainActivity
import com.example.customrpc.RpcService
import com.example.customrpc.ui.theme.*
import com.example.customrpc.ui.components.PresencePreview
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// State: 0=Offline (Red Particles), 1=Connecting (Yellow), 2=Online (Green)
data class StatusParticle(
    val angle: Float,
    val radiusOffset: Float,
    val size: Float,
    val speed: Float,
    val alpha: Float
)

@Composable
fun DashboardScreen(
    sharedPref: android.content.SharedPreferences,
    isConnected: Boolean, 
    isNetworkAvailable: Boolean,
    message: String,
    onNavigateSettings: () -> Unit,
    onNavigateAbout: () -> Unit,
    onNavigateOverrides: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val pm = context.getSystemService(android.os.PowerManager::class.java)
    var isBatteryOptimized by remember { 
        mutableStateOf(pm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !pm.isIgnoringBatteryOptimizations(context.packageName)) 
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isBatteryOptimized = pm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !pm.isIgnoringBatteryOptimizations(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val statusColor = when {
        isConnected -> DiscordGreen
        message.contains("Connecting", true) || message.contains("Identifying", true) -> Color(0xFFF47B0E) // Orange
        else -> DiscordRed
    }

    // Read current presence data for preview
    val appId = sharedPref.getString("appId", "") ?: ""
    val appName = sharedPref.getString("appName", "Custom RPC") ?: "Custom RPC"
    val type = sharedPref.getInt("activityType", 0)
    val details = sharedPref.getString("details", "") ?: ""
    val state = sharedPref.getString("state", "") ?: ""
    val largeKey = sharedPref.getString("largeImageKey", "") ?: ""
    val smallKey = sharedPref.getString("smallImageKey", "") ?: ""
    val tsMode = sharedPref.getInt("timestampMode", 0)
    val startTime = sharedPref.getLong("customStartTime", 0L).takeIf { it != 0L }
    val endTime = sharedPref.getLong("customEndTime", 0L).takeIf { it != 0L }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // App Bar Area (Top Nav)
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.title_dashboard), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Row {
                IconButton(onClick = onNavigateSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                }
                IconButton(onClick = onNavigateAbout) {
                    Icon(Icons.Default.Info, contentDescription = "About", tint = Color.White)
                }
            }
        }

        // Battery Warning
        if (isBatteryOptimized) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF432B2B)),
                onClick = {
                    try {
                        val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        intent.data = Uri.parse("package:${context.packageName}")
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Please disable optimization in settings manually", Toast.LENGTH_LONG).show()
                    }
                }
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = DiscordRed)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(stringResource(R.string.desc_battery_opt), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(stringResource(R.string.desc_battery_opt_fix), fontSize = 12.sp, color = DiscordDarkTextMuted)
                    }
                }
            }
        }

        // Network Warning
        if (!isNetworkAvailable) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 16.dp, end = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF432B2B))
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = DiscordRed)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(stringResource(R.string.status_no_internet), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(stringResource(R.string.msg_no_internet_warning), fontSize = 12.sp, color = DiscordDarkTextMuted)
                    }
                }
            }
        }

        // Status visual indicator (Restored Particle Ring Animation)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            val particles = remember {
                List(150) {
                    StatusParticle(
                        angle = Random.nextFloat() * 360f,
                        radiusOffset = (Random.nextFloat() - 0.5f) * 40f,
                        size = Random.nextFloat() * 3f + 1f,
                        speed = Random.nextFloat() * 1.2f + 0.5f,
                        alpha = Random.nextFloat() * 0.7f + 0.3f
                    )
                }
            }

            // Dynamic rotation logic
            var rotation by remember { mutableStateOf(0f) }
            val isConnecting = !isConnected && (message.contains("Connecting", true) || message.contains("Identifying", true))
            val isReady = isConnected
            
            val targetSpeed = when {
                isConnecting -> 2.0f // Kencang
                isReady -> 0.05f     // Lembut (Sangat Halus)
                else -> 0.0f         // Berhenti
            }
            val currentSpeed by animateFloatAsState(targetSpeed, label = "Speed")

            LaunchedEffect(Unit) {
                var lastFrameTime = withFrameMillis { it }
                while (true) {
                    val nextFrameTime = withFrameMillis { it }
                    val delta = (nextFrameTime - lastFrameTime) / 1000f
                    lastFrameTime = nextFrameTime
                    
                    if (currentSpeed > 0) {
                        rotation += delta * 360f * currentSpeed
                    }
                }
            }

            Canvas(modifier = Modifier.size(280.dp)) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val radius = (size.minDimension / 2f) - 30.dp.toPx()
                
                // 1. Draw Background Circle (Discord Dark Grey)
                drawCircle(
                    color = Color(0xFF2F3136),
                    radius = radius,
                    center = androidx.compose.ui.geometry.Offset(cx, cy)
                )
                
                // 2. Draw Particles
                particles.forEach { p ->
                    val currentAngle = (p.angle + rotation * p.speed) % 360f
                    val rad = Math.toRadians(currentAngle.toDouble())
                    val r = radius + p.radiusOffset.dp.toPx()
                    val px = cx + (r * cos(rad)).toFloat()
                    val py = cy + (r * sin(rad)).toFloat()
                    
                    drawCircle(
                        color = statusColor,
                        radius = p.size.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(px, py),
                        alpha = p.alpha
                    )
                }
            }
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if(isConnected) stringResource(R.string.status_online) 
                           else if(message.contains("Connecting", true)) stringResource(R.string.status_connecting) 
                           else if(!isNetworkAvailable) stringResource(R.string.status_no_internet)
                           else stringResource(R.string.status_offline),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = message, color = DiscordDarkTextMuted, textAlign = TextAlign.Center)
            }
        }

        // Presence Preview Section
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.label_active_preview), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DiscordDarkPrimary)
            Spacer(Modifier.height(8.dp))
            PresencePreview(
                appId = appId,
                appName = appName,
                type = type,
                details = details,
                state = state,
                largeImageKey = largeKey,
                smallImageKey = smallKey,
                timestampMode = tsMode,
                startTime = startTime,
                endTime = endTime
            )
        }

        // Actions Bottom Sheet
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = DiscordDarkSecondary),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Button(
                    onClick = {
                        if (isConnected) {
                            val intent = Intent(context, RpcService::class.java).apply { action = RpcService.ACTION_STOP }
                            context.startService(intent)
                        } else {
                            if (!isNetworkAvailable) {
                                Toast.makeText(context, context.getString(R.string.msg_action_requires_internet), Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val token = sharedPref.getString("token", "") ?: ""
                            val appId = sharedPref.getString("appId", "") ?: ""
                            if (appId.isBlank()) {
                                Toast.makeText(context, context.getString(R.string.msg_no_app_id), Toast.LENGTH_SHORT).show()
                                onNavigateSettings()
                                return@Button
                            }
                            if (token.isNotBlank()) {
                                val intent = Intent(context, RpcService::class.java).apply {
                                    action = RpcService.ACTION_START
                                    putExtra("TOKEN", token)
                                    putExtra("APP_NAME", sharedPref.getString("appName", "")?.ifBlank { "Custom RPC" })
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if(isConnected) DiscordRed else DiscordDarkPrimary)
                ) {
                    Text(if (isConnected) stringResource(R.string.btn_stop_connection) else stringResource(R.string.btn_start_connection), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onNavigateOverrides,
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("App Overrides", color = Color.White)
                }

                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedButton(
                    onClick = {
                        sharedPref.edit().putString("token", "").apply()
                        val intent = Intent(context, RpcService::class.java).apply { action = RpcService.ACTION_STOP }
                        context.startService(intent)
                        onLogout()
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text(stringResource(R.string.btn_logout), color = DiscordDarkText)
                }
            }
        }
    }
}
