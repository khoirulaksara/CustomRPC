package com.example.customrpc.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.example.customrpc.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.customrpc.ui.theme.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = com.example.customrpc.R.drawable.ic_app_logo_new),
            contentDescription = "App Logo",
            modifier = Modifier.size(100.dp).clip(RoundedCornerShape(24.dp))
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.title_login), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = DiscordDarkPrimary)
        Text(stringResource(R.string.about_version, "2.0 (Archangel)"), color = DiscordDarkTextMuted)
        Spacer(Modifier.height(32.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DiscordDarkSecondary)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.about_dev, "khoirulaksara"), fontWeight = FontWeight.Bold, color = Color.White)
                Text(stringResource(R.string.label_tagline1), fontSize = 13.sp, color = DiscordDarkTextMuted)
                Text(stringResource(R.string.label_tagline2), fontSize = 13.sp, color = DiscordDarkTextMuted)
                
                Spacer(Modifier.height(16.dp))
                
                Button(
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/khoirulaksara/CustomRPC"))) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = DiscordDarkPrimary)
                ) {
                    Text(stringResource(R.string.btn_visit_repo))
                }
                
                Spacer(Modifier.height(8.dp))
                
                OutlinedButton(
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://paypal.me/gonzsky"))) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.Red
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_support_dev), color = Color.White)
                }
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        OutlinedButton(onClick = onBack) {
            Text(stringResource(R.string.btn_back_dashboard))
        }
    }
}
