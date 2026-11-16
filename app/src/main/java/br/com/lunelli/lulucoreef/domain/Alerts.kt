package br.com.lunelli.lulucoreef.domain

import br.com.lunelli.lulucoreef.data.ParamRange
import br.com.lunelli.lulucoreef.data.WaterChange
import br.com.lunelli.lulucoreef.data.WaterTest
import kotlin.math.abs

/** Alerta de regra fixa (spec 8): o fato e uma acao segura. Nunca sugere suplemento separado de Ca, KH ou Mg. */
data class Alert(val severity: Status, val key: String, val fato: String, val acao: String)

object SafeActions {
    const val RETEST = "Reteste para confirmar antes de mexer em qualquer coisa."
    const val TPA = "Faça uma TPA de 10-12 L com água bem calibrada (salinidade e temperatura iguais às do aquário)."

    fun forParam(p: String, low: Boolean): String = when (p) {
        "kh" -> if (low) "$RETEST Se confirmar, revise a dose do All-For-Reef e suba devagar, sem saltos."
        else "$RETEST Se confirmar, reduza a dose do All-For-Reef e deixe baixar aos poucos; uma TPA de 10-12 L ajuda."
        "ca", "mg" -> if (low) "$RETEST Se confirmar, revise a dose do All-For-Reef e mantenha as TPAs semanais; a correção é gradual."
        else "$RETEST Se confirmar, reduza a dose do All-For-Reef e deixe as TPAs trazerem o valor de volta aos poucos."
        "salinidade" -> if (low) "Confira a calibração do instrumento. Se confirmar, suba devagar (no máximo 0,001 por dia) repondo parte da evaporação com água salgada."
        else "Confira a calibração do instrumento. Se confirmar, baixe devagar (no máximo 0,001 por dia) repondo com água de osmose."
        "temperatura" -> if (low) "Confira o STC-1000 e o aquecedor. Suba devagar, no máximo 1 °C por dia."
        else "Confira o STC-1000 e a ventilação. Baixe devagar, no máximo 1 °C por dia."
        "no3", "po4" -> if (low) "$RETEST Nutriente baixo demais também estressa os corais: revise a alimentação e evite cortes bruscos."
        else "$RETEST Se confirmar, faça uma TPA de 10-12 L com água bem calibrada e revise a alimentação. Corrija devagar."
        else -> RETEST
    }
}

fun paramAlerts(tests: List<WaterTest>, ranges: Map<String, ParamRange>, now: Long): List<Alert> {
    val out = ArrayList<Alert>()
    for (p in Params.ALL) {
        val last = tests.latestOf(p)
        val main = p in Params.MAIN
        if (last == null || now - last.medidoEm > 7 * DAY_MS) {
            if (main) {
                val fato = if (last == null) "${Params.label(p)} sem nenhuma medição." else "${Params.label(p)} sem medição há ${(now - last.medidoEm) / DAY_MS} dias."
                out += Alert(Status.ATENCAO, "stale_$p", fato, "Faça o teste de ${Params.label(p)} quando puder.")
            }
            if (last == null) continue
        }
        // NO3 e PO4 so contam com medicao nos ultimos 14 dias.
        if (!main && now - last.medidoEm > 14 * DAY_MS) continue
        val r = ranges[p] ?: continue
        val st = statusOf(last.valor, r)
        if (st == Status.IDEAL) continue
        val low = last.valor < r.idealMin
        val faixa = "${Params.format(p, r.idealMin)} a ${Params.format(p, r.idealMax)} ${r.unidade}"
        val fato = "${Params.label(p)} em ${Params.format(p, last.valor)} ${r.unidade}, ${if (low) "abaixo" else "acima"} da faixa ideal ($faixa)."
        out += Alert(st, "range_$p", fato, SafeActions.forParam(p, low))
    }
    khSwing(tests)?.let { out += it }
    return out
}

/** KH variando mais de 1,0 dKH em 24 h, pelo tempo real entre as duas ultimas medicoes. */
fun khSwing(tests: List<WaterTest>): Alert? {
    val kh = tests.filter { it.parametro == "kh" }.sortedByDescending { it.medidoEm }
    if (kh.size < 2) return null
    val dt = kh[0].medidoEm - kh[1].medidoEm
    if (dt < HOUR_MS) return null // reteste em seguida nao e variacao do aquario
    val delta = kh[0].valor - kh[1].valor
    val per24h = abs(delta) / maxOf(dt.toDouble() / DAY_MS, 1.0)
    if (per24h <= 1.0) return null
    val hours = dt / HOUR_MS
    return Alert(
        Status.CRITICO, "kh_swing",
        "KH variou ${num(abs(delta), 1)} dKH em ${if (hours < 48) "$hours h" else "${hours / 24} dias"} (de ${num(kh[1].valor, 1)} para ${num(kh[0].valor, 1)}).",
        "${SafeActions.RETEST} Evite novas correções hoje e mantenha a dose do All-For-Reef estável.",
    )
}

fun tpaAlert(changes: List<WaterChange>, now: Long): Alert? {
    val last = changes.maxByOrNull { it.feitaEm } ?: return Alert(Status.ATENCAO, "tpa", "Nenhuma TPA registrada.", SafeActions.TPA)
    val days = (now - last.feitaEm) / DAY_MS
    return if (days > 9) Alert(Status.ATENCAO, "tpa", "Última TPA há $days dias.", SafeActions.TPA) else null
}

fun lateRoutineAlerts(today: List<Occurrence>, now: Long): List<Alert> =
    today.filter { it.isLate(now, 2 * HOUR_MS) }.map {
        Alert(Status.ATENCAO, "routine_${it.routine.id}", "Rotina \"${it.routine.titulo}\" atrasada há ${(now - it.effectiveDueAt) / HOUR_MS} h.", "Marque como feita, adie ou pule no Painel.")
    }
