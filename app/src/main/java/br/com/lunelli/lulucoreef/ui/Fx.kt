package br.com.lunelli.lulucoreef.ui

import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
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

/** Onda do descanso ligada? (Ajustes > Onda no descanso). Desligada: fundo parado, sem onda. */

/**
 * Microanimacoes de dentro do app ligadas? (Ajustes > Animacoes da interface).
 *
 * Sao a entrada em cascata dos blocos, o numero contando, o anel enchendo, a mola do botao e a
 * transicao entre telas. Nenhuma delas mostra informacao: so enfeitam a troca de tela. No Fire HD 8
 * de 2017 elas custam quadros em cima do que importa, entao vem desligadas por padrao.
 */
val LocalAnim = compositionLocalOf { false }

/** Fundo: preto azulado liso, com um clarão quase imperceptivel no topo. */
@Composable
fun WaterBackground(modifier: Modifier = Modifier, strength: Float = 1f) {
    Canvas(modifier) {
        drawRect(Luluco.Navy)
        // Clarao ambiente no topo, bem apagado, na cor do acento. Ideia do Financia: da profundidade
        // sem desenhar nada. Fica em 6% para nao virar mancha num tablet que fica aceso o dia todo.
        drawRect(
            Brush.radialGradient(
                listOf(Luluco.Pool.copy(alpha = 0.06f * strength), Color.Transparent),
                center = Offset(size.width / 2, 0f),
                radius = size.width * 0.75f,
            ),
        )
        drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.02f * strength), Color.Transparent), endY = size.height * 0.5f))
    }
}

/** Leve "mola" ao tocar. */
@Composable
fun Modifier.pressSpring(source: InteractionSource): Modifier {
    if (!LocalAnim.current) return this
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "mola")
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Entrada em cascata: cada item sobe e aparece um pouco depois do anterior. */
@Composable
fun Modifier.cascade(index: Int): Modifier {
    if (!LocalAnim.current) return this
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
    if (!LocalAnim.current) return Params.format(param, value)
    val a = remember { Animatable((value * 0.9).toFloat()) }
    LaunchedEffect(value) { a.animateTo(value.toFloat(), tween(750, easing = FastOutSlowInEasing)) }
    return if (a.isRunning) num(a.value.toDouble(), Params.decimals(param)) else Params.format(param, value)
}

/**
 * Mostrador da nota de saude, em tracos.
 *
 * Era um arco cheio, que so dizia "mais ou menos cheio". O traco marca o passo da escala, entao da
 * para ver quanto falta sem ler o numero. A ideia vem do mostrador do Gluco.
 */
@Composable
fun HealthRing(score: Int?, size: Dp, modifier: Modifier = Modifier, stroke: Dp = size / 18, label: String? = "Saúde", critico: Boolean = false) {
    val anim = LocalAnim.current
    val a = remember { Animatable(if (anim) 0f else (score ?: 0) / 100f) }
    LaunchedEffect(score, anim) { if (anim) a.animateTo((score ?: 0) / 100f, tween(1100, easing = FastOutSlowInEasing)) else a.snapTo((score ?: 0) / 100f) }
    val color = scoreColor(score, critico)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        // Mostrador de tracos em vez de arco cheio: o traco marca o passo da escala, entao da para
        // ver quanto falta sem ler o numero. O arco cheio so dizia "mais ou menos cheio".
        Canvas(Modifier.size(size)) {
            val tracos = 44
            val raio = this.size.minDimension / 2 - stroke.toPx() * 1.2f
            val centro = Offset(this.size.width / 2, this.size.height / 2)
            val acesos = if (score == null) 0 else Math.round(a.value * tracos)
            val w = stroke.toPx() * 0.52f
            for (i in 0 until tracos) {
                val ang = (i.toFloat() / tracos) * 2f * PI.toFloat() - PI.toFloat() / 2f
                val r1 = raio - stroke.toPx() * 1.05f
                val p1 = Offset(centro.x + cos(ang) * r1, centro.y + sin(ang) * r1)
                val p2 = Offset(centro.x + cos(ang) * raio, centro.y + sin(ang) * raio)
                drawLine(
                    if (i < acesos) color else Color.White.copy(alpha = 0.09f),
                    p1, p2, strokeWidth = w, cap = StrokeCap.Round,
                )
            }
        }
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (score == null) "-" else (a.value * 100).roundToInt().toString(),
                fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.32f).sp,
                letterSpacing = (-1).sp, color = color,
            )
            if (label != null) Text(label.uppercase(), fontSize = (size.value * 0.095f).coerceAtLeast(10f).sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold, color = Luluco.TextSoft)
        }
    }
}

