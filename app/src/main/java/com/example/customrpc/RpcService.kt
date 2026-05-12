package com.example.customrpc

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import android.util.Log // Import Log
import android.app.usage.UsageStatsManager
import android.app.usage.UsageEvents
import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class RpcService : Service(), GatewayStateListener {
    private var gateway: DiscordGateway? = null
    private val connectionTimeoutHandler = Handler(Looper.getMainLooper())
    private var connectionTimeoutRunnable: Runnable? = null
    private var reconnectAttempts = 0
    private val appMonitorHandler = Handler(Looper.getMainLooper())
    private var lastPackage: String? = null

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_UPDATE_PRESENCE = "ACTION_UPDATE_PRESENCE"
        const val ACTION_PROBE = "ACTION_PROBE"
        const val ACTION_STATUS_UPDATE = "com.example.customrpc.STATUS_UPDATE"
        const val CONNECTION_TIMEOUT = 15000L // 15 detik
    }

    // Cache state to reply to probes
    private var lastIsConnected = false
    private var lastMessage = "Disconnected"

    override fun onCreate() {
        super.onCreate()
        Log.d("RpcService", "onCreate called. Initializing foreground service...")
        
        // Always call startForeground in onCreate to prevent ForegroundServiceDidNotStartInTimeException
        val sharedPref = getSharedPreferences("RpcSettings", android.content.Context.MODE_PRIVATE)
        val appName = sharedPref.getString("appName", "Custom RPC") ?: "Custom RPC"
        startPersistentNotification(appName)
        startAppMonitor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("RpcService", "onStartCommand called with action: ${intent?.action}")
        when (intent?.action) {
            ACTION_PROBE -> {
                // Reply immediately with cached state
                // Use a separate broadcast to avoid blocking
                // We reuse onStateChange logic but force a broadcast
                val broadcastIntent = Intent(ACTION_STATUS_UPDATE).apply {
                    putExtra("IS_CONNECTED", lastIsConnected)
                    putExtra("MESSAGE", lastMessage)
                    setPackage(packageName)
                }
                sendBroadcast(broadcastIntent)
            }
            ACTION_START -> {
                isIntentionalStop = false // Reset intentional stop flag
                val sharedPref = getSharedPreferences("RpcSettings", android.content.Context.MODE_PRIVATE)
                val token = intent?.getStringExtra("TOKEN") ?: sharedPref.getString("token", "") ?: ""
                val appName = intent?.getStringExtra("APP_NAME") ?: sharedPref.getString("appName", "Custom RPC") ?: "Custom RPC"
                
                if (token.isBlank()) {
                    Log.e("RpcService", "Cannot start: Token is empty")
                    return START_NOT_STICKY
                }

                // Hapus pending reconnects
                reconnectHandler.removeCallbacksAndMessages(null)

                // Hentikan koneksi lama jika ada
                gateway?.close()

                // startPersistentNotification(appName) // Move to onCreate
                val statusStr = when (sharedPref.getInt("userStatus", 0)) {
                    1 -> "idle"
                    2 -> "dnd"
                    3 -> "invisible"
                    else -> "online"
                }
                gateway = DiscordGateway(token, statusStr, this)
                gateway?.connect()
                
                // Update notification with the specific app name from intent if available
                updateNotification(appName)
                
                resetReconnection()
                // Mulai timer timeout
                startConnectionTimeout()

                // Save active state
                sharedPref.edit().putBoolean("serviceActive", true).apply()
            }
            ACTION_UPDATE_PRESENCE -> {
                val presenceData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getSerializableExtra("PRESENCE_DATA", PresenceData::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getSerializableExtra("PRESENCE_DATA") as? PresenceData
                }
                
                presenceData?.let {
                    gateway?.updatePresence(it)
                }
            }
            ACTION_STOP -> {
                isIntentionalStop = true
                Log.i("RpcService", "ACTION_STOP received. Closing connection and stopping service.")
                
                // Explicitly close gateway and clear reconnects
                gateway?.close(shutdownClient = true)
                gateway = null
                resetReconnection()
                clearConnectionTimeout()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                
                // Save inactive state
                val sharedPref = getSharedPreferences("RpcSettings", android.content.Context.MODE_PRIVATE)
                sharedPref.edit().putBoolean("serviceActive", false).apply()
                
                broadcastStatus(false, "Disconnected")
                stopSelf()
            }
        }
        val sharedPref = getSharedPreferences("RpcSettings", android.content.Context.MODE_PRIVATE)
        val wasActive = sharedPref.getBoolean("serviceActive", false)
        return if (isIntentionalStop || !wasActive) START_NOT_STICKY else START_STICKY
    }

    private fun broadcastStatus(isConnected: Boolean, message: String) {
        lastIsConnected = isConnected
        lastMessage = message
        
        val intent = Intent(ACTION_STATUS_UPDATE).apply {
            putExtra("IS_CONNECTED", isConnected)
            putExtra("MESSAGE", message)
            setPackage(packageName)
        }
        sendBroadcast(intent)
        
        if (isConnected && message.contains("ms")) {
            updateNotification(message)
        }
    }

    private fun startConnectionTimeout() {
        clearConnectionTimeout()
        Log.d("RpcService", "Starting connection timeout timer for ${CONNECTION_TIMEOUT}ms.")
        connectionTimeoutRunnable = Runnable {
            Log.w("RpcService", "Connection timeout triggered! Gateway did not respond in time.")
            // Panggil close tanpa shutdown client agar bisa connect lagi
            gateway?.close(shutdownClient = false)
            onStateChange(false, "Connection Timed Out")
        }
        connectionTimeoutHandler.postDelayed(connectionTimeoutRunnable!!, CONNECTION_TIMEOUT)
    }

    private fun clearConnectionTimeout() {
        connectionTimeoutRunnable?.let {
            Log.d("RpcService", "Clearing connection timeout timer.")
            connectionTimeoutHandler.removeCallbacks(it)
        }
    }

    override fun onDestroy() {
        Log.w("RpcService", "onDestroy called. Service is being stopped.")
        isIntentionalStop = true // Set this first to prevent any re-entry or reconnects
        
        // Stop any pending reconnects immediately
        reconnectHandler.removeCallbacksAndMessages(null)
        clearConnectionTimeout()

        try {
            gateway?.close(shutdownClient = true)
        } catch (e: Exception) {
            Log.e("RpcService", "Error closing gateway in onDestroy", e)
        }
        gateway = null 

        // Release Locks
        if (wakeLock?.isHeld == true) wakeLock?.release()
        if (wifiLock?.isHeld == true) wifiLock?.release()
        stopAppMonitor()
        
        super.onDestroy()
        // Broadcast final disconnected state
        broadcastStatus(false, "Disconnected")
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Only restart if it wasn't an intentional stop
        if (!isIntentionalStop) {
            Log.w("RpcService", "Task Removed (Swiped away). Attempting restart...")
            val restartIntent = Intent(applicationContext, RestartReceiver::class.java)
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                applicationContext, 1, restartIntent, android.app.PendingIntent.FLAG_ONE_SHOT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(android.app.AlarmManager::class.java)
            alarmManager?.set(android.app.AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 1000, pendingIntent)
        } else {
             Log.i("RpcService", "Task Removed, but stop was intentional. No restart.")
        }
        
        super.onTaskRemoved(rootIntent)
    }

    @Volatile private var isIntentionalStop = false
    private val reconnectHandler = Handler(Looper.getMainLooper())

    override fun onStateChange(isConnected: Boolean, message: String) {
        Log.d("RpcService", "Gateway state changed: isConnected=$isConnected, message=$message")
        
        val isAuthFailed = message.contains("4004") || message.contains("Invalid Session", ignoreCase = true)

        if (isConnected) {
            clearConnectionTimeout()
            resetReconnection()
            
            if (message.contains("Ready", ignoreCase = true)) {
                restoreLastPresence()
            }
        } else {
            if (!isIntentionalStop && !isAuthFailed) {
                 attemptReconnect()
            }
        }

        broadcastStatus(isConnected, message)
    }

    private fun resetReconnection() {
        reconnectAttempts = 0
        reconnectHandler.removeCallbacksAndMessages(null)
    }

    private fun attemptReconnect() {
        if (isIntentionalStop) return
        
        reconnectAttempts++
        val delay = (5 * java.lang.Math.pow(2.0, (reconnectAttempts - 1).coerceAtMost(4).toDouble()) * 1000).toLong()
        val secondsLeft = delay / 1000
        
        Log.w("RpcService", "Reconnecting attempt #$reconnectAttempts in ${secondsLeft}s...")
        broadcastStatus(false, "Reconnecting in ${secondsLeft}s...")
        
        reconnectHandler.postDelayed({
            Log.i("RpcService", "Auto-Reconnecting now...")
            startConnectionTimeout()
            gateway?.connect()
        }, delay)
    }

    private fun updateNotification(text: String) {
        val largeIconBitmap = android.graphics.BitmapFactory.decodeResource(resources, R.drawable.ic_app_logo_new)
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, notificationIntent, android.app.PendingIntent.FLAG_IMMUTABLE
        )

        // Actions
        val stopIntent = Intent(this, RpcService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = android.app.PendingIntent.getService(this, 2, stopIntent, android.app.PendingIntent.FLAG_IMMUTABLE)

        // Ambil nama app dari prefs untuk konsistensi
        val sharedPref = getSharedPreferences("RpcSettings", android.content.Context.MODE_PRIVATE)
        val appName = sharedPref.getString("appName", "Custom RPC") ?: "Custom RPC"

        val notification = NotificationCompat.Builder(this, "RPC_CHANNEL_V5")
            .setContentTitle(getString(R.string.notif_title_active))
            .setContentText(getString(R.string.notif_desc_background, appName)) // Format string usage
            .setSmallIcon(R.drawable.ic_app_logo_new)
            .setLargeIcon(largeIconBitmap)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true) // PENTING: Jangan bunyi/getar saat update
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(R.drawable.ic_stop, "STOP", stopPendingIntent)
            .build()
            
        val notificationManager = getSystemService(android.app.NotificationManager::class.java)
        notificationManager?.notify(1, notification)
    }

    private fun restoreLastPresence() {
        val sharedPref = getSharedPreferences("RpcSettings", android.content.Context.MODE_PRIVATE)
        val appId = sharedPref.getString("appId", "") ?: ""
        if (appId.isEmpty()) return // No settings saved yet

        try {
             val presence = PresenceData(
                appId = appId,
                name = sharedPref.getString("appName", "Custom RPC") ?: "Custom RPC",
                details = sharedPref.getString("details", "") ?: "",
                state = sharedPref.getString("state", "") ?: "",
                largeImageKey = sharedPref.getString("largeImageKey", "") ?: "",
                largeImageText = sharedPref.getString("largeImageText", "") ?: "",
                smallImageKey = sharedPref.getString("smallImageKey", "") ?: "",
                smallImageText = sharedPref.getString("smallImageText", "") ?: "",
                activityType = sharedPref.getInt("activityType", 0),
                partySize = sharedPref.getString("partySize", "")?.toIntOrNull(),
                partyMax = sharedPref.getString("partyMax", "")?.toIntOrNull(),
                button1Label = sharedPref.getString("btn1Text", "") ?: "",
                button1Url = sharedPref.getString("btn1Url", "") ?: "",
                button2Label = sharedPref.getString("btn2Text", "") ?: "",
                button2Url = sharedPref.getString("btn2Url", "") ?: "",
                streamingUrl = sharedPref.getString("streamingUrl", "") ?: "",
                userStatus = when (try { sharedPref.getInt("userStatus", 0) } catch (e: Exception) { 0 }) {
                    0 -> "online"
                    1 -> "idle"
                    2 -> "dnd"
                    3 -> "invisible"
                    else -> "online"
                },
                timestampStart = when (sharedPref.getInt("timestampMode", 2)) {
                    1 -> System.currentTimeMillis() // Elapsed Time (Start now)
                    2 -> { // Local Time (Start of Day)
                         val cal = java.util.Calendar.getInstance()
                         cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                         cal.set(java.util.Calendar.MINUTE, 0)
                         cal.set(java.util.Calendar.SECOND, 0)
                         cal.timeInMillis
                    }
                    3 -> sharedPref.getLong("customStartTime", 0L).takeIf { it != 0L } // Custom
                    else -> null
                },
                timestampEnd = when (sharedPref.getInt("timestampMode", 2)) {
                    3 -> sharedPref.getLong("customEndTime", 0L).takeIf { it != 0L }
                    else -> null
                }
            )
            Log.i("RpcService", "Restoring last presence: $presence")
            gateway?.updatePresence(presence)
        } catch (e: Exception) {
            Log.e("RpcService", "Failed to restore presence: ${e.message}")
        }
    }

    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null

    private fun startPersistentNotification(appName: String?) {
        val finalAppName = appName ?: getSharedPreferences("RpcSettings", android.content.Context.MODE_PRIVATE).getString("appName", "Custom RPC") ?: "Custom RPC"

        // Acquire WakeLock (Only if not already held)
        if (wakeLock == null) {
            val powerManager = getSystemService(android.os.PowerManager::class.java)
            wakeLock = powerManager?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "CustomRPC::WakeLock")
            wakeLock?.acquire(10*60*60*1000L /*10 hours*/)
        }

        // Acquire WifiLock (Only if not already held)
        if (wifiLock == null) {
            val wifiManager = getSystemService(android.net.wifi.WifiManager::class.java)
            val lockMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.net.wifi.WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                @Suppress("DEPRECATION")
                android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wifiManager?.createWifiLock(lockMode, "CustomRPC::WifiLock")
            wifiLock?.acquire()
        }

        createNotificationChannel()
        
        // Decode Large Icon (Full Color)
        val largeIconBitmap = android.graphics.BitmapFactory.decodeResource(resources, R.drawable.ic_app_logo_new)

        // Create PendingIntent to open MainActivity
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, notificationIntent, android.app.PendingIntent.FLAG_IMMUTABLE
        )

        // Actions
        val stopIntent = Intent(this, RpcService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = android.app.PendingIntent.getService(this, 2, stopIntent, android.app.PendingIntent.FLAG_IMMUTABLE)

        val startIntent = Intent(this, RpcService::class.java).apply { action = ACTION_START }
        val startPendingIntent = android.app.PendingIntent.getService(this, 3, startIntent, android.app.PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, "RPC_CHANNEL_V5")
            .setContentTitle(getString(R.string.notif_title_active))
            .setContentText(getString(R.string.notif_desc_background, finalAppName))
            .setSmallIcon(R.drawable.ic_app_logo_new)
            .setLargeIcon(largeIconBitmap)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(R.drawable.ic_play, "START", startPendingIntent)
            .addAction(R.drawable.ic_stop, "STOP", stopPendingIntent)
            .build()
        startForeground(1, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                "RPC_CHANNEL_V5", // Bump ID again
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_HIGH // High importance
            ).apply {
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(serviceChannel)
        }
    }

    private val appMonitorRunnable = object : Runnable {
        override fun run() {
            if (gateway != null && lastIsConnected) {
                checkForegroundApp()
            }
            appMonitorHandler.postDelayed(this, 8000L) // Poll every 8s
        }
    }

    private fun startAppMonitor() {
        appMonitorHandler.removeCallbacks(appMonitorRunnable)
        appMonitorHandler.postDelayed(appMonitorRunnable, 5000L)
    }

    private fun stopAppMonitor() {
        appMonitorHandler.removeCallbacks(appMonitorRunnable)
    }

    private fun checkForegroundApp() {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 30000 // Last 30s
        
        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
        var topPackage: String? = null
        val event = UsageEvents.Event()
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                topPackage = event.packageName
            }
        }
        
        if (topPackage != null && topPackage != lastPackage && topPackage != packageName) {
            Log.d("RpcService", "Foreground app changed: $topPackage")
            lastPackage = topPackage
            handleAppChange(topPackage)
        }
    }

    private fun handleAppChange(pkg: String) {
        val sharedPref = getSharedPreferences("RpcSettings", Context.MODE_PRIVATE)
        val overridesJson = sharedPref.getString("app_overrides", "[]") ?: "[]"
        val overrides: List<AppOverride> = Gson().fromJson(overridesJson, object : TypeToken<List<AppOverride>>() {}.type)
        
        val override = overrides.find { it.packageName == pkg }
        if (override != null) {
            Log.i("RpcService", "Applying override for ${override.appName}")
            
            // Apply Dynamic Variables
            val dynamicPresence = override.rpcData.copy(
                name = override.rpcData.name.replace("{app_name}", override.appName).replace("{app_pkg}", pkg),
                details = override.rpcData.details.replace("{app_name}", override.appName).replace("{app_pkg}", pkg),
                state = override.rpcData.state.replace("{app_name}", override.appName).replace("{app_pkg}", pkg),
                largeImageKey = override.rpcData.largeImageKey.replace("{app_pkg}", pkg),
                smallImageKey = override.rpcData.smallImageKey.replace("{app_pkg}", pkg)
            )
            
            gateway?.updatePresence(dynamicPresence)
        } else {
            // Fallback to default
            Log.d("RpcService", "No override for $pkg, restoring default presence")
            restoreLastPresence()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}