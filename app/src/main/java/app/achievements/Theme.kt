package app.achievements

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

val cBg = Color(0xFF0F151C)
val cBg2 = Color(0xFF18222E)
val cSf = Color(0xFF1F252D)
val cHi = Color(0xFF38414D)
val cTx = Color(0xFFF2F5F8)
val cMu = Color(0xFF8D98A5)
val cAc = Color(0xFF1A9FFF)
val cAc2 = Color(0xFF4F6BFF)
val cGold = Color(0xFFF2C14E)
val cGreen = Color(0xFF5CBF45)

// Steam Big Picture style backdrop: deep blue-black with soft gradient accents.
@Composable
fun Backdrop(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(cBg2, cBg))).drawBehind {
            drawRect(Brush.radialGradient(listOf(cAc.copy(alpha = 0.20f), Color.Transparent), center = Offset(size.width * 0.1f, -size.height * 0.05f), radius = size.width * 1.1f))
            drawRect(Brush.radialGradient(listOf(cAc2.copy(alpha = 0.13f), Color.Transparent), center = Offset(size.width, size.height * 0.95f), radius = size.width * 0.9f))
        },
        content = content
    )
}

// Controller-friendly item: press squish, focus scale, diffused glow plus a bright gradient outline, nav sounds.
fun Modifier.bp(corner: Dp = 14.dp, onClick: () -> Unit): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.965f else if (focused) 1.04f else 1f, spring(0.55f, 380f), label = "sc")
    val gl by animateFloatAsState(if (focused) 1f else 0f, tween(200), label = "gl")
    this.graphicsLayer { scaleX = sc; scaleY = sc }
        .drawBehind {
            if (gl > 0.01f) {
                val r = corner.toPx()
                for (i in 1..6) drawRoundRect(
                    color = cAc.copy(alpha = 0.08f * gl * (7 - i) / 6f),
                    topLeft = Offset(-i * 3.5f, -i * 3.5f),
                    size = Size(size.width + i * 7f, size.height + i * 7f),
                    cornerRadius = CornerRadius(r + i * 3.5f),
                    style = Stroke(7f)
                )
            }
        }
        .drawWithContent {
            drawContent()
            if (gl > 0.01f) drawRoundRect(
                brush = Brush.linearGradient(listOf(Color.White, cAc.copy(alpha = 0.9f))),
                cornerRadius = CornerRadius(corner.toPx()),
                style = Stroke(2.2.dp.toPx()),
                alpha = gl
            )
        }
        .onFocusChanged { if (it.isFocused && !focused) Sfx.play("move"); focused = it.isFocused }
        .clickable(interactionSource = src, indication = null) { Sfx.play("select"); onClick() }
}

// Staggered fade and rise when a list appears or its tab changes.
fun Modifier.appear(key: Any, i: Int): Modifier = composed {
    val a = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { delay(i.coerceAtMost(12) * 28L); a.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }
    this.graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 28f }
}

fun Modifier.rareGlow(on: Boolean): Modifier = if (!on) this else drawBehind {
    val r = size.minDimension * 0.85f
    drawCircle(Brush.radialGradient(listOf(cGold.copy(alpha = 0.55f), Color.Transparent), radius = r), radius = r)
}

@Composable
fun Pill(t: String, on: Boolean = false, accent: Boolean = false, modifier: Modifier = Modifier, click: () -> Unit) {
    val fill: Brush = when {
        accent -> Brush.horizontalGradient(listOf(cAc, cAc2))
        on -> SolidColor(cHi)
        else -> SolidColor(Color(0x33FFFFFF).copy(alpha = 0.10f))
    }
    Box(
        modifier.bp(22.dp, click).clip(RoundedCornerShape(22.dp)).background(fill).padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) { Text(t, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = if (on || accent) Color.White else cMu) }
}

@Composable
fun GradBar(a: Int, b: Int, h: Dp = 6.dp) {
    val f by animateFloatAsState(if (b == 0) 0f else a.toFloat() / b, tween(700), label = "bar")
    Box(Modifier.fillMaxWidth().height(h).clip(RoundedCornerShape(h)).background(Color(0x33FFFFFF))) {
        Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(Brush.horizontalGradient(listOf(cAc, cAc2))))
    }
}

// Frosted glass panel: translucent fill, soft sheen and a fading edge highlight.
fun Modifier.glass(corner: Dp = 16.dp): Modifier = this
    .clip(RoundedCornerShape(corner))
    .background(Brush.linearGradient(listOf(Color(0x24FFFFFF), Color(0x0AFFFFFF))))
    .border(1.dp, Brush.linearGradient(listOf(Color(0x66FFFFFF), Color(0x0DFFFFFF))), RoundedCornerShape(corner))

@Composable
fun Label(t: String) {
    Text(t, fontSize = 12.sp, color = cMu, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
fun Field(v: String, hint: String, set: (String) -> Unit) {
    TextField(
        value = v, onValueChange = set, singleLine = true, modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(hint, color = cMu) }, shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = cSf, unfocusedContainerColor = cSf,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = cTx, unfocusedTextColor = cTx, cursorColor = cAc
        )
    )
}
