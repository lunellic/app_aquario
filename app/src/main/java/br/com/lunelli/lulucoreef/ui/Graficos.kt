package br.com.lunelli.lulucoreef.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lunelli.lulucoreef.data.ParamRange
import br.com.lunelli.lulucoreef.data.WaterTest
import br.com.lunelli.lulucoreef.domain.DAY_MS
import br.com.lunelli.lulucoreef.domain.Params
import br.com.lunelli.lulucoreef.domain.faixasDe
import br.com.lunelli.lulucoreef.domain.Trend
import br.com.lunelli.lulucoreef.domain.num
import br.com.lunelli.lulucoreef.domain.trendOf
import kotlin.math.roundToInt

private val PERIODS = listOf("7" to "7 dias", "30" to "30 dias", "90" to "90 dias", "365" to "1 ano")

/** Um grafico por parametro. So pontos reais; faixa ideal sombreada; TPAs e doses no eixo do tempo; projecao tracejada. */
@Composable
fun GraficosScreen(vm: MainViewModel) {
    val now by vm.now.collectAsStateWithLifecycle()
    val tests by vm.tests.collectAsStateWithLifecycle()
    val ranges by vm.ranges.collectAsStateWithLifecycle()
    val changes by vm.changes.collectAsStateWithLifecycle()
    val dosings by vm.dosings.collectAsStateWithLifecycle()
    var period by remember { mutableStateOf("30") }
    val days = period.toInt()
    val from = now - days * DAY_MS
    // espaco a direita para a projecao (no maximo 14 dias)
    val to = now + minOf(14, maxOf(2, days / 4)) * DAY_MS
    val tpas = changes.map { it.feitaEm }.filter { it in from..now }
    val doses = dosings.map { it.dosadoEm }.filter { it in from..now }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader("Parâmetros")
        Segmented(PERIODS, period, Modifier.fillMaxWidth()) { period = it }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Legend(Luluco.Ok.copy(alpha = 0.35f), "faixa ideal")
            Legend(Luluco.Pool, "TPA")
            Legend(Luluco.Cream.copy(alpha = 0.6f), "dose")
            Legend(Luluco.Coral, "projeção (tracejado)")
        }
        (Params.MAIN + Params.OPTIONAL).forEach { p ->
            val pts = tests.filter { it.parametro == p && it.medidoEm in from..now }.sortedBy { it.medidoEm }
            if (p in Params.OPTIONAL && pts.isEmpty()) return@forEach
            val trend = remember(tests, ranges, now) { trendOf(tests, p, ranges[p], now) }
            Panel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${Params.label(p)} (${Params.unit(p)})", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Muted("${pts.size} medições" + (pts.lastOrNull { !it.baixaConfianca }?.let { " · última ${Params.format(p, it.valor)}" } ?: ""))
                }
                if (pts.isEmpty()) {
                    Muted("Sem medições neste período.")
                } else {
                    Chart(p, pts, ranges[p], trend, tpas, doses, from, to, now)
                    if (trend?.daysToExit != null) Text("Projeção: sai da faixa ideal em ~${maxOf(1, trend.daysToExit.roundToInt())} dias", color = Luluco.CoralText, style = MaterialTheme.typography.bodySmall)
                    else if (trend != null) Muted("Projeção: tendência de ${if (trend.slopePerDay >= 0) "+" else ""}${Params.format(p, trend.slopePerDay * 7)} por semana.")
                }
            }
        }
        Compare(tests.filter { !it.baixaConfianca }, from, now)
        Spacer(Modifier.height(8.dp))
    }
}

