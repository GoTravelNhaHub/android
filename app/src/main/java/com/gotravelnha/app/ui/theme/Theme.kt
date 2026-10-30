package com.gotravelnha.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette of the site's community design (web/tailwind.config.js, `cm` colours)
object Brand {
    val Primary = Color(0xFF0066E4)
    val Active = Color(0xFF0642D2)
    val Ink = Color(0xFF0A1024)
    val Body = Color(0xFF2B3245)
    val Muted = Color(0xFF7E8496)
    val Line = Color(0xFFE6EBF3)
    val Soft = Color(0xFFEAF2FC)
    val Canvas = Color(0xFFF7F9FD)
    val Heart = Color(0xFFE0476A)
    val Klook = Color(0xFFFF5B00)
    val Trip = Color(0xFF287DFA)

    val DayColours = listOf(0xFF0060FC, 0xFFFC6008, 0xFF0CA678, 0xFFC2255C, 0xFF7048E8, 0xFFE67700, 0xFF1098AD, 0xFF5C940D, 0xFFD6336C, 0xFF495057)
        .map { Color(it) }

    fun day(day: Int) = DayColours[(day - 1).mod(DayColours.size)]
}

private val Light = lightColorScheme(
    primary = Brand.Primary,
    onPrimary = Color.White,
    primaryContainer = Brand.Soft,
    onPrimaryContainer = Brand.Active,
    secondary = Brand.Active,
    background = Brand.Canvas,
    onBackground = Brand.Ink,
    surface = Color.White,
    onSurface = Brand.Ink,
    surfaceVariant = Color(0xFFF1F4F9),
    onSurfaceVariant = Brand.Muted,
    outline = Brand.Line,
    outlineVariant = Brand.Line,
)

private val Dark = darkColorScheme(
    primary = Color(0xFF5C9DF0),
    onPrimary = Color(0xFF0A1024),
    primaryContainer = Color(0xFF0B3580),
    onPrimaryContainer = Color(0xFFE3EEFD),
    secondary = Color(0xFF9DC3F7),
    background = Color(0xFF0A1024),
    onBackground = Color(0xFFE6EBF3),
    surface = Color(0xFF131A30),
    onSurface = Color(0xFFE6EBF3),
    surfaceVariant = Color(0xFF1D2540),
    onSurfaceVariant = Color(0xFFA2A8B4),
    outline = Color(0xFF2B3245),
    outlineVariant = Color(0xFF2B3245),
)

@Composable
fun GoTravelNhaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
