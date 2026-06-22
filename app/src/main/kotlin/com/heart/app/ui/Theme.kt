package com.heart.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val HeartRed = Color(0xFFE53935)
private val HeartRedDark = Color(0xFFB71C1C)

private val LightColors = lightColorScheme(
    primary = HeartRed,
    onPrimary = Color.White,
    secondary = HeartRedDark,
    background = Color(0xFFFFFBFB),
    surface = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = HeartRed,
    onPrimary = Color.White,
    secondary = Color(0xFFEF9A9A),
    background = Color(0xFF161212),
    surface = Color(0xFF211C1C),
)

@Composable
fun HeartTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}

object Lights {
    val Good = Color(0xFF2E7D32)
    val Moderate = Color(0xFFF9A825)
    val Elevated = Color(0xFFD84315)
    val Unknown = Color(0xFF9E9E9E)
}
