package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ThemeColorScheme = lightColorScheme(
    primary = NeonEmerald,
    secondary = OrangeSwiggy,
    tertiary = ElectricIndigo,
    background = SlateDark,
    surface = SlateCard,
    surfaceVariant = SlateBorder,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    outline = SlateBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // Force false for the premium, clinical Teal light theme
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = ThemeColorScheme,
        typography = Typography,
        content = content
    )
}

