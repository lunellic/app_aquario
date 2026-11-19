package br.com.lunelli.lulucoreef.ui

import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.animation.togetherWith
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
fun AtmosWave(modifier: Modifier = Modifier, alpha: Float = 1f, lines: Int = 26, fadeAt: Float = 1f, stroke: Float = 1.4f) {
    val time = rememberWaterTime(true)
    val paths = remember(lines) { List(lines) { androidx.compose.ui.graphics.Path() } }
    Canvas(modifier) {
        val t = time()
        val w = size.width
        val h = size.height
        val cy = h / 2
        val amp = h * 0.30f
        // cores da logo: azul royal -> azul claro -> laranja coral
        val sky = Color(0xFF3FA9E8)
        // a fronteira azul/laranja vai e volta devagar: metade sempre azul, metade laranja, trocando de lado
        val (left, right) = waveSideColors(t)
        val mid = sky
        val brush = Brush.horizontalGradient(0f to left.copy(alpha = 0f), 0.18f to left.copy(alpha = 0.6f * alpha), 0.42f to mid.copy(alpha = 0.45f * alpha), 0.62f to right.copy(alpha = 0.4f * alpha), fadeAt to right.copy(alpha = 0.16f * alpha), 1f to right.copy(alpha = 0.05f * alpha))
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
            drawPath(p, brush, style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke))
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
fun Sparkline(values: List<Pair<Long, Double>>, from: Long, to: Long, color: Color, modifier: Modifier = Modifier, band: Pair<Double, Double>? = null, colorOf: ((Double) -> Color)? = null) {
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
            drawLine(color.copy(alpha = if (colorOf != null) 0.5f else 1f), Offset(x(values[i - 1].first), y(values[i - 1].second)), Offset(x(values[i].first), y(values[i].second)), 1.6.dp.toPx(), cap = StrokeCap.Round)
        }
        // cada ponto na cor da zona em que caiu
        if (colorOf != null) values.forEach { drawCircle(lift(colorOf(it.second)), 2.4.dp.toPx(), Offset(x(it.first), y(it.second))) }
        val last = values.last()
        drawCircle(colorOf?.let { lift(it(last.second)) } ?: color, 3.2.dp.toPx(), Offset(x(last.first), y(last.second)))
    }
}

/** Cor de cada ponta da onda no instante t: azul e laranja trocando de lado devagar, em loop. */
fun waveSideColors(t: Float): Pair<Color, Color> {
    val blue = Color(0xFF2F6BE0); val orange = Color(0xFFF26A3D)
    val k = 0.5f + 0.5f * sin(t * 0.06f)
    fun mix(a: Color, b: Color, f: Float) = Color(a.red + (b.red - a.red) * f, a.green + (b.green - a.green) * f, a.blue + (b.blue - a.blue) * f, 1f)
    return mix(blue, orange, k) to mix(orange, blue, k)
}

/**
 * Relogio de virar: so o digito que mudou desce (o novo entra por cima, o antigo sai por baixo),
 * como os relogios antigos de plaquinha. 12:09 -> 12:10 mexe nos dois ultimos; 12:10 -> 12:11 so no ultimo.
 */
@Composable
fun FlipClock(time: String, fontSize: androidx.compose.ui.unit.TextUnit, color: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Row(modifier, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy((-fontSize.value * 0.03f).dp)) {
        time.forEachIndexed { i, ch ->
            androidx.compose.runtime.key(i) {
                androidx.compose.animation.AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        (androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(520, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { -it } +
                            androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(380))) togetherWith
                            (androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(520, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it } +
                                androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(300)))
                    },
                    label = "digito$i",
                ) { c ->
                    Text(c.toString(), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = fontSize, lineHeight = fontSize, color = color, maxLines = 1)
                }
            }
        }
    }
}

/** Barlow Condensed (licenca OFL, embutida): numeros altos e estreitos para o relogio de vidro. */
val Barlow = androidx.compose.ui.text.font.FontFamily(
    androidx.compose.ui.text.font.Font(br.com.lunelli.lulucoreef.R.font.barlow_medium, FontWeight.Medium),
    androidx.compose.ui.text.font.Font(br.com.lunelli.lulucoreef.R.font.barlow_semibold, FontWeight.SemiBold),
    androidx.compose.ui.text.font.Font(br.com.lunelli.lulucoreef.R.font.barlow_bold, FontWeight.Bold),
)

