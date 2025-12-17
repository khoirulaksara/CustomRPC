package com.example.customrpc

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.*

class MainActivity : AppCompatActivity() {

    // Views Containers
    private lateinit var viewLogin: View
    private lateinit var viewDashboard: View
    private lateinit var viewSettings: View
    private lateinit var viewAbout: View

    // Login View Elements
    private lateinit var loginTokenInput: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnTokenGuideLogin: Button

    // Dashboard View Elements
    private lateinit var tvDashboardStatus: TextView
    private lateinit var tvDashboardDesc: TextView
    private lateinit var btnToggleConnection: Button
    private lateinit var btnOpenConfig: Button
    private lateinit var btnStopService: Button
    private lateinit var btnLogout: ImageView

    // Settings View Elements
    private lateinit var appIdEditText: EditText
    private lateinit var appNameEditText: EditText
    private lateinit var activityTypeSpinner: Spinner
    private lateinit var userStatusSpinner: Spinner


    private lateinit var detailsEditText: EditText
    private lateinit var stateEditText: EditText
    private lateinit var partySizeEditText: EditText
    private lateinit var partyMaxEditText: EditText
    private lateinit var largeImageKeyEditText: EditText
    private lateinit var largeImageTextEditText: EditText
    private lateinit var smallImageKeyEditText: EditText
    private lateinit var smallImageTextEditText: EditText
    
    private lateinit var btn1Text: EditText
    private lateinit var btn1Url: EditText
    private lateinit var btn2Text: EditText
    private lateinit var btn2Url: EditText

    private lateinit var timestampSpinner: Spinner
    private lateinit var customTimestampLayout: View
    private lateinit var btnPickStartTime: Button
    private lateinit var tvStartTimeVal: TextView
    private lateinit var btnPickEndTime: Button
    private lateinit var tvEndTimeVal: TextView
    
    private lateinit var btnSaveApply: Button
    private lateinit var btnCancelConfig: Button

    private var customStartTime: Long? = null
    private var customEndTime: Long? = null
    private var isServiceConnected = false

    // Web Interface
    inner class WebAppInterface {
        @android.webkit.JavascriptInterface
        fun onTokenReceived(token: String) {
            runOnUiThread {
                var cleanToken = token.replace("\"", "").trim()
                handleSavedToken(cleanToken)
            }
        }
    }
    
    private fun handleSavedToken(token: String) {
         if (token.isNotEmpty()) {
            loginTokenInput.setText(token)
            saveSettings()
            Toast.makeText(this, getString(R.string.msg_token_saved), Toast.LENGTH_SHORT).show()
            
            // Close dialog if any
            discordLoginDialog?.dismiss()
            
            // Go to dashboard
            showDashboard()
        }
    }
    
    // Dialog Reference
    private var discordLoginDialog: android.app.Dialog? = null

    // Permissions
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Request Notification Permission for Android 13+
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        // Apply Material You (Dynamic Colors)
        com.google.android.material.color.DynamicColors.applyToActivitiesIfAvailable(this.application)

        // Request Ignore Battery Optimization
        val pm = getSystemService(android.os.PowerManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }
        setContentView(R.layout.activity_main)

        // Handle Window Insets (Edge-to-Edge)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Bind Containers
        viewLogin = findViewById(R.id.view_login)
        viewDashboard = findViewById(R.id.view_dashboard)
        viewSettings = findViewById(R.id.view_settings)
        viewAbout = findViewById(R.id.view_about)

        bindLoginViews()
        bindDashboardViews()
        bindSettingsViews()

        loadSettings()

        // Initial Routing
        val savedToken = loginTokenInput.text.toString()
        if (savedToken.isNotBlank()) {
            showDashboard()
        } else {
            showLogin()
        }
        
         handleIntent(intent)
         


