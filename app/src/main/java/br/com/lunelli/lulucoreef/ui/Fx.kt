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

/** Fundo: preto azulado liso, com um clarão quase imperceptivel no topo. */
@Composable
fun WaterBackground(modifier: Modifier = Modifier, strength: Float = 1f) {
    Canvas(modifier) {
        drawRect(Luluco.Navy)
        drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.025f * strength), Color.Transparent), endY = size.height * 0.5f))
    }
}

/**
 * Uma unica onda em fita, feita de muitas linhas finas que se torcem devagar em loop infinito
 * (inspirada na animacao do Dolby Atmos). Sem shader: so Path e gradiente linear, roda no Fire.
 */
@Composable
fun AtmosWave(modifier: Modifier = Modifier, alpha: Float = 1f, lines: Int = 26, fadeAt: Float = 1f) {
    val time = rememberWaterTime(true)
    val paths = remember(lines) { List(lines) { androidx.compose.ui.graphics.Path() } }
    Canvas(modifier) {
        val t = time()
        val w = size.width
        val h = size.height
        val cy = h / 2
        val amp = h * 0.30f
        val brush = Brush.horizontalGradient(0f to Luluco.Pool.copy(alpha = 0f), 0.2f to Luluco.Pool.copy(alpha = 0.55f * alpha), 0.55f to Luluco.Royal.copy(alpha = 0.45f * alpha), fadeAt to Luluco.Violet.copy(alpha = 0.16f * alpha), 1f to Luluco.Violet.copy(alpha = 0.05f * alpha))
        val steps = 72
        for (i in 0 until lines) {
            val f = i / (lines - 1f)
            val p = paths[i]
            p.reset()
            for (s in 0..steps) {
                val x = w * s / steps
                val u = x / w * 6.2832f
                // fita: duas bordas defasadas; cada linha interpola entre elas e a torcao gira com o tempo
                val twist = cos(u * 0.9f - t * 0.35f + f * 3.1416f)
                val y = cy + amp * sin(u * 0.75f + t * 0.22f) * (0.55f + 0.45f * sin(t * 0.07f + u * 0.3f)) + amp * 0.42f * twist * (f - 0.5f) * 2f
                if (s == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            drawPath(p, brush, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.4f))
        }
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
fun HealthRing(score: Int?, size: Dp, modifier: Modifier = Modifier, stroke: Dp = size / 18, label: String? = "Saúde") {
    val a = remember { Animatable(0f) }
    LaunchedEffect(score) { a.animateTo((score ?: 0) / 100f, tween(1100, easing = FastOutSlowInEasing)) }
    val color = scoreColor(score)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val sw = stroke.toPx()
            val inset = sw / 2
            val arcSize = Size(this.size.width - sw, this.size.height - sw)
            drawArc(Color.White.copy(alpha = 0.06f), -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(sw))
            if (score != null) drawArc(color, -90f, 360f * a.value, false, Offset(inset, inset), arcSize, style = Stroke(sw, cap = StrokeCap.Round))
        }
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (score == null) "-" else (a.value * 100).roundToInt().toString(), fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = (size.value * 0.32f).sp, letterSpacing = (-1).sp, color = Luluco.Cream)
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
        if (band != null) drawRect(Color.White.copy(alpha = 0.05f), Offset(0f, y(band.second)), Size(size.width, y(band.first) - y(band.second)))
        for (i in 1 until values.size) {
            drawLine(color, Offset(x(values[i - 1].first), y(values[i - 1].second)), Offset(x(values[i].first), y(values[i].second)), 1.6.dp.toPx(), cap = StrokeCap.Round)
        }
        val last = values.last()
        drawCircle(color, 2.8.dp.toPx(), Offset(x(last.first), y(last.second)))
    }
}
