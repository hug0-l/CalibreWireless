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
import androidx.compose.ui.unit.dp
import dev.hug0.calwireless.LogKind
import dev.hug0.calwireless.Status

val Mono = FontFamily.Monospace

// 暖石墨：一台安靜的器材，不是電競終端機
val Canvas = Color(0xFF161518)
val Panel = Color(0xFF1F1E22)
val Panel2 = Color(0xFF26252B)
val LogBg = Color(0xFF101013)
val Ink = Color(0xFFECE9E4)
val InkDim = Color(0xFFA6A098)
val InkFaint = Color(0xFF756F68)
val BorderDim = Color(0x12ECE9E4)

private val Sage = Color(0xFF8CBF94)
private val Amber = Color(0xFFD9A441)
private val Rust = Color(0xFFD07064)
private val Slate = Color(0xFF5C5A5E)

fun inkFor(kind: LogKind): Color = when (kind) {
    LogKind.OK -> Sage
    LogKind.WARN -> Amber
    LogKind.ERR -> Rust
    LogKind.INFO -> InkDim
}

fun ledFor(status: Status): Pair<Color, Boolean> = when (status) {
    Status.IDLE -> Slate to false
    Status.CONNECTING, Status.RETRY, Status.EJECTED -> Amber to true
    Status.CONNECTED -> Sage to false
    Status.PASSWORD -> Rust to false
    Status.BUSY -> Amber to false
}

@Composable
fun DevicePanelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Sage,
            background = Canvas,
            surface = Panel,
            surfaceVariant = Panel2,
            onBackground = Ink,
            onSurface = Ink,
            onSurfaceVariant = InkDim,
            outline = BorderDim,
            error = Rust,
        ),
    ) { content() }
}

@Composable
fun StatusLed(color: Color, pulse: Boolean, modifier: Modifier = Modifier) {
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
            .background(color.copy(alpha = if (pulse) 0.16f * lit else 0.13f)),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Box(
            Modifier.size(8.dp).clip(CircleShape)
                .background(color.copy(alpha = lit)),
        )
    }
}
