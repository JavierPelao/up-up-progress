package com.upupprogress.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = OrangePrimary,
    onPrimary = Color.White,

    primaryContainer = OrangeSoft,
    onPrimaryContainer = AppTextPrimary,

    secondary = OrangePrimary,
    onSecondary = Color.White,
    secondaryContainer = OrangeSoft,
    onSecondaryContainer = AppTextPrimary,

    tertiary = OrangeAccent,
    onTertiary = AppTextPrimary,

    background = AppBackground,
    onBackground = AppTextPrimary,

    surface = AppSurface,
    onSurface = AppTextPrimary,

    surfaceVariant = OrangeSoft,
    onSurfaceVariant = AppTextSecondary
)


@Composable
fun UpUpProgressTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}