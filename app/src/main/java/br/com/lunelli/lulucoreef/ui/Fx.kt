package br.com.lunelli.lulucoreef.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import br.com.lunelli.lulucoreef.domain.Params
import br.com.lunelli.lulucoreef.domain.num
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Efeitos de agua ligados? (Ajustes > Animacoes). Desligado: fundo parado e sem ondas. */
val LocalWaterFx = compositionLocalOf { true }

/**
 * Relogio dos efeitos de agua: ~20 quadros por segundo, so com a tela em primeiro plano.
 * O valor e lido apenas dentro do desenho (Canvas), entao nao recompoe a tela.
 */
@Composable
fun rememberWaterTime(enabled: Boolean = LocalWaterFx.current): () -> Float {
    val t = remember { mutableFloatStateOf(0f) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, enabled) {
        if (!enabled) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                t.floatValue += 0.05f
                delay(50)
            }
        }
    }
    return { t.floatValue }
}

/**
 * Fundo v0.4: grafite escuro com tres manchas de luz (verde-agua, azul e violeta) que derivam
 * bem devagar, como uma aurora discreta. Sem textura e sem shader (o Fire e API 22).
 */
@Composable
fun WaterBackground(modifier: Modifier = Modifier, strength: Float = 1f) {
    val time = rememberWaterTime()
    Canvas(modifier) {
        val t = time()
        drawRect(Brush.verticalGradient(listOf(Luluco.DeepTop, Luluco.DeepBottom)))
        val w = size.width
        val h = size.height
        val big = maxOf(w, h)
        fun glow(color: Color, a: Float, cx: Float, cy: Float, r: Float) {
            val c = Offset(cx, cy)
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = a * strength), Color.Transparent), c, r), r, c)
        }
        glow(Luluco.Pool, 0.13f, w * (0.18f + 0.10f * sin(t * 0.045f)), h * (0.10f + 0.08f * cos(t * 0.038f)), big * 0.55f)
        glow(Luluco.Royal, 0.12f, w * (0.88f + 0.08f * cos(t * 0.031f)), h * (0.35f + 0.12f * sin(t * 0.027f)), big * 0.50f)
        glow(Luluco.Violet, 0.07f, w * (0.45f + 0.15f * sin(t * 0.022f + 1.3f)), h * (1.0f + 0.06f * cos(t * 0.035f)), big * 0.45f)
    }
}

/** Ondas suaves na base de um cabecalho ou card. */
@Composable
fun Waves(modifier: Modifier = Modifier, color: Color = Luluco.Pool) {
    val time = rememberWaterTime()
    Canvas(modifier) {
        val t = time()
        for (layer in 0..1) {
            val amp = size.height * (if (layer == 0) 0.28f else 0.2f)
            val base = size.height * (if (layer == 0) 0.55f else 0.7f)
            val path = Path()
            path.moveTo(0f, size.height)
            var x = 0f
            val step = size.width / 24f
            while (x <= size.width + step) {
                val y = base + amp * sin((x / size.width) * 2f * PI.toFloat() * (1.5f + layer) + t * (0.9f + layer * 0.5f) + layer * 1.7f)
                path.lineTo(x, y)
                x += step
            }
            path.lineTo(size.width, size.height)
            path.close()
            drawPath(path, color.copy(alpha = if (layer == 0) 0.22f else 0.30f))
        }
    }
}

/** Leve "mola" ao tocar. */
@Composable
fun Modifier.pressSpring(source: InteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "mola")
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Entrada em cascata: cada item sobe e aparece um pouco depois do anterior. */
@Composable
fun Modifier.cascade(index: Int): Modifier {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index.coerceAtMost(12) * 55L)
        a.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
    }
    return graphicsLayer {
        alpha = a.value
        translationY = (1f - a.value) * 22.dp.toPx()
    }
}

/** Numero que conta ate o valor. Ao terminar mostra o valor exato registrado. */
@Composable
fun animatedParam(param: String, value: Double): String {
    val a = remember { Animatable((value * 0.9).toFloat()) }
    LaunchedEffect(value) { a.animateTo(value.toFloat(), tween(750, easing = FastOutSlowInEasing)) }
    return if (a.isRunning) num(a.value.toDouble(), Params.decimals(param)) else Params.format(param, value)
}

/** Anel da nota de saude enchendo, com o numero contando no centro. */
@Composable
fun HealthRing(score: Int?, size: Dp, modifier: Modifier = Modifier, stroke: Dp = size / 9, label: String? = "Saúde") {
    val a = remember { Animatable(0f) }
    LaunchedEffect(score) { a.animateTo((score ?: 0) / 100f, tween(1100, easing = FastOutSlowInEasing)) }
    val color = scoreColor(score)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val sw = stroke.toPx()
            val inset = sw / 2
            val arcSize = Size(this.size.width - sw, this.size.height - sw)
            drawArc(Color.White.copy(alpha = 0.07f), -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(sw))
            if (score != null) drawArc(color, -90f, 360f * a.value, false, Offset(inset, inset), arcSize, style = Stroke(sw, cap = StrokeCap.Round))
        }
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (score == null) "-" else (a.value * 100).roundToInt().toString(), fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.34f).sp, color = color)
            if (label != null) Text(label.uppercase(), fontSize = (size.value * 0.095f).coerceAtLeast(10f).sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold, color = Luluco.TextSoft)
        }
    }
}

fun scoreColor(score: Int?): Color = when {
    score == null -> Luluco.Neutral
    score >= 85 -> Luluco.Ok
    score >= 65 -> Luluco.CoralText
    else -> Luluco.Critical
}

/** Mini grafico (sparkline) com os pontos reais, para a vitrine. */
@Composable
fun Sparkline(values: List<Pair<Long, Double>>, from: Long, to: Long, color: Color, modifier: Modifier = Modifier, band: Pair<Double, Double>? = null) {
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        var lo = values.minOf { it.second }
        var hi = values.maxOf { it.second }
        if (band != null) { lo = minOf(lo, band.first); hi = maxOf(hi, band.second) }
        val pad = ((hi - lo).takeIf { it > 0 } ?: maxOf(1e-3, hi * 0.05)) * 0.15
        lo -= pad; hi += pad
        fun x(t: Long) = ((t - from).toDouble() / (to - from).coerceAtLeast(1)).toFloat().coerceIn(0f, 1f) * size.width
        fun y(v: Double) = (1f - ((v - lo) / (hi - lo)).toFloat()) * size.height
        if (band != null) drawRect(Luluco.Ok.copy(alpha = 0.14f), Offset(0f, y(band.second)), Size(size.width, y(band.first) - y(band.second)))
        for (i in 1 until values.size) {
            drawLine(color, Offset(x(values[i - 1].first), y(values[i - 1].second)), Offset(x(values[i].first), y(values[i].second)), 2.5.dp.toPx(), cap = StrokeCap.Round)
        }
        val last = values.last()
        drawCircle(color, 4.dp.toPx(), Offset(x(last.first), y(last.second)))
    }
}
