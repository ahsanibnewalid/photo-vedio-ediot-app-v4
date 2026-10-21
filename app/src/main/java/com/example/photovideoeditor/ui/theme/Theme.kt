package com.example.photovideoeditor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val EditorBackground = Color(0xFF0F0F10)
val EditorSurface = Color(0xFF1A1A1E)
val EditorSurfaceHigh = Color(0xFF26262B)
val EditorPrimary = Color(0xFF00F0FF)
val EditorTextSecondary = Color(0xFF9E9EA6)

private val DarkScheme = darkColorScheme(
    primary = EditorPrimary,
    onPrimary = Color.Black,
    background = EditorBackground,
    surface = EditorSurface,
    surfaceVariant = EditorSurfaceHigh,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = EditorTextSecondary
)

@Composable
fun PhotoVideoEditorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, content = content)
}