        // Handle Back Press
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewAbout.visibility == View.VISIBLE) {
                    showDashboard()
                } else if (viewSettings.visibility == View.VISIBLE) {
                    // Check if changes made? For now just go back
                    loadSettings()
                    showDashboard()
                } else if (viewDashboard.visibility == View.VISIBLE) {
                    // Logic for double press to exit or move task to back
                    moveTaskToBack(true)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun bindLoginViews() {
        loginTokenInput = findViewById(R.id.login_token_input)
        btnLogin = findViewById(R.id.btn_login)
        btnTokenGuideLogin = findViewById(R.id.btn_token_guide_login)

        btnLogin.setOnClickListener {
            val token = loginTokenInput.text.toString()
            if (token.isNotBlank()) {
                saveSettings() // Save token
                showDashboard()
            } else {
                Toast.makeText(this, getString(R.string.msg_enter_token), Toast.LENGTH_SHORT).show()
            }
        }

        btnTokenGuideLogin.setOnClickListener { showTokenGuideDialog() }
        
        val btnDiscordLogin = findViewById<Button>(R.id.btn_discord_login)
        btnDiscordLogin.setOnClickListener { showDiscordLogin() }
    }
    
    private fun showDiscordLogin() {
        // Wrapper with explicit params
        val container = FrameLayout(this)
        
        val webView = android.webkit.WebView(this)
        webView.layoutParams = FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, 
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )
        
        // Settings
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile; rv:88.0) Gecko/88.0 Firefox/88.0"
        
        // Interfaces
        webView.addJavascriptInterface(WebAppInterface(), "Android")
        webView.webChromeClient = android.webkit.WebChromeClient() // Crucial for rendering
        
        webView.webViewClient = object : android.webkit.WebViewClient() {
            var sniffingActive = false
            
            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                if (sniffingActive) return
                sniffingActive = true
                
                // Inject JS to Sniff Network Headers (XHR Monkey Patch)
                val js = """
                    (function() {
                        console.log("CustomRPC: Header Sniffer Started");
                        
                        // 1. Intercept XMLHttpRequest
                        var originalOpen = XMLHttpRequest.prototype.open;
                        var originalSetRequestHeader = XMLHttpRequest.prototype.setRequestHeader;
                        
                        XMLHttpRequest.prototype.setRequestHeader = function(key, value) {
                            if (key && key.toLowerCase() === 'authorization') {
                                console.log("Token Found via XHR: " + value);
                                Android.onTokenReceived(value);
                            }
                            originalSetRequestHeader.apply(this, arguments);
                        };
                        
                        // 2. Intercept Fetch API (if used)
                        /*
                        var originalFetch = window.fetch;
                        window.fetch = function() {
                           // Fetch usually takes headers in the second argument (options)
                           if (arguments[1] && arguments[1].headers && arguments[1].headers['Authorization']) {
                               Android.onTokenReceived(arguments[1].headers['Authorization']);
                           }
                           return originalFetch.apply(this, arguments);
                        };
                        */
                        
                        // Fallback: Still check localStorage just in case
                        var t = localStorage.getItem('token');
                        if (t) Android.onTokenReceived(t.replace(/"/g, ''));
                        
                    })();
                """.trimIndent()
                view?.evaluateJavascript(js, null)
                
                Toast.makeText(this@MainActivity, getString(R.string.msg_sniffing), Toast.LENGTH_SHORT).show()
            }
            
            override fun onReceivedError(view: android.webkit.WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                 // Toast.makeText(this@MainActivity, "Web Error: ${error?.description}", Toast.LENGTH_SHORT).show()
            }
        }
        
        container.addView(webView)
        webView.loadUrl("https://discord.com/login")

        // Dialog (Use generic Dialog instead of AlertDialog for full control)
        discordLoginDialog = android.app.Dialog(this, android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen)
        discordLoginDialog?.setContentView(container)
        discordLoginDialog?.setCancelable(true)
        
        discordLoginDialog?.show()
        
        // Force Window Layout
        discordLoginDialog?.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, 
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )
    }




    private fun bindDashboardViews() {
        tvDashboardStatus = findViewById(R.id.tv_dashboard_status)
        tvDashboardDesc = findViewById(R.id.tv_dashboard_desc)
        btnToggleConnection = findViewById(R.id.btn_toggle_connection)
        btnOpenConfig = findViewById(R.id.btn_open_config)
        btnStopService = findViewById(R.id.btn_stop_service)
        btnLogout = findViewById(R.id.btn_logout)
        val btnAbout = findViewById<ImageView>(R.id.btn_about)

        btnToggleConnection.setOnClickListener {
            if (isServiceConnected) {
                // Currently Online -> Disconnect (Stop Service, keep app open)
                sendDisconnectIntent()
            } else {
                 val token = loginTokenInput.text.toString()
                if(token.isNotBlank()) {
                    updateDashboardStatus(false, getString(R.string.status_connecting))
                    val serviceIntent = Intent(this, RpcService::class.java).apply {
                        action = RpcService.ACTION_START
                        putExtra("TOKEN", token)
                        putExtra("APP_NAME", appNameEditText.text.toString().ifBlank { getString(R.string.app_name) })
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }
                 } else {
                     showLogin()
                 }
            }
        }
        
        btnStopService.setOnClickListener {
             sendDisconnectIntent()
        }

        btnOpenConfig.setOnClickListener {
            showSettings()
        }
        
        btnAbout.setOnClickListener {
            showAbout()
        }
        
        btnLogout.setOnClickListener {
            // Clear token and logout
            loginTokenInput.setText("")
            saveSettings()
            sendDisconnectIntent()
            showLogin()
        }
    }
    
    private fun sendDisconnectIntent() {
        val serviceIntent = Intent(this, RpcService::class.java).apply {
            action = RpcService.ACTION_STOP
        }
        stopService(serviceIntent)
        updateDashboardStatus(false, getString(R.string.status_offline)) // Immediate feedback
    }

    private fun bindSettingsViews() {
        appIdEditText = findViewById(R.id.app_id_edit_text)
        appNameEditText = findViewById(R.id.app_name_edit_text)
        activityTypeSpinner = findViewById(R.id.activity_type_spinner)
        userStatusSpinner = findViewById(R.id.user_status_spinner)
        detailsEditText = findViewById(R.id.details_edit_text)
        stateEditText = findViewById(R.id.state_edit_text)
        partySizeEditText = findViewById(R.id.party_size_edit_text)
        partyMaxEditText = findViewById(R.id.party_max_edit_text)
        largeImageKeyEditText = findViewById(R.id.large_image_key_edit_text)
        largeImageTextEditText = findViewById(R.id.large_image_text_edit_text)
        smallImageKeyEditText = findViewById(R.id.small_image_key_edit_text)
        smallImageTextEditText = findViewById(R.id.small_image_text_edit_text)
        
        val layLargeImageKey = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.lay_large_image_key)
        val laySmallImageKey = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.lay_small_image_key)
        
        layLargeImageKey.setEndIconOnClickListener {
             fetchAssets(largeImageKeyEditText)
        }
        
        laySmallImageKey.setEndIconOnClickListener {
             fetchAssets(smallImageKeyEditText)
        }

        btn1Text = findViewById(R.id.btn1_text)
        btn1Url = findViewById(R.id.btn1_url)
        btn2Text = findViewById(R.id.btn2_text)
        btn2Url = findViewById(R.id.btn2_url)
        
        timestampSpinner = findViewById(R.id.timestamp_spinner)
        customTimestampLayout = findViewById(R.id.custom_timestamp_layout)
        btnPickStartTime = findViewById(R.id.btn_pick_start_time)
        tvStartTimeVal = findViewById(R.id.tv_start_time_val)
        btnPickEndTime = findViewById(R.id.btn_pick_end_time)
        tvEndTimeVal = findViewById(R.id.tv_end_time_val)
        
        btnSaveApply = findViewById(R.id.btn_save_apply)
        btnCancelConfig = findViewById(R.id.btn_cancel_config)

        // Spinners Logic
        val types = arrayOf("Playing", "Streaming", "Listening", "Watching", "Custom", "Competing")
        val typeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, types)
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        activityTypeSpinner.adapter = typeAdapter

        // Manual translation for Status Spinner (since logic depends on index/string)
        val statusTypes = arrayOf("Online", "Idle", "Do Not Disturb", "Invisible")
        val statusAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, statusTypes)
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        userStatusSpinner.adapter = statusAdapter

        val tsTypes = arrayOf(getString(R.string.ts_none), "Elapsed Time", "Local Time", "Custom Range")
        val tsAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, tsTypes)
        tsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        timestampSpinner.adapter = tsAdapter

        timestampSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
             override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                 customTimestampLayout.visibility = if (position == 3) View.VISIBLE else View.GONE
             }
             override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        
        btnPickStartTime.setOnClickListener { pickDateTime { ts -> customStartTime = ts; tvStartTimeVal.text = Date(ts).toString() }}
        btnPickEndTime.setOnClickListener { pickDateTime { ts -> customEndTime = ts; tvEndTimeVal.text = Date(ts).toString() }}

        btnSaveApply.setOnClickListener {
            saveSettings()
            if (isServiceConnected) {
                sendPresenceUpdate()
            }
            showDashboard()
        }

        btnCancelConfig.setOnClickListener {
            // Discard changes? Or just go back. For now just go back (reloading settings implies discard)
            loadSettings() 
            showDashboard()
        }
    }

    private fun sendPresenceUpdate() {
        val typeInt = when(activityTypeSpinner.selectedItemPosition) {
            0 -> 0; 1 -> 1; 2 -> 2; 3 -> 3; 4 -> 4; 5 -> 5; else -> 0
        }
        
        var start: Long? = null
        var end: Long? = null
        when(timestampSpinner.selectedItemPosition) {
            1 -> start = System.currentTimeMillis()
            2 -> {
                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                start = cal.timeInMillis
            }
            3 -> { start = customStartTime; end = customEndTime }
        }

        val presenceData = PresenceData(
            appId = appIdEditText.text.toString().trim(),
            name = appNameEditText.text.toString().trim(),
            details = detailsEditText.text.toString().trim(),
            state = stateEditText.text.toString().trim(),

            largeImageKey = (largeImageKeyEditText.tag as? String) ?: largeImageKeyEditText.text.toString().trim(),
            largeImageText = largeImageTextEditText.text.toString().trim(),
            smallImageKey = (smallImageKeyEditText.tag as? String) ?: smallImageKeyEditText.text.toString().trim(),
            smallImageText = smallImageTextEditText.text.toString().trim(),
            activityType = typeInt,
            partySize = partySizeEditText.text.toString().toIntOrNull(),
            partyMax = partyMaxEditText.text.toString().toIntOrNull(),
            button1Label = btn1Text.text.toString().trim(),
            button1Url = btn1Url.text.toString().trim(),
            button2Label = btn2Text.text.toString().trim(),
            button2Url = btn2Url.text.toString().trim(),
            timestampStart = start,
            timestampEnd = end,
            userStatus = when(userStatusSpinner.selectedItemPosition) {
                0 -> "online"; 1 -> "idle"; 2 -> "dnd"; 3 -> "invisible"; else -> "online"
            }
        )

        val serviceIntent = Intent(this, RpcService::class.java).apply {
            action = RpcService.ACTION_UPDATE_PRESENCE
            putExtra("PRESENCE_DATA", presenceData)
        }
        startService(serviceIntent)
        Toast.makeText(this, getString(R.string.msg_rpc_updated), Toast.LENGTH_SHORT).show()
    }
    
    // --- Asset Fetching Logic ---
    private fun fetchAssets(targetInput: EditText) {
        val appId = appIdEditText.text.toString().trim()
        val token = loginTokenInput.text.toString().trim()
        
        if (appId.isEmpty()) {
            Toast.makeText(this, getString(R.string.msg_no_app_id), Toast.LENGTH_SHORT).show()
            return
        }
        
        Toast.makeText(this, getString(R.string.msg_fetching_assets), Toast.LENGTH_SHORT).show()
        
        Thread {
            try {
                // Ensure OkHttp Client is available (it's in dependencies)
                val client = okhttp3.OkHttpClient()
                val request = okhttp3.Request.Builder()
                    .url("https://discord.com/api/v9/oauth2/applications/$appId/assets")
                    .addHeader("Authorization", token) // User Token is sufficient for own apps
                    .build()
                    
                val response = client.newCall(request).execute()
                val json = response.body?.string()
                
                if (response.isSuccessful && json != null) {
                    val assets = org.json.JSONArray(json)
                    val names = ArrayList<String>()
                    
                    val assetMap = mutableMapOf<String, String>()
                    assetMap["(None)"] = "" // Allow clearing the selection
                    
                    for (i in 0 until assets.length()) {
                        val obj = assets.getJSONObject(i)
                        val name = obj.getString("name")
                        val id = obj.getString("id")
                        assetMap[name] = id
                    }
                    
                    runOnUiThread {
                        if (assetMap.isEmpty()) {
                            Toast.makeText(this, getString(R.string.msg_no_assets_found), Toast.LENGTH_LONG).show()
                        } else {
                            showAssetSelector(assetMap, targetInput)
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this, "Failed: ${response.code} (Check App ID / Token)", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
    
    private fun showAssetSelector(assetMap: Map<String, String>, targetInput: EditText) {
        val names = assetMap.keys.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.title_select_image))
            .setItems(names) { _, which ->
                val selectedName = names[which]
                val selectedId = assetMap[selectedName]
                
                targetInput.setText(selectedName) // Show Name (Friendly)
                targetInput.setTag(selectedId)    // Store ID (Hidden)
            }
            .show()
    }
    // ----------------------------

    private fun showLogin() {
        viewLogin.visibility = View.VISIBLE
        viewDashboard.visibility = View.GONE
        viewSettings.visibility = View.GONE
        viewAbout.visibility = View.GONE
    }

    private fun showDashboard() {
        viewLogin.visibility = View.GONE
        viewDashboard.visibility = View.VISIBLE
        viewSettings.visibility = View.GONE
        viewAbout.visibility = View.GONE
    }

    private fun showSettings() {
        viewLogin.visibility = View.GONE
        viewDashboard.visibility = View.GONE
        viewSettings.visibility = View.VISIBLE
        viewAbout.visibility = View.GONE
    }

    private fun showAbout() {
        viewLogin.visibility = View.GONE
        viewDashboard.visibility = View.GONE
        viewSettings.visibility = View.GONE
        viewAbout.visibility = View.VISIBLE
        
        val btnBackAbout = findViewById<Button>(R.id.btn_back_about)
        val btnGithub = findViewById<Button>(R.id.btn_github)
        val btnEmail = findViewById<Button>(R.id.btn_email)
        val btnDonate = findViewById<Button>(R.id.btn_donate)

        btnBackAbout.setOnClickListener {
            showDashboard()
        }
        
        btnGithub.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/khoirulaksara"))
            startActivity(intent)
        }
        
        btnEmail.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:me@serat.us")
                putExtra(Intent.EXTRA_SUBJECT, "CustomRPC Feedback")
            }
            startActivity(intent)
        }
        
        btnDonate.setOnClickListener {
             val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://paypal.me/gonzsky")) 
             startActivity(intent)
        }
    }
    
    // Broadcast Receiver
     override fun onResume() {
        super.onResume()
        val filter = IntentFilter(RpcService.ACTION_STATUS_UPDATE)
        ContextCompat.registerReceiver(this, statusReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        
        // Probe Status NOW (Receiver is ready)
        val probeIntent = Intent(this, RpcService::class.java).apply {
            action = RpcService.ACTION_PROBE
        }
        startService(probeIntent)
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == RpcService.ACTION_STATUS_UPDATE) {
                val isConnected = intent.getBooleanExtra("IS_CONNECTED", false)
                val message = intent.getStringExtra("MESSAGE") ?: "Unknown"
                updateDashboardStatus(isConnected, message)
            }
        }
    }
    
    private fun updateDashboardStatus(isConnected: Boolean, message: String) {
        val particleView = findViewById<ParticleRingView>(R.id.particle_view)
        isServiceConnected = isConnected
        
        if (isConnected) {
            tvDashboardStatus.text = getString(R.string.status_online)
            tvDashboardStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
            tvDashboardDesc.text = message
            btnToggleConnection.text = getString(R.string.btn_stop)
            btnToggleConnection.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_red_dark)
            
            // ONLINE: Green Solid
            particleView.setStatus(2)
        } else {
             // Handle "Connecting..." intermediate state
             val isConnecting = message.contains("Connecting", true)
             if (isConnecting) {
                tvDashboardStatus.text = getString(R.string.status_connecting)
                tvDashboardStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))
                
                // CONNECTING: Yellow Particles
                particleView.setStatus(1)
             } else {
                tvDashboardStatus.text = getString(R.string.status_offline)
                tvDashboardStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                btnToggleConnection.text = getString(R.string.btn_start)
                btnToggleConnection.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#5865F2")) 
                
                // OFFLINE: Red Particles
                particleView.setStatus(0)
             }
             tvDashboardDesc.text = message
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(statusReceiver)
    }

    // Persistence
    private fun saveSettings() {
        val sharedPref = getSharedPreferences("RpcSettings", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("token", loginTokenInput.text.toString())
            putString("appId", appIdEditText.text.toString())
            putString("appName", appNameEditText.text.toString())
            putInt("activityType", activityTypeSpinner.selectedItemPosition)
            putString("userStatus", when(userStatusSpinner.selectedItemPosition) {
                0 -> "online"; 1 -> "idle"; 2 -> "dnd"; 3 -> "invisible"; else -> "online"
            })
            putString("details", detailsEditText.text.toString())
            putString("state", stateEditText.text.toString())
            putString("partySize", partySizeEditText.text.toString())
            putString("partyMax", partyMaxEditText.text.toString())
            putString("largeImageKey", (largeImageKeyEditText.tag as? String) ?: largeImageKeyEditText.text.toString())
            putString("largeImageName", largeImageKeyEditText.text.toString()) // Save what user sees
            putString("largeImageText", largeImageTextEditText.text.toString())
            putString("smallImageKey", (smallImageKeyEditText.tag as? String) ?: smallImageKeyEditText.text.toString())
            putString("smallImageName", smallImageKeyEditText.text.toString()) // Save what user sees
            putString("smallImageText", smallImageTextEditText.text.toString())
            putString("btn1Text", btn1Text.text.toString())
            putString("btn1Url", btn1Url.text.toString())
            putString("btn2Text", btn2Text.text.toString())
            putString("btn2Url", btn2Url.text.toString())
            putInt("timestampMode", timestampSpinner.selectedItemPosition)
            apply()
        }
    }

    private fun loadSettings() {
        val sharedPref = getSharedPreferences("RpcSettings", Context.MODE_PRIVATE)
        loginTokenInput.setText(sharedPref.getString("token", ""))
        
        val savedAppId = sharedPref.getString("appId", "")
        appIdEditText.setText(savedAppId)
        appNameEditText.setText(sharedPref.getString("appName", ""))

        activityTypeSpinner.setSelection(sharedPref.getInt("activityType", 0))

        val statusIdx = when(sharedPref.getString("userStatus", "online")) {
            "idle" -> 1
            "dnd" -> 2
            "invisible" -> 3
            else -> 0
        }
        userStatusSpinner.setSelection(statusIdx)

        detailsEditText.setText(sharedPref.getString("details", ""))
        stateEditText.setText(sharedPref.getString("state", ""))
        partySizeEditText.setText(sharedPref.getString("partySize", ""))
        partyMaxEditText.setText(sharedPref.getString("partyMax", ""))
        largeImageKeyEditText.setText(sharedPref.getString("largeImageName", "")) // Restore Name
        largeImageKeyEditText.setTag(sharedPref.getString("largeImageKey", ""))   // Restore ID
        largeImageTextEditText.setText(sharedPref.getString("largeImageText", ""))
        smallImageKeyEditText.setText(sharedPref.getString("smallImageName", "")) // Restore Name
        smallImageKeyEditText.setTag(sharedPref.getString("smallImageKey", ""))   // Restore ID
        smallImageTextEditText.setText(sharedPref.getString("smallImageText", ""))
        btn1Text.setText(sharedPref.getString("btn1Text", ""))
        btn1Url.setText(sharedPref.getString("btn1Url", ""))
        btn2Text.setText(sharedPref.getString("btn2Text", ""))
        btn2Url.setText(sharedPref.getString("btn2Url", ""))
        timestampSpinner.setSelection(sharedPref.getInt("timestampMode", 2))
    }

    private fun pickDateTime(onPicked: (Long) -> Unit) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, day ->
            TimePickerDialog(this, { _, hour, minute ->
                calendar.set(year, month, day, hour, minute)
                onPicked(calendar.timeInMillis)
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showTokenGuideDialog() {
       val message = getString(R.string.dialog_guide_msg)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_guide_title))
            .setMessage(message)
            .setPositiveButton(getString(R.string.btn_open_login)) { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.com/login")))
            }
            .setNegativeButton(getString(R.string.btn_close), null)
            .show()
    }
    
     private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val uri = intent.data
            if (uri != null && uri.scheme == "customrpc" && uri.host == "token") {
                val token = uri.getQueryParameter("value")
                if (!token.isNullOrEmpty()) {
                    loginTokenInput.setText(token)
                    saveSettings()
                    showDashboard()
                }
            }
        }
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }
}