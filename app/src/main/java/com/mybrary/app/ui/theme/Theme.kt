package com.mybrary.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val primaryBlue = Color(0xFF1A237E)
private val secondaryAmber = Color(0xFFFFB300)
private val surfaceCream = Color(0xFFFFF8E1)

private val LightColorScheme = lightColorScheme(
    primary = primaryBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3F51B5),
    onPrimaryContainer = Color.White,
    secondary = secondaryAmber,
    onSecondary = Color(0xFF1A1A1A),
    secondaryContainer = Color(0xFFFFECB3),
    onSecondaryContainer = Color(0xFF1A1A1A),
    surface = surfaceCream,
    onSurface = Color(0xFF1A1A1A),
    background = Color(0xFFFAF8F0),
    onBackground = Color(0xFF1A1A1A),
    error = Color(0xFFC62828),
)

@Composable
fun MybraryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography(),
        content = content,
    )
}