/**
 * Cor do anel da nota. A nota e um total: 85 com um unico parametro critico ainda da 85, e pela
 * regra do numero o anel ficaria verde. Verde se le como "esta tudo bem", e nao esta: com calcio
 * critico nada esta bem. Por isso, havendo parametro critico, o anel nao passa de laranja.
 */
fun scoreColor(score: Int?, critico: Boolean = false): Color = when {
    score == null -> Luluco.Neutral
    critico -> if (score >= 65) Luluco.CoralText else Luluco.Critical
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

/** Barlow Condensed (licenca OFL, embutida): numeros altos e estreitos para o relogio do descanso. */
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
 * Relogio do descanso: numeros chapados, sem painel atras e sem animacao.
 *
 * Era um relogio "de vidro": degrade de aluminio, contorno claro em cima e escuro embaixo, sombra e
 * virada animada por digito. Saiu inteiro por tres motivos que apontavam para o mesmo lugar:
 *
 * 1. o efeito de vidro nao era o que o Lucas queria ("a cor um pouco mais dura, mais chapada");
 * 2. em alguns horarios o digito aparecia partido na vertical, com metade mais clara que a outra
 *    (o 9, por exemplo). A causa era o contorno em Stroke sobre a fonte em tamanho enorme: no Fire
 *    o glifo vai para uma textura e o tracado nao fecha igual nas duas metades;
 * 3. desenhava dois textos com pincel por digito, mais sombra, mais quatro animacoes rodando sem
 *    parar. Era o item mais caro da tela que fica ligada o dia inteiro.
 *
 * Agora e uma cor so, um desenho de texto por grupo de digitos e nenhuma animacao.
 */
@Composable
fun GlassClock(time: String, modifier: Modifier = Modifier, maxFont: Float = 240f, dim: Float = 1f) {
    val medidor = androidx.compose.ui.text.rememberTextMeasurer()
    val densidade = androidx.compose.ui.platform.LocalDensity.current
    fun estiloEm(tamanho: Float, cor: Color) = androidx.compose.ui.text.TextStyle(
        fontFamily = Barlow, fontWeight = FontWeight.SemiBold, fontSize = tamanho.sp, lineHeight = (tamanho * 0.98f).sp, color = cor,
        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center, androidx.compose.ui.text.style.LineHeightStyle.Trim.Both),
    )
    // Largura de quatro digitos MEDIDA na fonte de verdade, a 100sp, em dp. Antes era um chute
    // ("4 digitos + dois pontos ~ 1,3 em") que estava errado pela metade: a 300sp do modo noite o
    // relogio pedia uns 690 dp numa caixa de 520, a Row espremia o ultimo bloco e os minutos
    // quebravam em duas linhas, "0" em cima e "1" embaixo. Medir tambem respeita a escala de
    // fonte do sistema, que o chute ignorava.
    val digitosA100 = remember(densidade) {
        with(densidade) { medidor.measure("0000", estiloEm(100f, Color.White), softWrap = false, maxLines = 1).size.width.toDp().value }
    }
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        // em unidades de "fs": digitos (medidos) + dois pontos (0,225) - dois vaos negativos (0,07)
        val porFs = digitosA100 / 100f + 0.225f - 0.07f
        val fs = minOf(maxFont, maxWidth.value / porFs * 0.96f)
        val cor = Luluco.Cream.copy(alpha = 0.93f * dim)
        val estilo = estiloEm(fs, cor)
        val partes = time.split(":")
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy((-fs * 0.035f).dp),
        ) {
            FlatDigits(partes.getOrElse(0) { time }, estilo, fs)
            if (partes.size > 1) {
                FlatColon(fs, cor)
                FlatDigits(partes[1], estilo, fs)
            }
        }
    }
}

/** Caixa da altura do numero (sem o respiro da fonte em cima e embaixo), para o numero nao ficar cortado. */
@Composable
private fun FlatDigits(texto: String, estilo: androidx.compose.ui.text.TextStyle, fs: Float) {
    Box(Modifier.height((fs * 0.92f).dp).wrapContentHeight(unbounded = true), contentAlignment = Alignment.Center) {
        // Uma linha, sempre. Se um dia nao couber, corta na borda; quebrar o horario ao meio
        // e pior do que perder um pedaco de digito.
        Text(texto, style = estilo, maxLines = 1, softWrap = false)
    }
}

/** Dois pontos redondos, na mesma cor chapada dos numeros. */
@Composable
private fun FlatColon(fs: Float, cor: Color) {
    val d = (fs * 0.125f).dp
    androidx.compose.foundation.layout.Column(
        Modifier.padding(horizontal = (fs * 0.05f).dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy((fs * 0.22f).dp),
    ) {
        repeat(2) { Box(Modifier.size(d).clip(CircleShape).background(cor)) }
    }
}
