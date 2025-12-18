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

class RpcService : Service(), GatewayStateListener {
    private var gateway: DiscordGateway? = null
    private val connectionTimeoutHandler = Handler(Looper.getMainLooper())
    private var connectionTimeoutRunnable: Runnable? = null

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
                val token = intent.getStringExtra("TOKEN") ?: return START_NOT_STICKY
                val appName = intent.getStringExtra("APP_NAME") ?: "Custom RPC"
                
                // Hentikan koneksi lama jika ada
                gateway?.close()

                startPersistentNotification(appName)
                gateway = DiscordGateway(token, this)
                gateway?.connect()
                
                // Mulai timer timeout
                startConnectionTimeout()
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startConnectionTimeout() {
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
        
        // Stop any pending reconnects immediately
        reconnectHandler.removeCallbacksAndMessages(null)
        clearConnectionTimeout()

        try {
            // Panggil close dengan shutdownClient = true karena service akan berhenti total
            gateway?.close(shutdownClient = true)
        } catch (e: Exception) {
            Log.e("RpcService", "Error closing gateway in onDestroy", e)
        }
        gateway = null // Prevent usage after destruction

        // Release Locks
        if (wakeLock?.isHeld == true) wakeLock?.release()
        if (wifiLock?.isHeld == true) wifiLock?.release()
        
        super.onDestroy()
        // Broadcast final disconnected state
        onStateChange(false, "Disconnected")
        
        // Auto-Restart if NOT intentional stop (e.g. System Kill / Swipe Notification)
        if (!isIntentionalStop) {
            Log.w("RpcService", getString(R.string.msg_service_killed))
            val restartIntent = Intent(applicationContext, RestartReceiver::class.java)
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                applicationContext, 1, restartIntent, android.app.PendingIntent.FLAG_ONE_SHOT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(android.app.AlarmManager::class.java)
            alarmManager?.set(android.app.AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 1000, pendingIntent)
        }
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
        lastIsConnected = isConnected
        lastMessage = message
        Log.d("RpcService", "Gateway state changed: isConnected=$isConnected, message=$message")
        // Jika kita mendapat status berhasil, batalkan timeout dan reconnect
        if (isConnected) {
            clearConnectionTimeout()
            reconnectHandler.removeCallbacksAndMessages(null)
            
            // Auto-Restore Presence on Connect (Only when fully READY)
            if (message.contains("Ready", ignoreCase = true)) {
                restoreLastPresence()
            }
        } else {
            // Jika putus koneksi dan BUKAN user yang stop, coba reconnect
            // Kita reconnect untuk semua error KECUALI Authentication Failed (Code 4004)
            val isAuthFailed = message.contains("4004") || message.contains("Invalid Session", ignoreCase = true)
            
            if (!isIntentionalStop && !isConnected && !isAuthFailed) {
                 Log.w("RpcService", "Connection dropped unexpectedly ($message). Reconnecting in 5 seconds...")
                 reconnectHandler.postDelayed({
                     Log.i("RpcService", "Auto-Reconnecting now...")
                     gateway?.connect()
                 }, 5000)
            }
        }

        val intent = Intent(ACTION_STATUS_UPDATE).apply {
            putExtra("IS_CONNECTED", isConnected)
            putExtra("MESSAGE", message)
            setPackage(packageName) // Explicitly set package to ensure delivery
        }
        sendBroadcast(intent)
        
        // Update Notification if it's a Ping update
        if (isConnected && message.contains("ms")) {
            updateNotification(message)
        }
    }

    private fun updateNotification(text: String) {
        val largeIconBitmap = android.graphics.BitmapFactory.decodeResource(resources, R.drawable.ic_app_logo_new)
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, notificationIntent, android.app.PendingIntent.FLAG_IMMUTABLE
        )

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
                name = sharedPref.getString("appName", "Custom App") ?: "Custom App",
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

    private fun startPersistentNotification(appName: String) {
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

        val notification = NotificationCompat.Builder(this, "RPC_CHANNEL_V5") // Bump ID again
            .setContentTitle(getString(R.string.notif_title_active))
            .setContentText(getString(R.string.notif_desc_background, appName))
            .setSmallIcon(R.drawable.ic_app_logo_new) // Small icon must be white/transparent
            .setLargeIcon(largeIconBitmap) // Large icon can be colored
            .setContentIntent(pendingIntent) // Open App on Click
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_MAX) // For pre-O
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
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

    override fun onBind(intent: Intent?): IBinder? = null
}