/** Ate 6 parametros no mesmo grafico, cada um na propria escala, com periodo proprio (ex.: KH, Ca e Mg em 90 dias). */
@Composable
private fun Compare(tests: List<WaterTest>, @Suppress("UNUSED_PARAMETER") fromTop: Long, now: Long) {
    var sel by remember { mutableStateOf(setOf("kh", "ca", "mg")) }
    var dias by remember { mutableStateOf("90") }
    val from = now - (dias.toLong() * DAY_MS)
    val all = Params.MAIN + Params.OPTIONAL
    val cores = listOf(Luluco.AreaTeste, Luluco.Coral, Luluco.AreaAnimal, Luluco.AreaCalc, Luluco.AreaGrafico, Luluco.AreaTpa)
    val cor = all.withIndex().associate { (i, p) -> p to cores[i % cores.size] }
    Panel(Modifier.fillMaxWidth()) {
        Text("Comparar parâmetros", fontWeight = FontWeight.SemiBold)
        Muted("Marque quantos quiser. Cada linha usa a própria escala: compare o formato, não a altura.")
        Spacer(Modifier.height(8.dp))
        ChipRow(all.map { it to Params.label(it) }, null, selectedSet = sel) { p -> sel = if (p in sel) sel - p else sel + p }
        Spacer(Modifier.height(6.dp))
        ChipRow(listOf("7" to "7 dias", "30" to "30 dias", "90" to "90 dias", "365" to "1 ano"), dias) { dias = it }
        val series = sel.toList().sortedBy { all.indexOf(it) }.map { p -> p to tests.filter { it.parametro == p && it.medidoEm in from..now }.sortedBy { it.medidoEm } }
        Spacer(Modifier.height(10.dp))
        if (series.none { it.second.size >= 2 }) Muted("Poucas medições neste período.")
        else Canvas(Modifier.fillMaxWidth().height(220.dp)) {
            fun x(t: Long) = ((t - from).toFloat() / (now - from).coerceAtLeast(1)) * size.width
            for (i in 0..4) drawLine(Color.White.copy(alpha = 0.05f), Offset(0f, size.height * i / 4), Offset(size.width, size.height * i / 4), 1f)
            series.forEach { (p, s) ->
                if (s.isEmpty()) return@forEach
                val c = cor[p] ?: Luluco.Cream
                val lo = s.minOf { it.valor }; val hi = s.maxOf { it.valor }
                val span = (hi - lo).takeIf { it > 0 } ?: 1.0
                fun y(v: Double) = (1f - ((v - lo) / span).toFloat()) * size.height * 0.84f + size.height * 0.08f
                for (i in 1 until s.size) drawLine(c, Offset(x(s[i - 1].medidoEm), y(s[i - 1].valor)), Offset(x(s[i].medidoEm), y(s[i].valor)), 2.dp.toPx(), cap = StrokeCap.Round)
                s.forEach { drawCircle(c, 3.dp.toPx(), Offset(x(it.medidoEm), y(it.valor))) }
            }
        }
        Spacer(Modifier.height(6.dp))
        series.forEach { (p, s) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Legend(cor[p] ?: Luluco.Cream, "")
                Text(if (s.isEmpty()) "${Params.label(p)}: sem medições" else "${Params.label(p)}: ${Params.format(p, s.minOf { it.valor })} a ${Params.format(p, s.maxOf { it.valor })} ${Params.unit(p)}", fontSize = 13.sp, color = Luluco.TextSoft)
            }
        }
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(14.dp)) { drawRect(color) }
        Spacer(Modifier.width(6.dp))
        Muted(text)
    }
}

@Composable
private fun Chart(p: String, pts: List<WaterTest>, range: ParamRange?, trend: Trend?, tpas: List<Long>, doses: List<Long>, from: Long, to: Long, now: Long) {
    var lo = pts.minOf { it.valor }
    var hi = pts.maxOf { it.valor }
    if (range != null) {
        lo = minOf(lo, range.idealMin)
        hi = maxOf(hi, range.idealMax)
    }
    val margin = ((hi - lo).takeIf { it > 0 } ?: maxOf(1e-3, hi * 0.05)) * 0.15
    lo -= margin
    hi += margin
    val line = Luluco.PoolText
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)

    Row(Modifier.fillMaxWidth().height(170.dp).padding(top = 8.dp)) {
        Column(Modifier.width(52.dp).fillMaxHeight().padding(bottom = 14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Muted(num(hi, Params.decimals(p)))
            Muted(num((hi + lo) / 2, Params.decimals(p)))
            Muted(num(lo, Params.decimals(p)))
        }
        Column(Modifier.weight(1f)) {
            Canvas(Modifier.fillMaxWidth().weight(1f)) {
                fun x(t: Long) = ((t - from).toDouble() / (to - from)).toFloat() * size.width
                fun y(v: Double) = (1f - ((v - lo) / (hi - lo)).toFloat()) * size.height
                // faixa ideal
                if (range != null) {
                    drawRect(Luluco.ZoneIdeal.copy(alpha = 0.10f), topLeft = Offset(0f, y(range.idealMax)), size = Size(size.width, y(range.idealMin) - y(range.idealMax)))
                    // bordas marcadas: sem elas o bloco se confunde com o fundo do card
                    listOf(range.idealMax, range.idealMin).forEach { v ->
                        drawLine(Luluco.ZoneIdeal.copy(alpha = 0.45f), Offset(0f, y(v)), Offset(size.width, y(v)), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))
                    }
                }
                // moldura e "agora"
                drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                drawLine(grid, Offset(x(now), 0f), Offset(x(now), size.height), 1.dp.toPx())
                // TPAs: linha vertical; doses: tracinho no eixo
                tpas.forEach { drawLine(Luluco.Pool.copy(alpha = 0.7f), Offset(x(it), 0f), Offset(x(it), size.height), 1.5.dp.toPx()) }
                doses.forEach { drawLine(Luluco.Cream.copy(alpha = 0.6f), Offset(x(it), size.height - 7.dp.toPx()), Offset(x(it), size.height), 1.5.dp.toPx()) }
                // Medicoes reais, ligadas por curva suave com preenchimento em degrade embaixo.
                //
                // A curva passa exatamente por cada medicao: ela so chega na proxima em curva em
                // vez de em bico. Uma curva que "corrigisse" o caminho para ficar bonita estaria
                // inventando medicao que nao houve.
                val solid = pts.filter { !it.baixaConfianca }
                val xy = solid.map { Offset(x(it.medidoEm), y(it.valor)) }
                if (xy.size >= 2) {
                    val caminho = curvaSuave(xy)
                    val sob = androidx.compose.ui.graphics.Path().apply {
                        addPath(caminho)
                        lineTo(xy.last().x, size.height)
                        lineTo(xy.first().x, size.height)
                        close()
                    }
                    drawPath(sob, Brush.verticalGradient(listOf(line.copy(alpha = 0.26f), line.copy(alpha = 0f)), startY = 0f, endY = size.height))
                    drawPath(caminho, line, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                }
                pts.forEach {
                    val c = Offset(x(it.medidoEm), y(it.valor))
                    // cada ponto na cor da zona em que caiu, e baixa confianca vazado
                    val cor = if (range != null) zoneColor(it.valor, range) else line
                    if (it.baixaConfianca) drawCircle(cor, 4.dp.toPx(), c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx())) else drawCircle(cor, 4.dp.toPx(), c)
                }
                // projecao tracejada a partir de agora
                if (trend != null) {
                    val endDays = (to - now).toDouble() / DAY_MS
                    val v0 = trend.intercept.coerceIn(lo, hi)
                    val v1 = (trend.intercept + trend.slopePerDay * endDays).coerceIn(lo, hi)
                    drawLine(Luluco.Coral, Offset(x(now), y(v0)), Offset(x(to), y(v1)), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
                }
            }
            Row(Modifier.fillMaxWidth().height(14.dp)) {
                Muted(fmtDay(from).take(5), Modifier.weight(1f))
                Muted("hoje + projeção")
            }
            if (range != null) {
                Spacer(Modifier.height(12.dp))
                FaixasBar(p, pts, range, from, to)
            }
        }
    }
}

