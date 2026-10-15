package com.app.tageditor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val NeuColorScheme = darkColorScheme(
    primary = NeuAccent,
    onPrimary = NeuOnAccent,
    secondary = NeuAccentSoft,
    onSecondary = NeuOnAccent,
    background = NeuBackground,
    onBackground = NeuTextPrimary,
    surface = NeuSurface,
    onSurface = NeuTextPrimary,
    surfaceVariant = NeuInset,
    onSurfaceVariant = NeuTextSecondary,
    error = NeuError,
    onError = NeuOnAccent,
    outline = NeuLight
)

@Composable
fun TagEditorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NeuColorScheme,
        typography = NeuTypography,
        shapes = NeuMaterialShapes,
        content = content
    )
}
