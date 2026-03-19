package com.example.easychat.ui.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary         = Color(0xFF1B73E8),
    onPrimary       = Color.White,
    primaryContainer= Color(0xFFD3E4FD),
    secondary       = Color(0xFF4CAF50),
    tertiary        = Color(0xFF1B73E8),       // cor do ✓✓ lido
    background      = Color(0xFFFAFAFA),
    surface         = Color.White,
    error           = Color(0xFFD32F2F)
)

private val DarkColors = darkColorScheme(
    primary         = Color(0xFF90CAF9),
    onPrimary       = Color(0xFF003258),
    primaryContainer= Color(0xFF1B4A72),
    secondary       = Color(0xFF81C784),
    tertiary        = Color(0xFF90CAF9),
    background      = Color(0xFF121212),
    surface         = Color(0xFF1E1E1E),
    error           = Color(0xFFEF9A9A)
)

@Composable
fun EasyChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content     = content
    )
}