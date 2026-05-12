package com.example.customrpc

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.customrpc.ui.theme.*
import java.util.Calendar
import java.util.Date

import com.example.customrpc.ui.screens.*

enum class Screen { Login, Dashboard, Settings, About, AppOverrides }

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        val sharedPref = getSharedPreferences("RpcSettings", Context.MODE_PRIVATE)
        setContent {
            CustomRPCTheme {
                MainApp(sharedPref)
            }
        }
        
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val uri = intent.data
            if (uri != null && uri.scheme == "customrpc" && uri.host == "token") {
                val token = uri.getQueryParameter("value")
                if (!token.isNullOrEmpty()) {
                    val sharedPref = getSharedPreferences("RpcSettings", Context.MODE_PRIVATE)
                    sharedPref.edit().putString("token", token).apply()
                    Toast.makeText(this, "Token Saved", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }
}

@Composable
fun MainApp(sharedPref: android.content.SharedPreferences) {
    var currentScreen by remember { 
        mutableStateOf(if (sharedPref.getString("token", "").isNullOrBlank()) Screen.Login else Screen.Dashboard)
    }
    val context = LocalContext.current

    // Connection Status State
    var isConnected by remember { mutableStateOf(false) }
    var connectionMessage by remember { mutableStateOf("OFFLINE") }
    var isNetworkAvailable by remember { mutableStateOf(true) }

    DisposableEffect(context) {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isNetworkAvailable = true
            }
            override fun onLost(network: Network) {
                val currentNetwork = connectivityManager?.activeNetwork
                val currentCaps = connectivityManager?.getNetworkCapabilities(currentNetwork)
                isNetworkAvailable = currentCaps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                isNetworkAvailable = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            // Initial check
            val activeNetwork = connectivityManager?.activeNetwork
            val caps = connectivityManager?.getNetworkCapabilities(activeNetwork)
            isNetworkAvailable = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

            connectivityManager?.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to register network callback", e)
            isNetworkAvailable = true 
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == RpcService.ACTION_STATUS_UPDATE) {
                    isConnected = intent.getBooleanExtra("IS_CONNECTED", false)
                    connectionMessage = intent.getStringExtra("MESSAGE") ?: "Unknown"
                }
            }
        }
        context.registerReceiver(
            receiver, 
            IntentFilter(RpcService.ACTION_STATUS_UPDATE), 
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        // Probe status immediately upon registering
        val probeIntent = Intent(context, RpcService::class.java).apply { action = RpcService.ACTION_PROBE }
        context.startService(probeIntent)

        onDispose {
            context.unregisterReceiver(receiver)
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        }
    }

    BackHandler(enabled = currentScreen != Screen.Dashboard) {
        if (currentScreen == Screen.About || currentScreen == Screen.Settings || currentScreen == Screen.AppOverrides) {
             currentScreen = Screen.Dashboard
        } else if (currentScreen == Screen.Login) {
             (context as? android.app.Activity)?.finish()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize().systemBarsPadding(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (currentScreen) {
            Screen.Login -> LoginScreen(
                sharedPref = sharedPref,
                onLoginSuccess = { currentScreen = Screen.Dashboard }
            )
            Screen.Dashboard -> DashboardScreen(
                sharedPref = sharedPref,
                isConnected = isConnected,
                isNetworkAvailable = isNetworkAvailable,
                message = connectionMessage,
                onNavigateSettings = { currentScreen = Screen.Settings },
                onNavigateAbout = { currentScreen = Screen.About },
                onNavigateOverrides = { currentScreen = Screen.AppOverrides },
                onLogout = { currentScreen = Screen.Login }
            )
            Screen.Settings -> SettingsScreen(
                sharedPref = sharedPref,
                onBack = { currentScreen = Screen.Dashboard },
                isConnected = isConnected
            )
            Screen.About -> AboutScreen(
                onBack = { currentScreen = Screen.Dashboard }
            )
            Screen.AppOverrides -> AppOverridesScreen(
                sharedPref = sharedPref,
                onBack = { currentScreen = Screen.Dashboard }
            )
        }
    }
}

// Screens have been moved to com.example.customrpc.ui.screens package.