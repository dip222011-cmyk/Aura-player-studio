package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val AuraDarkColorScheme = darkColorScheme(
    primary = LuminousCyan,
    onPrimary = Color(0xFF001F2E),
    primaryContainer = Color(0xFF0C4A6E),
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = ElectricViolet,
    onSecondary = Color(0xFF2E084F),
    secondaryContainer = Color(0xFF4C1D95),
    onSecondaryContainer = Color(0xFFF3E8FF),
    tertiary = CreatorAmber,
    onTertiary = Color(0xFF2B1900),
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = StudioSurface,
    onSurface = TextPrimary,
    surfaceVariant = StudioSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = StudioOutline,
    error = CrimsonAlert,
    onError = Color.White
)

private val AuraLightColorScheme = lightColorScheme(
    primary = LuminousCyanDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0C4A6E),
    secondary = ElectricVioletDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E8FF),
    onSecondaryContainer = Color(0xFF3B0764),
    tertiary = CreatorAmber,
    onTertiary = Color.White,
    background = LightStudioBg,
    onBackground = LightTextPrimary,
    surface = LightStudioSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightStudioSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = Color(0xFFCBD5E1),
    error = CrimsonAlert,
    onError = Color.White
)

val AuraShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

enum class ThemeMode {
    STUDIO_DARK,
    SYSTEM,
    LIGHT
}

@Composable
fun AuraPlayerTheme(
    themeMode: ThemeMode = ThemeMode.STUDIO_DARK,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.STUDIO_DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> AuraDarkColorScheme
        else -> AuraLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AuraTypography,
        shapes = AuraShapes,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    AuraPlayerTheme(
        themeMode = if (darkTheme) ThemeMode.STUDIO_DARK else ThemeMode.LIGHT,
        dynamicColor = dynamicColor,
        content = content
    )
}
