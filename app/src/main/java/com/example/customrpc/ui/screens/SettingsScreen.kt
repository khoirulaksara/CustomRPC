package com.example.customrpc.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.customrpc.PresenceData
import com.example.customrpc.RpcService
import com.example.customrpc.ui.theme.*
import com.example.customrpc.ui.components.AsyncDiscordImage
import com.example.customrpc.ui.components.PresencePreview
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date

// AsyncDiscordImage has been moved to com.example.customrpc.ui.components

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(sharedPref: android.content.SharedPreferences, onBack: () -> Unit, isConnected: Boolean) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var appId by remember { mutableStateOf(sharedPref.getString("appId", "") ?: "") }
    var appName by remember { mutableStateOf(sharedPref.getString("appName", "Custom RPC") ?: "Custom RPC") }
    
    // Status Logic
    var statusItems = listOf("Online", "Idle", "Do Not Disturb", "Invisible")
    var statusExpanded by remember { mutableStateOf(false) }
    var statusSelection by remember { mutableIntStateOf(try { sharedPref.getInt("userStatus", 0) } catch(e: Exception) { 0 }) }

    // Activity Type
    var activityItems = listOf("Playing", "Streaming", "Listening", "Watching", "Custom", "Competing")
    var typeExpanded by remember { mutableStateOf(false) }
    var typeSelection by remember { mutableIntStateOf(sharedPref.getInt("activityType", 0)) }

    var details by remember { mutableStateOf(sharedPref.getString("details", "") ?: "") }
    var state by remember { mutableStateOf(sharedPref.getString("state", "") ?: "") }
    var partySize by remember { mutableStateOf(sharedPref.getString("partySize", "") ?: "") }
    var partyMax by remember { mutableStateOf(sharedPref.getString("partyMax", "") ?: "") }

    var largeImageKey by remember { mutableStateOf(sharedPref.getString("largeImageKey", "") ?: "") }
    var largeImageName by remember { mutableStateOf(sharedPref.getString("largeImageName", "") ?: "") }
    var largeImageText by remember { mutableStateOf(sharedPref.getString("largeImageText", "") ?: "") }
    
    var smallImageKey by remember { mutableStateOf(sharedPref.getString("smallImageKey", "") ?: "") }
    var smallImageName by remember { mutableStateOf(sharedPref.getString("smallImageName", "") ?: "") }
    var smallImageText by remember { mutableStateOf(sharedPref.getString("smallImageText", "") ?: "") }

    var btn1Text by remember { mutableStateOf(sharedPref.getString("btn1Text", "") ?: "") }
    var btn1Url by remember { mutableStateOf(sharedPref.getString("btn1Url", "") ?: "") }
    var btn2Text by remember { mutableStateOf(sharedPref.getString("btn2Text", "") ?: "") }
    var btn2Url by remember { mutableStateOf(sharedPref.getString("btn2Url", "") ?: "") }
    var streamingUrl by remember { mutableStateOf(sharedPref.getString("streamingUrl", "") ?: "") }

    var tsItems = listOf("None", "Elapsed Time", "Local Time", "Custom Range")
    var tsExpanded by remember { mutableStateOf(false) }
    var tsSelection by remember { mutableIntStateOf(sharedPref.getInt("timestampMode", 2)) }

    var customStartTime by remember { mutableStateOf(sharedPref.getLong("customStartTime", 0L).takeIf { it != 0L }) }
    var customEndTime by remember { mutableStateOf(sharedPref.getLong("customEndTime", 0L).takeIf { it != 0L }) }

    var isLoadingAssets by remember { mutableStateOf(false) }
    var assetMap by remember { mutableStateOf<Map<String, String>?>(null) }
    var showAssetSelectorFor by remember { mutableStateOf<String?>(null) }

    // Recent App IDs logic
    var recentAppIds by remember { 
        mutableStateOf(sharedPref.getStringSet("recentAppIds", emptySet())?.toList() ?: emptyList())
    }

    fun fetchAssets(type: String) {
        val appidCurrent = appId.trim()
        val tokenCurrent = sharedPref.getString("token", "")?.trim() ?: ""
        if (appidCurrent.isBlank() || tokenCurrent.isBlank()) {
            Toast.makeText(context, context.getString(R.string.msg_app_id_token_missing), Toast.LENGTH_SHORT).show()
            return
        }
        if (assetMap != null) {
            showAssetSelectorFor = type
            return
        }
        isLoadingAssets = true
        val client = okhttp3.OkHttpClient()
        val request = okhttp3.Request.Builder()
            .url("https://discord.com/api/v9/oauth2/applications/$appidCurrent/assets")
            .addHeader("Authorization", tokenCurrent)
            .build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                (context as? android.app.Activity)?.runOnUiThread {
                    isLoadingAssets = false
                    Toast.makeText(context, context.getString(R.string.msg_network_error), Toast.LENGTH_SHORT).show()
                }
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val body = response.body?.string() ?: ""
                (context as? android.app.Activity)?.runOnUiThread {
                    isLoadingAssets = false
                    if (response.isSuccessful && body.isNotEmpty()) {
                        try {
                            val array = org.json.JSONArray(body)
                            val map = mutableMapOf<String, String>()
                            map["(None)"] = ""
                            for (i in 0 until array.length()) {
                                val obj = array.getJSONObject(i)
                                val name = obj.optString("name")
                                val id = obj.optString("id")
                                if (name.isNotEmpty() && id.isNotEmpty()) map[name] = id
                            }
                            assetMap = map
                            showAssetSelectorFor = type
                        } catch (e: Exception) {
                            Toast.makeText(context, context.getString(R.string.msg_no_assets), Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, context.getString(R.string.msg_failed_api), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_configuration)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DiscordDarkSecondary, titleContentColor = Color.White)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            BottomAppBar(containerColor = DiscordDarkSecondary) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.btn_cancel), color = DiscordDarkTextMuted) }
                    Button(
                        onClick = {
                            with(sharedPref.edit()) {
                                putString("appId", appId)
                                putString("appName", appName)
                                putInt("userStatus", statusSelection)
                                putInt("activityType", typeSelection)
                                putString("details", details)
                                putString("state", state)
                                putString("partySize", partySize)
                                putString("partyMax", partyMax)
                                putString("largeImageKey", largeImageKey)
                                putString("largeImageName", largeImageName)
                                putString("largeImageText", largeImageText)
                                putString("smallImageKey", smallImageKey)
                                putString("smallImageName", smallImageName)
                                putString("smallImageText", smallImageText)
                                putString("btn1Text", btn1Text)
                                putString("btn1Url", btn1Url)
                                putString("btn2Text", btn2Text)
                                putString("btn2Url", btn2Url)
                                putLong("customStartTime", customStartTime ?: 0L)
                                putLong("customEndTime", customEndTime ?: 0L)
                                putInt("timestampMode", tsSelection)
                                putString("streamingUrl", streamingUrl)
                                
                                // Update Recent App IDs
                                if (appId.isNotBlank()) {
                                    val updatedSet = recentAppIds.toMutableSet().apply { add(appId.trim()) }
                                    putStringSet("recentAppIds", updatedSet)
                                }
                                
                                apply()
                            }
                            
                            if (isConnected) {
                                // Notify service to update presence dynamically without restarting
                                val statusStr = when(statusSelection) { 0->"online"; 1->"idle"; 2->"dnd"; 3->"invisible"; else->"online" }
                                var start: Long? = null
                                var end: Long? = null
                                when(tsSelection) {
                                    1 -> start = System.currentTimeMillis()
                                    2 -> {
                                        val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
                                        start = cal.timeInMillis
                                    }
                                    3 -> { start = customStartTime; end = customEndTime }
                                }
                                val pd = PresenceData(
                                    appId = appId, name = appName, details = details, state = state,
                                    largeImageKey = largeImageKey, largeImageText = largeImageText,
                                    smallImageKey = smallImageKey, smallImageText = smallImageText,
                                    activityType = typeSelection, partySize = partySize.toIntOrNull(), partyMax = partyMax.toIntOrNull(),
                                    button1Label = btn1Text, button1Url = btn1Url, button2Label = btn2Text, button2Url = btn2Url,
                                    timestampStart = start, timestampEnd = end, userStatus = statusStr,
                                    streamingUrl = streamingUrl
                                )
                                val intent = Intent(context, RpcService::class.java).apply {
                                    action = RpcService.ACTION_UPDATE_PRESENCE
                                    putExtra("PRESENCE_DATA", pd)
                                }
                                context.startService(intent)
                                Toast.makeText(context, context.getString(R.string.msg_rpc_updated), Toast.LENGTH_SHORT).show()
                            }
                            onBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DiscordDarkPrimary)
                    ) { Text(stringResource(R.string.btn_save_apply)) }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(scrollState).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Recent App IDs
            if (recentAppIds.isNotEmpty()) {
                Column {
                    Text(stringResource(R.string.label_recent_app_ids), fontSize = 12.sp, color = DiscordDarkTextMuted)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        recentAppIds.takeLast(3).reversed().forEach { id ->
                            SuggestionChip(
                                onClick = { appId = id },
                                label = { Text(id.take(8) + "...") },
                                colors = SuggestionChipDefaults.suggestionChipColors(containerColor = DiscordDarkSecondary, labelColor = Color.White)
                            )
                        }
                    }
                }
            }

            OutlinedTextField(value = appId, onValueChange = { appId = it }, label = { Text(stringResource(R.string.hint_app_id)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = appName, onValueChange = { appName = it }, label = { Text(stringResource(R.string.hint_app_name)) }, modifier = Modifier.fillMaxWidth())
            
            // Live Preview
            Text(stringResource(R.string.label_live_preview), fontWeight = FontWeight.Bold, color = DiscordDarkPrimary)
            PresencePreview(
                appId = appId.trim(),
                appName = appName,
                type = typeSelection,
                details = details,
                state = state,
                largeImageKey = largeImageKey,
                smallImageKey = smallImageKey,
                timestampMode = tsSelection,
                startTime = customStartTime,
                endTime = customEndTime
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            // Appearance Status Dropdown (Disabled as it overrides manual Discord status)
            ExposedDropdownMenuBox(expanded = false, onExpandedChange = { }) {
                OutlinedTextField(
                    enabled = false,
                    readOnly = true, value = statusItems[statusSelection] + " (Not Supported)", onValueChange = {}, label = { Text("Appearance Status") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = false) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable, true).fillMaxWidth()
                )
            }

            ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = !typeExpanded }) {
                OutlinedTextField(
                    readOnly = true, value = activityItems[typeSelection], onValueChange = {}, label = { Text("Activity Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable, true).fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                    activityItems.forEachIndexed { index, selectionOption ->
                        DropdownMenuItem(text = { Text(selectionOption) }, onClick = { typeSelection = index; typeExpanded = false })
                    }
                }
            }

            if (typeSelection != 4) {
                OutlinedTextField(value = details, onValueChange = { details = it }, label = { Text(if (typeSelection == 2) "Song Name" else "Details (Top Line)") }, modifier = Modifier.fillMaxWidth())
            }
            
            OutlinedTextField(value = state, onValueChange = { state = it }, label = { Text(if (typeSelection == 2) "Artist Name" else if (typeSelection == 4) "Custom Status Text" else "State (Bottom Line)") }, modifier = Modifier.fillMaxWidth())
            
            if (typeSelection == 1) {
                OutlinedTextField(value = streamingUrl, onValueChange = { streamingUrl = it }, label = { Text("Streaming URL (Twitch/YouTube)") }, modifier = Modifier.fillMaxWidth())
            }

            if (typeSelection == 0 || typeSelection == 5) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = partySize, onValueChange = { partySize = it }, label = { Text("Party Size") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = partyMax, onValueChange = { partyMax = it }, label = { Text("Party Max") }, modifier = Modifier.weight(1f))
                }
            }

            // Timestamps
            ExposedDropdownMenuBox(expanded = tsExpanded, onExpandedChange = { tsExpanded = !tsExpanded }) {
                OutlinedTextField(
                    readOnly = true, value = tsItems[tsSelection], onValueChange = {}, label = { Text(stringResource(R.string.label_timestamps)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tsExpanded) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable, true).fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = tsExpanded, onDismissRequest = { tsExpanded = false }) {
                    tsItems.forEachIndexed { index, selectionOption ->
                        DropdownMenuItem(text = { Text(selectionOption) }, onClick = { tsSelection = index; tsExpanded = false })
                    }
                }
            }
            if (tsSelection == 3) {
                Card(colors = CardDefaults.cardColors(containerColor = DiscordDarkSecondary)) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        Button(onClick = {
                            val c = Calendar.getInstance()
                            DatePickerDialog(context, { _, y, m, d -> TimePickerDialog(context, { _, h, min ->
                                val nc = Calendar.getInstance().apply { set(y,m,d,h,min) }
                                customStartTime = nc.timeInMillis
                            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show() }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                        }) { Text("Pick Start Time") }
                        if (customStartTime != null) Text(Date(customStartTime!!).toString(), color = Color.White)
                        
                        Spacer(modifier = Modifier.height(8.dp))

                        Button(onClick = {
                            val c = Calendar.getInstance()
                            DatePickerDialog(context, { _, y, m, d -> TimePickerDialog(context, { _, h, min ->
                                val nc = Calendar.getInstance().apply { set(y,m,d,h,min) }
                                customEndTime = nc.timeInMillis
                            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show() }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                        }) { Text("Pick End Time") }
                        if (customEndTime != null) Text(Date(customEndTime!!).toString(), color = Color.White)
                    }
                }
            }

            // Assets Section
            HorizontalDivider(color = Color.DarkGray)
            Text("Assets", fontWeight = FontWeight.Bold, color = DiscordDarkPrimary)
            OutlinedTextField(
                value = largeImageName.ifBlank { largeImageKey }, 
                onValueChange = { largeImageKey = it; largeImageName = it }, 
                label = { Text("Large Image Key/URL") }, 
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    val id = largeImageKey.trim()
                    if (id.isNotEmpty()) {
                        val url = if (id.startsWith("http")) id else "https://cdn.discordapp.com/app-assets/${appId.trim()}/$id.png"
                        AsyncDiscordImage(
                            url = url,
                            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(4.dp))
                        )
                    }
                },
                trailingIcon = {
                    IconButton(onClick = { fetchAssets("large") }) {
                        if (isLoadingAssets && showAssetSelectorFor == null) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = DiscordDarkPrimary)
                        else Icon(Icons.Default.Search, contentDescription = "Fetch")
                    }
                }
            )
            OutlinedTextField(value = largeImageText, onValueChange = { largeImageText = it }, label = { Text("Large Image Tooltip") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = smallImageName.ifBlank { smallImageKey }, 
                onValueChange = { smallImageKey = it; smallImageName = it }, 
                label = { Text("Small Image Key/URL") }, 
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    val id = smallImageKey.trim()
                    if (id.isNotEmpty()) {
                        val url = if (id.startsWith("http")) id else "https://cdn.discordapp.com/app-assets/${appId.trim()}/$id.png"
                        AsyncDiscordImage(
                            url = url,
                            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(4.dp))
                        )
                    }
                },
                trailingIcon = {
                    IconButton(onClick = { fetchAssets("small") }) {
                        if (isLoadingAssets && showAssetSelectorFor == null) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = DiscordDarkPrimary)
                        else Icon(Icons.Default.Search, contentDescription = "Fetch")
                    }
                }
            )
            OutlinedTextField(value = smallImageText, onValueChange = { smallImageText = it }, label = { Text("Small Image Tooltip") }, modifier = Modifier.fillMaxWidth())

            if (typeSelection == 0 || typeSelection == 5) {
                // Buttons Section
                HorizontalDivider(color = Color.DarkGray)
                Text("Buttons", fontWeight = FontWeight.Bold, color = DiscordDarkPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = btn1Text, onValueChange = { btn1Text = it }, label = { Text("Button 1 Label") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = btn1Url, onValueChange = { btn1Url = it }, label = { Text("Button 1 URL") }, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = btn2Text, onValueChange = { btn2Text = it }, label = { Text("Button 2 Label") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = btn2Url, onValueChange = { btn2Url = it }, label = { Text("Button 2 URL") }, modifier = Modifier.weight(1f))
                }
            }
        }
    }

    if (showAssetSelectorFor != null && assetMap != null) {
        AlertDialog(
            onDismissRequest = { showAssetSelectorFor = null },
            title = { Text(stringResource(R.string.title_select_asset), color = Color.White) },
            containerColor = DiscordDarkSecondary,
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    assetMap!!.forEach { (name, id) ->
                        Surface(
                            onClick = {
                                if (showAssetSelectorFor == "large") {
                                    largeImageName = name
                                    largeImageKey = id
                                } else {
                                    smallImageName = name
                                    smallImageKey = id
                                }
                                showAssetSelectorFor = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (id.isNotEmpty()) {
                                    AsyncDiscordImage(
                                        url = "https://cdn.discordapp.com/app-assets/${appId.trim()}/$id.png",
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.Black.copy(alpha = 0.2f))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.DarkGray),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("None", fontSize = 10.sp, color = Color.Gray)
                                    }
                                }
                                
                                Spacer(modifier = Modifier.width(12.dp))
                                
                                Text(
                                    text = name,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}