/**
 * Curva suave por entre os pontos (Catmull-Rom virada em Bezier), com tensao baixa.
 * Passa exatamente por cada ponto; a tensao baixa evita que ela estufe entre dois pontos distantes.
 */
private fun curvaSuave(pts: List<Offset>): androidx.compose.ui.graphics.Path {
    val path = androidx.compose.ui.graphics.Path()
    if (pts.isEmpty()) return path
    path.moveTo(pts[0].x, pts[0].y)
    if (pts.size == 1) return path
    val t = 0.2f
    for (i in 0 until pts.size - 1) {
        val p0 = pts.getOrElse(i - 1) { pts[i] }
        val p1 = pts[i]
        val p2 = pts[i + 1]
        val p3 = pts.getOrElse(i + 2) { p2 }
        path.cubicTo(
            p1.x + (p2.x - p0.x) * t, p1.y + (p2.y - p0.y) * t,
            p2.x - (p3.x - p1.x) * t, p2.y - (p3.y - p1.y) * t,
            p2.x, p2.y,
        )
    }
    return path
}

/**
 * Leituras por faixa: barra empilhada com a legenda ao lado.
 *
 * Diz quanto das medicoes do periodo caiu em cada zona. Conta LEITURA, nao tempo: a medicao aqui e
 * de dois em dois dias, e dizer "tempo" obrigaria a inventar o que houve entre uma e outra.
 */
@Composable
private fun FaixasBar(param: String, pts: List<WaterTest>, range: ParamRange, from: Long, to: Long) {
    val f = remember(pts, range, from, to) { faixasDe(pts, param, range, from, to) }
    if (f.total == 0) {
        Muted("Sem leituras confiáveis no período.")
        return
    }
    Eyebrow("Leituras por faixa")
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        // a barra segue a mesma ordem da legenda ao lado: senao o olho inverte a leitura
        Column(Modifier.width(10.dp).height(96.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.05f))) {
            listOf(Luluco.ZoneIdeal to f.ideal, Luluco.ZoneWarn to f.atencao, Luluco.ZoneCrit to f.critico).forEach { (cor, n) ->
                if (n > 0) Box(Modifier.fillMaxWidth().weight(n.toFloat()).background(cor))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            val limites = listOf(
                Triple(Luluco.ZoneIdeal, f.ideal, "na faixa" to "${Params.format(param, range.idealMin)} a ${Params.format(param, range.idealMax)}"),
                Triple(Luluco.ZoneWarn, f.atencao, "atenção" to "${Params.format(param, range.alertaMin)} a ${Params.format(param, range.alertaMax)}"),
                Triple(Luluco.ZoneCrit, f.critico, "crítico" to "fora de ${Params.format(param, range.alertaMin)} a ${Params.format(param, range.alertaMax)}"),
            )
            limites.forEach { (cor, n, txt) ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Box(Modifier.padding(top = 5.dp).size(7.dp).clip(CircleShape).background(cor))
                    Column {
                        Text("${txt.first} · ${f.pct(n)}%", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = Luluco.Cream)
                        Text("$n de ${f.total} leituras · ${txt.second}", fontSize = 11.5.sp, color = Luluco.TextFaint)
                    }
                }
            }
        }
    }
}
