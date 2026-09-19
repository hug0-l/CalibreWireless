package dev.hug0.calwireless.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.hug0.calwireless.Led
import dev.hug0.calwireless.LogKind

val Mono = FontFamily.Monospace

val Canvas = Color(0xFF0E1113)
val Panel = Color(0xFF16191C)
val LogBg = Color(0xFF0B0D0F)
val Ink = Color(0xFFE6EDF3)
val InkDim = Color(0xFF8B949E)
val InkFaint = Color(0xFF6E7681)
val BorderDim = Color(0x14FFFFFF)

fun inkFor(kind: LogKind): Color = when (kind) {
    LogKind.OK -> Color(0xFF3FB950)
    LogKind.WARN -> Color(0xFFD29922)
    LogKind.ERR -> Color(0xFFF85149)
    LogKind.INFO -> InkDim
}

@Composable
fun DevicePanelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF3FB950),
            background = Canvas,
            surface = Panel,
            surfaceVariant = Color(0xFF1C2024),
            onBackground = Ink,
            onSurface = Ink,
            onSurfaceVariant = InkDim,
            outline = BorderDim,
            error = Color(0xFFF85149),
        ),
        typography = MaterialTheme.typography.copy(
            titleLarge = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        ),
    ) { content() }
}

fun ledColor(led: Led): Color = when (led) {
    Led.OFF -> Color(0xFF545D68)
    Led.PULSE -> Color(0xFFD29922)
    Led.GREEN -> Color(0xFF3FB950)
    Led.AMBER -> Color(0xFFD29922)
    Led.RED -> Color(0xFFF85149)
}

@Composable
fun StatusLed(led: Led, reducedMotion: Boolean = false, modifier: Modifier = Modifier) {
    val color = ledColor(led)
    val pulse = led == Led.PULSE && !reducedMotion
    val transition = rememberInfiniteTransition(label = "led")
    val anim by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "led-a",
    )
    val lit = if (pulse) anim else 1f
    Box(
        modifier.size(20.dp).clip(CircleShape)
            .background(color.copy(alpha = if (pulse) 0.16f * lit else 0.14f)),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Box(
            Modifier.size(8.dp).clip(CircleShape)
                .background(color.copy(alpha = lit)),
        )
    }
}
