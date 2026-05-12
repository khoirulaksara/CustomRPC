package com.example.customrpc.ui.screens

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.customrpc.AppOverride
import com.example.customrpc.R
import com.example.customrpc.ui.theme.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppOverridesScreen(
    sharedPref: android.content.SharedPreferences,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var appList by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var hasPermission by remember { mutableStateOf(hasUsageStatsPermission(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = hasUsageStatsPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }

            val apps = resolveInfos.map { info ->
                val appInfo = info.activityInfo.applicationInfo
                AppInfo(
                    name = info.loadLabel(pm).toString(),
                    packageName = appInfo.packageName,
                    icon = info.loadIcon(pm)
                )
            }.distinctBy { it.packageName }.sortedBy { it.name }

            appList = apps
            isLoading = false
        }
    }

    // Re-check permission when returning to app
    // In a real app we'd use LifecycleObserver, but for now this is okay
    
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    // Configuration states
    var customAppName by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("") }
    var largeKey by remember { mutableStateOf("") }
    var smallKey by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(0) }

    val overridesJson = sharedPref.getString("app_overrides", "[]") ?: "[]"
    var overrides by remember { 
        mutableStateOf(Gson().fromJson<List<AppOverride>>(overridesJson, object : TypeToken<List<AppOverride>>() {}.type)) 
    }

    Column(modifier = Modifier.fillMaxSize().background(DiscordDarkBackground)) {
        // Top Bar
        TopAppBar(
            title = { Text("App Overrides", color = Color.White) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DiscordDarkSecondary)
        )

        if (!hasPermission) {
            PermissionWarning {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        }

        // Active Overrides Section
        if (overrides.isNotEmpty()) {
            Text(
                "Active Overrides", 
                color = DiscordDarkPrimary, 
                fontWeight = FontWeight.Bold, 
                modifier = Modifier.padding(16.dp)
            )
            LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                items(overrides) { override ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(override.appName, color = Color.White)
                        IconButton(onClick = {
                            overrides = overrides.filter { it.packageName != override.packageName }
                            sharedPref.edit().putString("app_overrides", Gson().toJson(overrides)).apply()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = DiscordRed)
                        }
                    }
                }
            }
            HorizontalDivider(color = DiscordDarkSecondary, thickness = 1.dp)
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("Search apps to add...", color = DiscordDarkTextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = DiscordDarkTextMuted) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = DiscordDarkPrimary,
                unfocusedBorderColor = DiscordDarkSecondary
            ),
            shape = RoundedCornerShape(12.dp)
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DiscordDarkPrimary)
            }
        } else {
            val filteredApps = appList.filter { 
                it.name.contains(searchQuery, ignoreCase = true) || 
                it.packageName.contains(searchQuery, ignoreCase = true)
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filteredApps) { app ->
                    AppItem(app) {
                        selectedApp = app
                        customAppName = app.name
                        details = "Playing {app_name}"
                        state = "In-app"
                        largeKey = "{app_pkg}"
                        smallKey = ""
                        type = 0
                        showDialog = true
                    }
                }
            }
        }
    }

    if (showDialog && selectedApp != null) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = DiscordDarkSecondary,
            title = { Text("Configure RPC for ${selectedApp!!.name}", color = Color.White) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = customAppName,
                        onValueChange = { customAppName = it },
                        label = { Text("RPC Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = details,
                        onValueChange = { details = it },
                        label = { Text("Details") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = state,
                        onValueChange = { state = it },
                        label = { Text("State") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = largeKey,
                        onValueChange = { largeKey = it },
                        label = { Text("Large Image Key") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = smallKey,
                        onValueChange = { smallKey = it },
                        label = { Text("Small Image Key") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val basePresence = Gson().fromJson(sharedPref.getString("presence_data", "{}"), com.example.customrpc.PresenceData::class.java) 
                    // Note: In a real app we'd fetch current settings. For now, we build a new one.
                    val newPresence = com.example.customrpc.PresenceData(
                        appId = sharedPref.getString("appId", "") ?: "",
                        name = customAppName,
                        details = details,
                        state = state,
                        largeImageKey = largeKey,
                        largeImageText = "",
                        smallImageKey = smallKey,
                        smallImageText = "",
                        activityType = type,
                        partySize = null,
                        partyMax = null,
                        button1Label = "",
                        button1Url = "",
                        button2Label = "",
                        button2Url = "",
                        timestampStart = System.currentTimeMillis(),
                        timestampEnd = null
                    )
                    val newOverride = AppOverride(selectedApp!!.packageName, selectedApp!!.name, newPresence)
                    overrides = overrides.filter { it.packageName != selectedApp!!.packageName } + newOverride
                    sharedPref.edit().putString("app_overrides", Gson().toJson(overrides)).apply()
                    showDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AppItem(app: AppInfo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            bitmap = app.icon.toBitmap().asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(40.dp).clip(CircleShape)
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(app.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(app.packageName, color = DiscordDarkTextMuted, fontSize = 12.sp)
        }
    }
}

@Composable
fun PermissionWarning(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF432B2B))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Usage Access Required", color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                "This feature needs permission to detect which app is in the foreground.",
                color = DiscordDarkTextMuted,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Grant Permission")
            }
        }
    }
}

fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
    return mode == AppOpsManager.MODE_ALLOWED
}