/** "Qui 19 Nov" */
fun shortDate(d: java.time.LocalDate): String {
    val dias = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")
    val meses = listOf("Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez")
    return "${dias[d.dayOfWeek.value - 1]} ${d.dayOfMonth} ${meses[d.monthValue - 1]}"
}

/**
 * Relogio de vidro (referencia do Lucas): pilula com a data em cima e um painel de vidro escuro com
 * numeros altos, estreitos e juntos, cor de aluminio, com brilho na borda de cima e sombra embaixo.
 * Cada digito vira sozinho quando muda. [dim] escurece tudo para o modo noite.
 */
@Composable
fun GlassClock(time: String, date: String, modifier: Modifier = Modifier, maxFont: Float = 240f, dim: Float = 1f) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        // 4 digitos + dois pontos ocupam ~1,3 em na Barlow Condensed SemiBold com os digitos encostados
        val fs = minOf(maxFont, (maxWidth.value - 40f) / 1.32f)
        val size = fs.sp
        val glassShape = androidx.compose.foundation.shape.RoundedCornerShape((fs * 0.13f).dp)
        val pillShape = androidx.compose.foundation.shape.RoundedCornerShape(50)
        val rim = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.30f * dim), Color.White.copy(alpha = 0.06f * dim)))
        val glass = Brush.verticalGradient(listOf(Color(0xFF3B3E43).copy(alpha = 0.92f), Color(0xFF1C1E21).copy(alpha = 0.92f)))
        androidx.compose.foundation.layout.Column(Modifier.graphicsLayer { alpha = dim }) {
            // data numa pilula de vidro
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxWidthCompat().clip(pillShape).background(glass).border(1.5.dp, rim, pillShape).padding(vertical = (fs * 0.025f).coerceIn(6f, 12f).dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(date, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = (fs * 0.10f).coerceIn(18f, 30f).sp, color = Color(0xFFF2F3F5))
            }
            androidx.compose.foundation.layout.Spacer(Modifier.height((fs * 0.03f).dp))
            // painel de vidro com os numeros
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxWidthCompat().clip(glassShape).background(glass).border(1.5.dp, rim, glassShape)
                    .padding(horizontal = (fs * 0.035f).dp, vertical = (fs * 0.035f).dp),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy((-fs * 0.035f).dp)) {
                    time.forEachIndexed { i, ch ->
                        androidx.compose.runtime.key(i) {
                            if (ch == ':') GlassColon(fs)
                            else androidx.compose.animation.AnimatedContent(
                                targetState = ch,
                                transitionSpec = {
                                    (androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(560, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { -it } +
                                        androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(400))) togetherWith
                                        (androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(560, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it } +
                                            androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(320)))
                                },
                                label = "vidro$i",
                            ) { c -> GlassDigit(c, size, fs) }
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.fillMaxWidthCompat() = this.fillMaxWidth()

private val DigitFill = Brush.verticalGradient(listOf(Color(0xFFA9ACB1), Color(0xFF8C9095), Color(0xFF7A7E83)))
private val DigitRim = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.25f)))

@Composable
private fun GlassDigit(c: Char, size: androidx.compose.ui.unit.TextUnit, fs: Float) {
    val base = androidx.compose.ui.text.TextStyle(
        fontFamily = Barlow, fontWeight = FontWeight.SemiBold, fontSize = size, lineHeight = size * 0.98f,
        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center, androidx.compose.ui.text.style.LineHeightStyle.Trim.Both),
    )
    // caixa da altura do numero (sem o respiro da fonte em cima e embaixo): os digitos enchem o painel
    androidx.compose.foundation.layout.Box(Modifier.height((fs * 0.74f).dp).wrapContentHeight(unbounded = true), contentAlignment = Alignment.Center) {
        // corpo com degrade de aluminio e sombra
        Text(c.toString(), style = base.copy(brush = DigitFill, shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 6f), 14f)))
        // borda clara em cima e escura embaixo: o efeito de vidro
        Text(c.toString(), style = base.copy(brush = DigitRim, drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.2f, join = StrokeJoin.Round)))
    }
}

/** Dois pontos redondos, como na referencia. */
@Composable
private fun GlassColon(fs: Float) {
    val d = (fs * 0.13f).dp
    androidx.compose.foundation.layout.Column(
        Modifier.padding(horizontal = (fs * 0.045f).dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy((fs * 0.22f).dp),
    ) {
        repeat(2) {
            androidx.compose.foundation.layout.Box(Modifier.size(d).clip(CircleShape).background(DigitFill).border(1.5.dp, DigitRim, CircleShape))
        }
    }
}
