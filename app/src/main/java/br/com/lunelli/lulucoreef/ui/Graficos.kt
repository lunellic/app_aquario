package br.com.lunelli.lulucoreef.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lunelli.lulucoreef.data.ParamRange
import br.com.lunelli.lulucoreef.data.WaterTest
import br.com.lunelli.lulucoreef.domain.DAY_MS
import br.com.lunelli.lulucoreef.domain.Params
import br.com.lunelli.lulucoreef.domain.Trend
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
        ChipRow(PERIODS, period) { period = it }
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
        Spacer(Modifier.height(8.dp))
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
            Muted(Params.format(p, hi))
            Muted(Params.format(p, (hi + lo) / 2))
            Muted(Params.format(p, lo))
        }
        Column(Modifier.weight(1f)) {
            Canvas(Modifier.fillMaxWidth().weight(1f)) {
                fun x(t: Long) = ((t - from).toDouble() / (to - from)).toFloat() * size.width
                fun y(v: Double) = (1f - ((v - lo) / (hi - lo)).toFloat()) * size.height
                // faixa ideal
                if (range != null) drawRect(Luluco.Ok.copy(alpha = 0.16f), topLeft = Offset(0f, y(range.idealMax)), size = Size(size.width, y(range.idealMin) - y(range.idealMax)))
                // moldura e "agora"
                drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                drawLine(grid, Offset(x(now), 0f), Offset(x(now), size.height), 1.dp.toPx())
                // TPAs: linha vertical; doses: tracinho no eixo
                tpas.forEach { drawLine(Luluco.Pool.copy(alpha = 0.7f), Offset(x(it), 0f), Offset(x(it), size.height), 1.5.dp.toPx()) }
                doses.forEach { drawLine(Luluco.Cream.copy(alpha = 0.6f), Offset(x(it), size.height - 7.dp.toPx()), Offset(x(it), size.height), 1.5.dp.toPx()) }
                // medicoes reais ligadas por linha
                val solid = pts.filter { !it.baixaConfianca }
                for (i in 1 until solid.size) {
                    drawLine(line, Offset(x(solid[i - 1].medidoEm), y(solid[i - 1].valor)), Offset(x(solid[i].medidoEm), y(solid[i].valor)), 2.dp.toPx(), cap = StrokeCap.Round)
                }
                pts.forEach {
                    val c = Offset(x(it.medidoEm), y(it.valor))
                    // baixa confianca: ponto vazado, fora da linha e da projecao
                    if (it.baixaConfianca) drawCircle(line, 4.dp.toPx(), c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx())) else drawCircle(line, 4.dp.toPx(), c)
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
        }
    }
}
