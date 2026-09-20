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
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.hug0.calwireless.LogKind
import dev.hug0.calwireless.Status

val Mono = FontFamily.Monospace

data class PanelColors(
    val canvas: Color,
    val panel: Color,
    val panel2: Color,
    val logBg: Color,
    val ink: Color,
    val inkDim: Color,
    val inkFaint: Color,
    val border: Color,
    val sage: Color,
    val amber: Color,
    val rust: Color,
    val slate: Color,
    val switchOn: Color,
    val switchOff: Color,
    val switchThumbOff: Color,
    val switchThumbOn: Color,
    val segmentUnselected: Color,
    val segmentSelBg: Color,
    val segmentSelFg: Color,
    val ruleW: Float,
    val cardBorder: Color,
)

private val Dark = PanelColors(
    canvas = Color(0xFF161518),
    panel = Color(0xFF1F1E22),
    panel2 = Color(0xFF26252B),
    logBg = Color(0xFF101013),
    ink = Color(0xFFECE9E4),
    inkDim = Color(0xFFA6A098),
    inkFaint = Color(0xFF756F68),
    border = Color(0x12ECE9E4),
    sage = Color(0xFF8CBF94),
    amber = Color(0xFFD9A441),
    rust = Color(0xFFD07064),
    slate = Color(0xFF5C5A5E),
    switchOn = Color(0xFF5F8266),
    switchOff = Color(0xFF2A292F),
    switchThumbOff = Color(0xFFA6A098),
    switchThumbOn = Color(0xFF0D1117),
    segmentUnselected = Color(0xFF141317),
    segmentSelBg = Color(0xFF26252B),
    segmentSelFg = Color(0xFFECE9E4),
    ruleW = 1f,
    cardBorder = Color(0x12ECE9E4),
)

// 暖紙：e-ink 預設態。黑字白紙、無陰影、邊界靠 1px 灰線。
private val Light = PanelColors(
    canvas = Color(0xFFF7F4EE),
    panel = Color(0xFFFFFFFF),
    panel2 = Color(0xFFEFEBE3),
    logBg = Color(0xFFFBF9F4),
    ink = Color(0xFF1D1C1A),
    inkDim = Color(0xFF5C574F),
    inkFaint = Color(0xFF8A847B),
    border = Color(0x1F000000),
    sage = Color(0xFF2F7D45),
    amber = Color(0xFF9A6D14),
    rust = Color(0xFFB03A2E),
    slate = Color(0xFFB9B3A9),
    switchOn = Color(0xFF2F7D45),
    switchOff = Color(0xFFE4DFD5),
    switchThumbOff = Color(0xFF8A847B),
    switchThumbOn = Color(0xFFFFFFFF),
    segmentUnselected = Color(0xFFEDE9E1),
    segmentSelBg = Color(0xFFFFFFFF),
    segmentSelFg = Color(0xFF1D1C1A),
    ruleW = 1f,
    cardBorder = Color(0x1F000000),
)

// eink-ui.com token set: pure white paper, near-black ink, warm rules, 2px emphasis, zero shadow.
private val Eink = PanelColors(
    canvas = Color(0xFFFFFFFF),
    panel = Color(0xFFFFFFFF),
    panel2 = Color(0xFFF4F1EC),
    logBg = Color(0xFFFFFFFF),
    ink = Color(0xFF14110F),
    inkDim = Color(0xFF3D3833),
    inkFaint = Color(0xFF6B6560),
    border = Color(0xFFE4DFD8),
    sage = Color(0xFF14110F),
    amber = Color(0xFF6B6560),
    rust = Color(0xFF14110F),
    slate = Color(0xFFC9C2B9),
    switchOn = Color(0xFF14110F),
    switchOff = Color(0xFFFFFFFF),
    switchThumbOff = Color(0xFF14110F),
    switchThumbOn = Color(0xFFFFFFFF),
    segmentUnselected = Color(0xFFFFFFFF),
    segmentSelBg = Color(0xFF14110F),
    segmentSelFg = Color(0xFFFFFFFF),
    ruleW = 2f,
    cardBorder = Color(0xFF14110F),
)

val LocalPanelColors = staticCompositionLocalOf { Dark }

fun darkPalette(): PanelColors = Dark
val LocalTextScale = staticCompositionLocalOf { 1f }

