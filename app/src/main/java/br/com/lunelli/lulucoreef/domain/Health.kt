package br.com.lunelli.lulucoreef.domain

import br.com.lunelli.lulucoreef.data.HealthCase
import br.com.lunelli.lulucoreef.data.ParamRange
import br.com.lunelli.lulucoreef.data.Routine
import br.com.lunelli.lulucoreef.data.RoutineLog
import br.com.lunelli.lulucoreef.data.WaterChange
import br.com.lunelli.lulucoreef.data.WaterTest
import br.com.lunelli.lulucoreef.data.reliable
import java.time.ZoneId
import kotlin.math.roundToInt

data class ScoreItem(val label: String, val points: Double)

/** score nulo = sem dados suficientes; `missing` diz o que falta registrar. */
data class HealthScore(val score: Int?, val items: List<ScoreItem>, val missing: List<String>)

/** Nota de saude 0-100 (spec 10): comeca em 100 e desconta por regra fixa e explicavel. */
fun healthScore(
    allTests: List<WaterTest>, ranges: Map<String, ParamRange>, changes: List<WaterChange>,
    routines: List<Routine>, logs: List<RoutineLog>, cases: List<HealthCase>, now: Long, zone: ZoneId,
): HealthScore {
    val tests = allTests.reliable()
    val items = ArrayList<ScoreItem>()
    val missing = ArrayList<String>()

    fun param(p: String, factor: Double, staleCounts: Boolean) {
        val last = tests.latestOf(p)
        if (last == null) {
            if (staleCounts) missing += "medir ${Params.label(p)}"
            return
        }
        val age = now - last.medidoEm
        if (age > 7 * DAY_MS) {
            items += ScoreItem("${Params.label(p)} sem medição há mais de 7 dias", -5 * factor)
            return
        }
        val r = ranges[p] ?: return
        when (statusOf(last.valor, r)) {
            Status.ATENCAO -> items += ScoreItem("${Params.label(p)} em atenção (${Params.format(p, last.valor)})", -8 * factor)
            Status.CRITICO -> items += ScoreItem("${Params.label(p)} crítico (${Params.format(p, last.valor)})", -15 * factor)
            else -> {}
        }
    }
    Params.MAIN.forEach { param(it, 1.0, true) }
    // NO3 e PO4 so contam com medicao nos ultimos 14 dias, pela metade.
    Params.OPTIONAL.forEach { p ->
        val last = tests.latestOf(p)
        if (last != null && now - last.medidoEm <= 14 * DAY_MS) param(p, 0.5, false)
    }
    // Temperatura entra quando houver sensor (versao futura).

    val lastTpa = changes.maxByOrNull { it.feitaEm }
    if (lastTpa == null) {
        missing += "registrar uma TPA"
    } else {
        val days = (now - lastTpa.feitaEm) / DAY_MS
        if (days > 16) items += ScoreItem("TPA há $days dias (mais de 16)", -20.0)
        else if (days > 9) items += ScoreItem("TPA há $days dias (mais de 9)", -10.0)
    }

    val (done, expected) = compliance(routines, logs, now, zone)
    if (expected > 0 && done < expected) {
        val frac = 1.0 - done.toDouble() / expected
        items += ScoreItem("Rotinas dos últimos 7 dias: $done de $expected cumpridas", -15 * frac)
    }

    val open = cases.count { it.fechadoEm == null && it.deletedAt == null }
    if (open > 0) items += ScoreItem("$open caso${if (open > 1) "s" else ""} de saúde aberto${if (open > 1) "s" else ""}", -minOf(15.0, 5.0 * open))

    if (missing.isNotEmpty()) return HealthScore(null, items, missing)
    val score = (100 + items.sumOf { it.points }).roundToInt().coerceIn(0, 100)
    return HealthScore(score, items, missing)
}
