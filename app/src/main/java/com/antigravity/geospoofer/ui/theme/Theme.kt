package com.antigravity.geospoofer.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = TechBlue,
    onPrimary = TextPrimary,
    primaryContainer = TechBlue.copy(alpha = 0.2f),
    onPrimaryContainer = TechBlueLight,
    secondary = TechCyan,
    onSecondary = TextPrimary,
    secondaryContainer = TechCyan.copy(alpha = 0.2f),
    onSecondaryContainer = TechCyanLight,
    tertiary = TechEmerald,
    onTertiary = TextPrimary,
    background = DarkBg,
    onBackground = TextPrimary,
    surface = DarkCard,
    onSurface = TextPrimary,
    surfaceVariant = DarkCardSubtle,
    onSurfaceVariant = TextSecondary,
    outline = DarkCardBorder,
    outlineVariant = DarkDivider,
    error = TechRose,
    onError = TextPrimary,
    errorContainer = TechRoseDark,
    onErrorContainer = TechRose
)

private val LightColorScheme = DarkColorScheme // Always maintain dark tech cockpit aesthetic

@Composable
fun LocationMockProTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep precision tech palette consistent
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            dynamicDarkColorScheme(context)
        }
        else -> DarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
