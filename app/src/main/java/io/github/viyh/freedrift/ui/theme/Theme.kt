package io.github.viyh.freedrift.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BlackDark = darkColorScheme(
    primary = Color(0xFF7FB3D5),
    onPrimary = Color.Black,
    secondary = Color(0xFF566573),
    background = Color.Black,
    onBackground = Color(0xFFE8E8E8),
    surface = Color(0xFF181818),
    onSurface = Color(0xFFE8E8E8),
    surfaceVariant = Color(0xFF262626),
    onSurfaceVariant = Color(0xFFB0B0B0),
)

@Composable
fun FreeDriftTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = BlackDark,
        content = content,
    )
}
