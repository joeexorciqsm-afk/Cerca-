package com.cerca.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Esquema claro (predeterminado) con colores fijos y buen contraste.
private val LightColors = lightColorScheme(
    primary = CercaPrimary,
    onPrimary = Color.White,
    secondary = CercaSecondary,
    onSecondary = Color.White,
    background = CercaBackground,
    onBackground = CercaOnLight,
    surface = CercaSurface,
    onSurface = CercaOnLight,
    surfaceVariant = Color(0xFFE3EBEA),
    onSurfaceVariant = Color(0xFF3F4B4A)
)

// Esquema oscuro coherente.
private val DarkColors = darkColorScheme(
    primary = CercaPrimaryDark,
    onPrimary = Color(0xFF00201E),
    secondary = CercaSecondaryDark,
    onSecondary = Color(0xFF1B2A32),
    background = Color(0xFF101514),
    onBackground = Color(0xFFE6EBEA),
    surface = Color(0xFF1A2120),
    onSurface = Color(0xFFE6EBEA),
    surfaceVariant = Color(0xFF2A3534),
    onSurfaceVariant = Color(0xFFC3CFCE)
)

@Composable
fun CercaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Usamos colores fijos (sin dynamicColor) para que la app se vea siempre
    // igual y con buen contraste, mejorando la accesibilidad.
    val colorScheme = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
