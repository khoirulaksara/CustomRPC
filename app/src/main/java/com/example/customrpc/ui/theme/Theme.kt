package com.example.customrpc.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val DiscordDarkBackground = Color(0xFF313338)
val DiscordDarkPrimary = Color(0xFF5865F2)
val DiscordDarkSecondary = Color(0xFF2B2D31)
val DiscordDarkText = Color(0xFFF2F3F5)
val DiscordDarkTextMuted = Color(0xFFB5BAC1)
val DiscordGreen = Color(0xFF23A559)
val DiscordYellow = Color(0xFFF0B232)
val DiscordRed = Color(0xFFDA373C)

private val DarkColorScheme = darkColorScheme(
    primary = DiscordDarkPrimary,
    secondary = DiscordDarkSecondary,
    tertiary = DiscordDarkTextMuted,
    background = DiscordDarkBackground,
    surface = DiscordDarkSecondary,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = DiscordDarkText,
    onSurface = DiscordDarkText,
)

@Composable
fun CustomRPCTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