@Composable
@ReadOnlyComposable
fun paletteFor(mode: Int, systemDark: Boolean): PanelColors = when {
    mode == 3 -> Eink
    mode == 1 -> Light
    mode == 2 -> Dark
    systemDark -> Dark
    else -> Light
}

// 全域名字改為讀當前主題（調用點全在 @Composable 內，零改動遷移）
val Canvas: Color @Composable @ReadOnlyComposable get() = LocalPanelColors.current.canvas
val Panel: Color @Composable @ReadOnlyComposable get() = LocalPanelColors.current.panel
val Panel2: Color @Composable @ReadOnlyComposable get() = LocalPanelColors.current.panel2
val LogBg: Color @Composable @ReadOnlyComposable get() = LocalPanelColors.current.logBg
val Ink: Color @Composable @ReadOnlyComposable get() = LocalPanelColors.current.ink
val InkDim: Color @Composable @ReadOnlyComposable get() = LocalPanelColors.current.inkDim
val InkFaint: Color @Composable @ReadOnlyComposable get() = LocalPanelColors.current.inkFaint
val BorderDim: Color @Composable @ReadOnlyComposable get() = LocalPanelColors.current.border
val CardBorder: Color @Composable @ReadOnlyComposable get() = LocalPanelColors.current.cardBorder
val SegSelBg: Color @Composable @ReadOnlyComposable get() = LocalPanelColors.current.segmentSelBg
val SegSelFg: Color @Composable @ReadOnlyComposable get() = LocalPanelColors.current.segmentSelFg
val SegUnselBg: Color @Composable @ReadOnlyComposable get() = LocalPanelColors.current.segmentUnselected
@Composable
@ReadOnlyComposable
fun ruleW(): androidx.compose.ui.unit.Dp = LocalPanelColors.current.ruleW.dp

@Composable
@ReadOnlyComposable
fun pal() = LocalPanelColors.current

fun palIsDark(mode: Int, systemDark: Boolean): Boolean = when (mode) {
    1 -> false
    2 -> true
    3 -> false
    else -> systemDark
}

@Composable
fun DevicePanelTheme(palette: PanelColors, textScale: Float = 1f, content: @Composable () -> Unit) {
    val dark = palette === Dark
    val scheme = if (dark) {
        darkColorScheme(
            primary = palette.sage, background = palette.canvas, surface = palette.panel,
            surfaceVariant = palette.panel2, onBackground = palette.ink, onSurface = palette.ink,
            onSurfaceVariant = palette.inkDim, outline = palette.border, error = palette.rust,
            surfaceContainerLow = Color(0xFF1A191D), surfaceContainer = palette.panel,
            surfaceContainerHigh = palette.panel2, surfaceContainerHighest = Color(0xFF2C2B32),
            secondaryContainer = Color(0xFF2A292F), onSecondaryContainer = palette.ink,
        )
    } else {
        lightColorScheme(
            primary = palette.sage, background = palette.canvas, surface = palette.panel,
            surfaceVariant = palette.panel2, onBackground = palette.ink, onSurface = palette.ink,
            onSurfaceVariant = palette.inkDim, outline = palette.border, error = palette.rust,
            secondaryContainer = palette.panel2, onSecondaryContainer = palette.ink,
        )
    }
    MaterialTheme(colorScheme = scheme) {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalPanelColors provides palette,
            LocalTextScale provides textScale,
        ) { content() }
    }
}

@Composable
@ReadOnlyComposable
fun inkFor(kind: LogKind): Color = when (kind) {
    LogKind.OK -> pal().sage
    LogKind.WARN -> pal().amber
    LogKind.ERR -> pal().rust
    LogKind.INFO -> pal().inkDim
}

@Composable
@ReadOnlyComposable
fun ledFor(status: Status): Pair<Color, Boolean> = when (status) {
    Status.IDLE -> pal().slate to false
    Status.CONNECTING, Status.RETRY, Status.EJECTED -> pal().amber to true
    Status.CONNECTED -> pal().sage to false
    Status.PASSWORD -> pal().rust to false
    Status.BUSY -> pal().amber to false
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
        modifier.size(22.dp).clip(CircleShape)
            .background(color.copy(alpha = if (pulse) 0.16f * lit else 0.13f)),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Box(
            Modifier.size(9.dp).clip(CircleShape)
                .background(color.copy(alpha = lit)),
        )
    }
}
