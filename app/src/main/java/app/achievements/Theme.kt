package app.achievements

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// Palette and fonts taken from the steamOS Pegasus theme: flat dark panels, one blue accent, gold for numbers.
val cBg = Color(0xFF0F1318)
val cPanel = Color(0xFF171B21)
val cSf = Color(0xFF1B2028)
val cHi = Color(0xFF27303A)
val cTx = Color.White
val cMu = Color(0xFF8A8FA3)
val cAc = Color(0xFF1A9EFF)
val cGold = Color(0xFFFFD34E)
val cGreen = Color(0xFF6CCF58)

// Gilroy ExtraBold for titles and buttons (set in MainActivity); body text is Arial Bold.
var headFont: FontFamily = FontFamily.SansSerif

@Composable
fun Backdrop(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(cBg), content = content)
}

// Controller-friendly item: slight scale on focus, crisp blue frame, theme navigation sounds. No rounding anywhere.
fun Modifier.bp(corner: Dp = 0.dp, snd: String = "select", onClick: () -> Unit): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.97f else if (focused) 1.03f else 1f, tween(120), label = "sc")
    val gl by animateFloatAsState(if (focused) 1f else 0f, tween(120), label = "gl")
    this.graphicsLayer { scaleX = sc; scaleY = sc }
        .drawWithContent {
            drawContent()
            if (gl > 0.01f) {
                val t = 3.dp.toPx()
                drawRect(color = cAc, style = Stroke(t), alpha = gl)
                drawRect(color = Color.White, topLeft = Offset(t, t), size = Size(size.width - 2 * t, size.height - 2 * t), style = Stroke(1.dp.toPx()), alpha = gl * 0.6f)
            }
        }
        .onFocusChanged { if (it.isFocused && !focused) Sfx.play("move"); focused = it.isFocused }
        .clickable(interactionSource = src, indication = null) { Sfx.play(snd); onClick() }
}

// Staggered fade and rise when a list appears or its tab changes.
fun Modifier.appear(key: Any, i: Int): Modifier = composed {
    val a = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { delay(i.coerceAtMost(12) * 28L); a.animateTo(1f, tween(280, easing = FastOutSlowInEasing)) }
    this.graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 24f }
}

// Rare achievements (under 10%) get a pulsing gold frame.
fun Modifier.rareGlow(on: Boolean): Modifier = if (!on) this else composed {
    val inf = rememberInfiniteTransition(label = "rare")
    val a by inf.animateFloat(0.5f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "a")
    this.drawWithContent { drawContent(); drawRect(color = cGold, style = Stroke(3.dp.toPx()), alpha = a) }
}

@Composable
fun Pill(t: String, on: Boolean = false, accent: Boolean = false, tab: Boolean = false, modifier: Modifier = Modifier, click: () -> Unit) {
    val filled = on || accent
    Box(
        modifier.bp(0.dp, if (tab) "tab" else "select", click).background(if (filled) cAc else cHi).padding(horizontal = 18.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(t, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = headFont, letterSpacing = 0.6.sp, color = if (filled) Color.White else cMu)
    }
}

@Composable
fun GradBar(a: Int, b: Int, h: Dp = 6.dp) {
    val f by animateFloatAsState(if (b == 0) 0f else a.toFloat() / b, tween(700), label = "bar")
    val inf = rememberInfiniteTransition(label = "shine")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "t")
    Box(Modifier.fillMaxWidth().height(h).background(cHi)) {
        Box(
            Modifier.fillMaxWidth(f).fillMaxHeight().clipToBounds().background(cAc).drawWithContent {
                drawContent()
                val w = size.width
                drawRect(Color.White.copy(alpha = 0.35f), topLeft = Offset((t * 1.6f - 0.3f) * w, 0f), size = Size(w * 0.16f, size.height))
            }
        )
    }
}

fun Modifier.glass(corner: Dp = 0.dp): Modifier = this.background(cPanel)

@Composable
fun Label(t: String) {
    Text(t, fontSize = 12.sp, color = cMu, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
fun Field(v: String, hint: String, set: (String) -> Unit) {
    TextField(
        value = v, onValueChange = set, singleLine = true, modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(hint, color = cMu) }, shape = RectangleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = cSf, unfocusedContainerColor = cSf,
            focusedIndicatorColor = cAc, unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = cTx, unfocusedTextColor = cTx, cursorColor = cAc
        )
    )
}
