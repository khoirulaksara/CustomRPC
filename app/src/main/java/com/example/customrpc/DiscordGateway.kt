package com.example.customrpc

import android.util.Log
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

interface GatewayStateListener {
    fun onStateChange(isConnected: Boolean, message: String)
}

class DiscordGateway(
    private val token: String,
    private val listener: GatewayStateListener
) : WebSocketListener() {
    private val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    private var webSocket: WebSocket? = null
    private var heartbeatInterval: Long = 41250
    @Volatile private var isConnected = false
    private var sequence: Int? = null

    // Discord Gateway Intents
    // Kita tidak perlu menerima pesan atau presence orang lain, cukup kirim presence kita sendiri.
    // Mengeset ke 0 untuk menghemat baterai & data.
    private val intents = 0


    fun connect() {
        if (isConnected) {
            listener.onStateChange(true, "Already connected.")
            return
        }
        Log.i("DiscordGateway", "Attempting to connect to Discord Gateway.")
        val request = Request.Builder().url("wss://gateway.discord.gg/?v=10&encoding=json").build()
        webSocket = client.newWebSocket(request, this)
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        isConnected = true
        Log.i("DiscordGateway", "WebSocket connection opened.")
        listener.onStateChange(true, "WebSocket Open. Waiting for HELLO...")
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        // Log.d("DiscordGateway", "Received: $text") // Disabled to save logcat space
        try {
            val json = JSONObject(text)
            val op = json.getInt("op")

            if (!json.isNull("s")) {
                sequence = json.getInt("s")
            }
            // (Sequence captured)

            when (op) {
                10 -> { // Hello -> Konfirmasi koneksi
                    val d = json.optJSONObject("d")
                    if (d != null) {
                        heartbeatInterval = d.getLong("heartbeat_interval")
                        startHeartbeat()
                        sendIdentify()
                        Log.i("DiscordGateway", "HELLO received. Identify sent.")
                        listener.onStateChange(true, "Connected. Identifying...")
                    } else {
                        Log.e("DiscordGateway", "Connection Failed: Invalid HELLO packet.")
                        listener.onStateChange(false, "Connection Failed: Invalid HELLO packet.")
                        close(1002, "Invalid HELLO")
                    }
                }
                0 -> { // Dispatch
                    val eventType = json.optString("t")
                    when (eventType) {
                        "READY" -> {
                            // PRIORITAS: Ubah status UI dulu sebelum parsing berat
                            listener.onStateChange(true, "Connected! (Ready)")
                            Log.i("DiscordGateway", "READY event received! Session established.")
                        }
                        else -> {
                            // Log.d("DiscordGateway", "Unhandled DISPATCH event type: $eventType")
                        }
                    }
                }
                1 -> sendHeartbeat() // Heartbeat
                7 -> { // Reconnect - Discord meminta kita untuk reconnect
                    Log.w("DiscordGateway", "Received RECONNECT (Opcode 7). Reconnecting...")
                    listener.onStateChange(false, "Reconnecting...")
                    close(1012, "Reconnect requested")
                    connect()
                }
                9 -> { // Invalid Session -> Token salah atau sesi kedaluwarsa
                    isConnected = false
                    Log.e("DiscordGateway", "Connection Failed: Invalid Session. Please check your token.")
                    listener.onStateChange(false, "Connection Failed: Invalid Session. Please check your token.")
                    close(4004, "Invalid session")
                }
                11 -> {
                    val latency = System.currentTimeMillis() - lastHeartbeatTime
                    Log.d("DiscordGateway", "Heartbeat ACK received. Ping: ${latency}ms")
                    listener.onStateChange(true, "Connected • ${latency}ms")
                }
                else -> {
                    Log.w("DiscordGateway", "Unhandled OP code: $op. Full message: $text")
                }
            }
        } catch (e: Exception) {
            Log.e("DiscordGateway", "Error parsing message", e)
            listener.onStateChange(false, "Connection Failed: Error parsing server message.")
            close(1008, "Message parsing error")
        }
    }

    private fun startHeartbeat() {
        Thread {
            while (isConnected) {
                try {
                    Thread.sleep(heartbeatInterval)
                    sendHeartbeat()
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    Log.e("DiscordGateway", "Heartbeat thread interrupted.", e)
                    listener.onStateChange(false, "Heartbeat stopped.")
                }
            }
        }.start()
    }

    private var lastHeartbeatTime: Long = 0

    private fun sendHeartbeat() {
        if (!isConnected || webSocket == null) return
        lastHeartbeatTime = System.currentTimeMillis() // Track send time
        val heartbeat = JSONObject().apply {
            put("op", 1)
            put("d", sequence ?: JSONObject.NULL)
        }
        // Log.d("DiscordGateway", "Sending Heartbeat: $heartbeat")
        webSocket?.send(heartbeat.toString())
    }

    private fun sendIdentify() {
        val identify = JSONObject().apply {
            put("op", 2)
            put("d", JSONObject().apply {
                put("token", token)
                put("properties", JSONObject().apply {
                    put("\$os", "windows")
                    put("\$browser", "Discord Client")
                    put("\$device", "desktop")
                })
                put("intents", intents) // Menambahkan intents di sini
            })
        }
        Log.d("DiscordGateway", "Sending Identify payload with intents: $intents.")
        webSocket?.send(identify.toString())
    }

    fun updatePresence(presence: PresenceData) {
        if (!isConnected) {
             listener.onStateChange(false, "Cannot update presence, not connected.")
            return
        }

        val activity = JSONObject().apply {
            put("name", presence.name.ifBlank { "Custom App" }) 
            put("type", presence.activityType)
            
            if (presence.appId.isNotBlank()) put("application_id", presence.appId)

            if (presence.details.isNotBlank()) put("details", presence.details)
            if (presence.state.isNotBlank()) put("state", presence.state)

            // Assets
            val assets = JSONObject()
            if (presence.largeImageKey.isNotBlank()) {
                assets.put("large_image", presence.largeImageKey)
                if (presence.largeImageText.isNotBlank()) assets.put("large_text", presence.largeImageText)
            }

            if (presence.smallImageKey.isNotBlank()) {
                assets.put("small_image", presence.smallImageKey)
                if (presence.smallImageText.isNotBlank()) assets.put("small_text", presence.smallImageText)
            }
            if (assets.length() > 0) put("assets", assets)

            // Timestamps
            if (presence.timestampStart != null || presence.timestampEnd != null) {
                val timestamps = JSONObject()
                if (presence.timestampStart != null) timestamps.put("start", presence.timestampStart)
                if (presence.timestampEnd != null) timestamps.put("end", presence.timestampEnd)
                put("timestamps", timestamps)
            }

            // Party
            // Discord requires party.id if size is sent.
            if ((presence.partySize != null && presence.partyMax != null && presence.partyMax > 0) || !presence.partyId.isNullOrBlank()) {
                val party = JSONObject()
                if (presence.partySize != null && presence.partyMax != null) {
                    party.put("size", JSONArray().apply {
                        put(presence.partySize)
                        put(presence.partyMax)
                    })
                }
                // Use provided ID or fallback to random
                party.put("id", if (!presence.partyId.isNullOrBlank()) presence.partyId else java.util.UUID.randomUUID().toString())
                put("party", party)
            }

            // Secrets (Join Request)
            if (!presence.joinSecret.isNullOrBlank()) {
                val secrets = JSONObject()
                secrets.put("join", presence.joinSecret)
                put("secrets", secrets)
            }

            // Buttons
            // Buttons logic: "buttons" array of objects + "metadata" (sometimes required by Discord internally, or ignored).
            val buttons = JSONArray()
            if (presence.button1Label.isNotBlank() && presence.button1Url.isNotBlank()) {
                buttons.put(JSONObject().apply {
                    put("label", presence.button1Label)
                    put("url", presence.button1Url)
                })
            }
            if (presence.button2Label.isNotBlank() && presence.button2Url.isNotBlank()) {
                buttons.put(JSONObject().apply {
                    put("label", presence.button2Label)
                    put("url", presence.button2Url)
                })
            }
            // Only send buttons if assets are present (Discord requirement for User RPC)
            if (buttons.length() > 0 && assets.length() > 0) {
                put("buttons", buttons)
            }

            // Adding flags: 1 means INSTANCE. But user example uses instance=false.
            // put("flags", 1) 
            put("instance", false)
        }

        val presenceUpdate = JSONObject().apply {
            put("op", 3)
            put("d", JSONObject().apply {
                val status = presence.userStatus.takeIf { it.isNotBlank() } ?: "online"
                val isIdle = status.equals("idle", ignoreCase = true)
                
                put("since", if (isIdle) System.currentTimeMillis() else JSONObject.NULL)
                put("activities", JSONArray().put(activity))
                put("status", status)
                put("afk", isIdle)
            })
        }
        Log.d("DiscordGateway", "Updating Presence Payload: ${presenceUpdate.toString(4)}")
        webSocket?.send(presenceUpdate.toString())
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        isConnected = false
        Log.i("DiscordGateway", "WebSocket is closing. Code: $code, Reason: $reason")
        listener.onStateChange(false, "Disconnected: $reason (Code: $code)")
        this.webSocket = null // Clear webSocket reference
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        isConnected = false
        Log.e("DiscordGateway", "Connection Failed!", t)
        val message = "Connection Failed: ${t.message ?: "Unknown error"}"
        listener.onStateChange(false, message)
        this.webSocket = null // Clear webSocket reference
    }

    // Method to close the connection and optionally shut down the client's executor
    fun close(code: Int = 1000, reason: String = "User Closed", shutdownClient: Boolean = false) {
        if (webSocket != null) {
            webSocket?.close(code, reason)
        }
        isConnected = false
        Log.i("DiscordGateway", "Gateway closed. Reason: $reason")
        this.webSocket = null
        if (shutdownClient) {
            client.dispatcher.executorService.shutdown()
        }
    }
}