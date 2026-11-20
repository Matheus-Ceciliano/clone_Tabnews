package com.punto.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Bg = Color(0xFF0A0D12)
val PanelBg = Color(0xFF141A23)
val PanelBg2 = Color(0xFF1C2431)
val Accent = Color(0xFFFF6A2B)
val Cyan = Color(0xFF2EC5FF)
val Amber = Color(0xFFFFC23D)
val Danger = Color(0xFFFF2D55)
val TextMain = Color(0xFFE9EEF5)
val TextDim = Color(0xFF8792A3)
val Track = Color(0xFF232C3A)
val Ok = Color(0xFF34D399)

@Composable
fun PuntoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color.White,
            secondary = Cyan,
            background = Bg,
            onBackground = TextMain,
            surface = PanelBg,
            onSurface = TextMain,
            surfaceVariant = PanelBg2,
            onSurfaceVariant = TextDim,
            surfaceContainerHigh = PanelBg2,
        ),
        content = content,
    )
}

/** Cartão escuro arredondado usado em toda a tela. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(PanelBg)
            .padding(14.dp),
        content = content,
    )
}
