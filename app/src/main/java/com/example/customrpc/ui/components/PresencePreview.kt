package com.example.customrpc.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.customrpc.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import com.example.customrpc.R
import androidx.compose.ui.res.painterResource

@Composable
fun AsyncDiscordImage(url: String, modifier: Modifier) {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    
    LaunchedEffect(url) {
        if (url.isBlank()) {
            bitmap = null
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bytes = response.body?.bytes()
                        if (bytes != null) {
                            val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (bmp != null) {
                                bitmap = bmp.asImageBitmap()
                            }
                        }
                    } else {
                        bitmap = null
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AsyncDiscordImage", "Load failed: ${e.message}")
                bitmap = null
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(modifier = modifier.background(Color.DarkGray.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
            if (url.isNotBlank()) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 1.dp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun PresencePreview(
    appId: String,
    appName: String,
    type: Int,
    details: String,
    state: String,
    largeImageKey: String,
    smallImageKey: String,
    largeImageText: String = "",
    smallImageText: String = "",
    timestampMode: Int = 0,
    startTime: Long? = null,
    endTime: Long? = null
) {
    val activityTypes = listOf("Playing", "Streaming", "Listening to", "Watching", "Custom Status", "Competing in")
    val typeText = if (type in 0 until activityTypes.size) activityTypes[type] else "Playing"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1F22)), // Discord Card Dark
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = typeText.uppercase(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = Color.White.copy(alpha = 0.7f)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Large Image
                Box(contentAlignment = Alignment.BottomEnd) {
                    val fallbackUrl = "https://cdn.discordapp.com/app-icons/1450458624806752470/bdd3cad40808a39d3e614fcbfceebf00.png"
                    
                    val isLargeNone = largeImageKey.isBlank() || largeImageKey.equals("(none)", ignoreCase = true)
                    val largeUrl = if (largeImageKey.startsWith("http")) largeImageKey 
                                  else if (!isLargeNone) "https://cdn.discordapp.com/app-assets/$appId/$largeImageKey.png"
                                  else fallbackUrl
                    
                    AsyncDiscordImage(
                        url = largeUrl,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.2f))
                    )
                    
                    val isSmallNone = smallImageKey.isBlank() || smallImageKey.equals("(none)", ignoreCase = true)
                    if (!isSmallNone) {
                        val smallUrl = if (smallImageKey.startsWith("http")) smallImageKey 
                                      else "https://cdn.discordapp.com/app-assets/$appId/$smallImageKey.png"
                        
                        AsyncDiscordImage(
                            url = smallUrl,
                            modifier = Modifier
                                .size(24.dp)
                                .offset(x = 4.dp, y = 4.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E1F22)) // Match card background
                                .padding(2.dp)
                                .clip(CircleShape)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column {
                    Text(
                        text = appName.ifBlank { "Custom App" },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    if (details.isNotBlank() && type != 4) {
                        Text(
                            text = details,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp
                        )
                    }
                    if (state.isNotBlank()) {
                        Text(
                            text = state,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp
                        )
                    }
                    
                    // Timestamps Preview
                    if (type != 4) {
                        val timeText = when(timestampMode) {
                            1 -> "00:00 elapsed"
                            2 -> {
                                val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                                "${sdf.format(java.util.Date())}"
                            }
                            3 -> {
                                if (startTime != null && endTime != null) {
                                    val diff = endTime - startTime
                                    val hours = diff / (1000 * 60 * 60)
                                    val mins = (diff / (1000 * 60)) % 60
                                    String.format("%02d:%02d left", hours, mins)
                                } else if (startTime != null) {
                                    "00:00 elapsed"
                                } else ""
                            }
                            else -> ""
                        }
                        if (timeText.isNotEmpty()) {
                            Text(
                                text = timeText,
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
