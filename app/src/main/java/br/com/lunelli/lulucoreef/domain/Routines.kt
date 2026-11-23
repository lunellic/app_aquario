package br.com.lunelli.lulucoreef.domain

import br.com.lunelli.lulucoreef.data.Product
import br.com.lunelli.lulucoreef.data.Routine
import br.com.lunelli.lulucoreef.data.RoutineLog
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

const val SNOOZE_MS = 30 * 60_000L

enum class OccState { PENDENTE, ADIADO, FEITO, PULADO }

/**
 * Uma ocorrencia da rotina. `carried` marca a pendencia arrastada de um dia anterior:
 * a rotina nao estava marcada para hoje, mas ficou em aberto e continua cobrando.
 */
data class Occurrence(val routine: Routine, val dueAt: Long, val log: RoutineLog?, val carried: Boolean = false) {
    val state: OccState
        get() = when (log?.status) {
            "feito" -> OccState.FEITO
            "pulado" -> OccState.PULADO
            "adiado" -> OccState.ADIADO
            else -> OccState.PENDENTE
        }

    /** Momento em que passa a cobrar: o horario previsto ou, se adiado, 30 min apos o ultimo adiamento. */
    val effectiveDueAt: Long get() = if (state == OccState.ADIADO) log!!.updatedAt + SNOOZE_MS else dueAt
    val open: Boolean get() = state == OccState.PENDENTE || state == OccState.ADIADO
    fun isDue(now: Long) = open && now >= effectiveDueAt
    fun isLate(now: Long, toleranceMs: Long = 0) = open && now - effectiveDueAt > toleranceMs
}

fun Routine.time(): LocalTime = try {
    LocalTime.parse(horario.take(5))
} catch (_: Exception) {
    LocalTime.of(20, 0)
}

fun Routine.isInterval() = recorrencia == "a_cada_n_dias" || recorrencia == "a_cada_n_semanas"

/** Atividade de uma vez so, com data marcada (como um compromisso de calendario). */
fun Routine.isOnce() = recorrencia == "unica"

/** Usa proximaEm como data: rotinas por intervalo e atividades de uma vez. */
fun Routine.isAnchored() = isInterval() || isOnce()
fun Routine.intervalDays(): Long = (intervaloN ?: 1).toLong() * (if (recorrencia == "a_cada_n_semanas") 7 else 1)

private fun at(date: LocalDate, time: LocalTime, zone: ZoneId) = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
fun Long.toLocalDate(zone: ZoneId): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

/**
 * Ocorrencia da rotina no dia, ou null.
 * Rotinas por intervalo usam proximaEm como ancora: vencem nessa data e continuam aparecendo
 * (atrasadas) ate serem feitas ou puladas, quando a ancora avanca. Sem ancora, nao sao agendadas.
 */
fun dueOn(r: Routine, date: LocalDate, zone: ZoneId): Long? {
    if (!r.ativa || r.deletedAt != null) return null
    return when (r.recorrencia) {
        "diaria" -> at(date, r.time(), zone)
        "dias_da_semana" -> if (r.diasSemana.orEmpty().contains(date.dayOfWeek.value)) at(date, r.time(), zone) else null
        else -> {
            val anchor = r.proximaEm ?: return null
            if (!anchor.toLocalDate(zone).isAfter(date)) anchor else null
        }
    }
}

/** Quantos dias para tras procurar pendencia em aberto de uma rotina por dias da semana. */
const val CARRY_DAYS = 30

/**
 * Ocorrencias de um dia.
 *
 * Com `now` informado e `date` de hoje em diante, arrasta tambem as pendencias dos dias anteriores:
 * uma rotina marcada para sexta que nao foi feita nem pulada continua cobrando no sabado, no domingo
 * e nos dias seguintes, ate ser resolvida. Sem isso ela simplesmente desaparecia no dia seguinte,
 * porque `dueOn` so devolve a ocorrencia no proprio dia da semana.
 *
 * Nao arrasta rotina diaria: a ocorrencia de hoje ja substitui a de ontem, e empilhar dose perdida
 * nao ajuda (nao da para dosar ontem). Rotinas por intervalo e de uma vez so nunca tiveram o
 * problema, porque a ancora `proximaEm` fica no passado e `dueOn` as devolve em todos os dias.
 */
fun occurrencesOn(routines: List<Routine>, logs: List<RoutineLog>, date: LocalDate, zone: ZoneId, now: Long? = null): List<Occurrence> {
    val byKey = logs.filter { it.deletedAt == null }.groupBy { it.routineId to it.previstoPara }
    val doDia = routines.mapNotNull { r ->
        val due = dueOn(r, date, zone) ?: return@mapNotNull null
        val o = Occurrence(r, due, byKey[r.id to due]?.maxByOrNull { it.updatedAt })
        // atividade de uma vez ja concluida (feita ou pulada): aparece so no dia em que foi resolvida
        if (r.isOnce() && !o.open && (o.log!!.feitoEm ?: o.log.updatedAt).toLocalDate(zone) != date) null else o
    }
    val arrastadas = if (now == null || date.isBefore(now.toLocalDate(zone))) emptyList()
    else carriedInto(routines, byKey, date, zone, doDia.map { it.routine.id }.toSet())
    return (arrastadas + doDia).sortedWith(compareBy({ it.dueAt }, { it.routine.titulo }))
}

/**
 * Pendencias de dias anteriores que ainda cobram em `date`. So rotinas por dias da semana, uma por
 * rotina (a ultima ainda aberta) e so quando a rotina nao tem ocorrencia no proprio dia: se ela
 * vence hoje de novo, a de hoje ja representa o servico, nao precisa listar as duas.
 *
 * Pega a mais recente, nao a mais antiga: se ele perdeu tres TPAs seguidas, fazer uma TPA o poe em
 * dia. Mostrar a de tres semanas atras faria a lista cobrar de novo a cada vez que ele resolvesse uma.
 */
private fun carriedInto(
    routines: List<Routine>,
    byKey: Map<Pair<String, Long>, List<RoutineLog>>,
    date: LocalDate,
    zone: ZoneId,
    jaNoDia: Set<String>,
): List<Occurrence> = routines.mapNotNull { r ->
    if (!r.ativa || r.deletedAt != null) return@mapNotNull null
    if (r.recorrencia != "dias_da_semana") return@mapNotNull null
    if (r.id in jaNoDia) return@mapNotNull null
    for (i in 1..CARRY_DAYS) {
        val d = date.minusDays(i.toLong())
        val due = dueOn(r, d, zone) ?: continue
        val log = byKey[r.id to due]?.maxByOrNull { it.updatedAt }
        val o = Occurrence(r, due, log, carried = true)
        if (o.open) return@mapNotNull o
    }
    null
}

/** Proxima ocorrencia estritamente depois de `after` (para o alarme). */
fun nextDueAfter(r: Routine, after: Long, zone: ZoneId): Long? {
    if (!r.ativa || r.deletedAt != null) return null
    if (r.isAnchored()) return r.proximaEm?.takeIf { it > after }
    var date = after.toLocalDate(zone)
    repeat(8) {
        val due = dueOn(r, date, zone)
        if (due != null && due > after) return due
        date = date.plusDays(1)
    }
    return null
}

/** Nova ancora de rotina por intervalo depois de feita (conta a partir de hoje) ou pulada (a partir da ancora). */
fun nextAnchor(r: Routine, done: Boolean, now: Long, zone: ZoneId): Long? {
    if (!r.isInterval()) return r.proximaEm
    val step = r.intervalDays()
    val today = now.toLocalDate(zone)
    var date = if (done) today.plusDays(step) else (r.proximaEm ?: now).toLocalDate(zone).plusDays(step)
    while (!date.isAfter(today)) date = date.plusDays(step)
    return at(date, r.time(), zone)
}

/** Previstas x feitas nos ultimos `days` dias (ate agora). Pulada conta como nao cumprida. */
fun compliance(routines: List<Routine>, logs: List<RoutineLog>, now: Long, zone: ZoneId, days: Int = 7): Pair<Int, Int> {
    val today = now.toLocalDate(zone)
    var expected = 0
    var done = 0
    val seen = HashSet<Pair<String, Long>>()
    for (i in 0 until days) {
        for (o in occurrencesOn(routines, logs, today.minusDays(i.toLong()), zone)) {
            if (o.dueAt > now || !seen.add(o.routine.id to o.dueAt)) continue
            expected++
            if (o.state == OccState.FEITO) done++
        }
    }
    return done to expected
}

fun doseFor(p: Product, volumeL: Double): Double? {
    val q = p.doseQuantidade ?: return null
    val base = p.doseBaseLitros
    return if (base != null && base > 0) q * volumeL / base else q
}

/**
 * Dose arredondada de forma pratica para medir na mao: colher em fracoes comuns ou de meia em meia,
 * gotas de meia em meia, g/ml com uma casa. "~" quando o valor exato foi arredondado.
 * O valor exato continua sendo o gravado em dosings.
 */
fun formatDose(q: Double?, unit: String?): String {
    if (q == null) return "dose a definir"
    fun approx(shown: Double) = if (abs(shown - q) > 0.005 * maxOf(q, 1e-9) + 1e-9) "~" else ""
    return when (unit ?: "") {
        "colher" -> {
            if (q < 0.875) {
                val (v, label) = listOf(0.25 to "1/4", 1.0 / 3 to "1/3", 0.5 to "1/2", 2.0 / 3 to "2/3", 0.75 to "3/4").minByOrNull { abs(it.first - q) }!!
                "${approx(v)}$label de colher"
            } else {
                val v = Math.round(q * 2) / 2.0
                "${approx(v)}${numShort(v)} ${if (v > 1.0) "colheres" else "colher"}"
            }
        }
        "gotas" -> {
            val v = maxOf(0.5, Math.round(q * 2) / 2.0)
            "${approx(v)}${numShort(v)} ${if (abs(v - 1.0) < 1e-6) "gota" else "gotas"}"
        }
        "g", "ml" -> {
            val v = if (q >= 10) Math.round(q).toDouble() else Math.round(q * 10) / 10.0
            "${approx(v)}${numShort(v)} $unit"
        }
        "", "outra" -> numShort(q)
        else -> "${numShort(q)} $unit"
    }
}

fun recurrenceText(r: Routine): String {
    val names = listOf("seg", "ter", "qua", "qui", "sex", "sáb", "dom")
    val h = r.horario.take(5)
    return when (r.recorrencia) {
        "diaria" -> "todos os dias, $h"
        "dias_da_semana" -> r.diasSemana.orEmpty().sorted().joinToString(", ") { names.getOrElse(it - 1) { "?" } } + ", $h"
        "a_cada_n_dias" -> "a cada ${r.intervaloN ?: 1} dias, $h"
        "a_cada_n_semanas" -> "a cada ${r.intervaloN ?: 1} semanas, $h"
        "unica" -> "uma vez só, $h"
        else -> h
    }
}

/** Texto de uma linha para o aviso agrupado: "20h: Goni Supreme 1/4 de colher, Amino Coral 2 gotas". */
fun reminderText(items: List<Occurrence>, products: Map<String, Product>, volumeL: Double, zone: ZoneId): String {
    if (items.isEmpty()) return ""
    val t = Instant.ofEpochMilli(items.first().dueAt).atZone(zone).toLocalTime()
    val hour = if (t.minute == 0) "${t.hour}h" else "${t.hour}h${t.minute.toString().padStart(2, '0')}"
    return "$hour: " + items.joinToString(", ") { o ->
        val p = o.routine.productId?.let { products[it] }
        if (o.routine.tipo == "dosagem" && p != null) "${o.routine.titulo} ${formatDose(doseFor(p, volumeL), p.doseUnidade)}" else o.routine.titulo
    }
}

/**
 * Agenda de um dia para o calendario: o que esta marcado naquela data.
 * Diferente de occurrencesOn, rotinas com data (intervalo ou uma vez) aparecem so no dia marcado.
 */
fun agendaOn(routines: List<Routine>, logs: List<RoutineLog>, date: LocalDate, zone: ZoneId): List<Occurrence> {
    val byKey = logs.filter { it.deletedAt == null }.groupBy { it.routineId to it.previstoPara }
    return routines.mapNotNull { r ->
        if (!r.ativa || r.deletedAt != null) return@mapNotNull null
        val due = if (r.isAnchored()) r.proximaEm?.takeIf { it.toLocalDate(zone) == date } else dueOn(r, date, zone)
        due?.let { Occurrence(r, it, byKey[r.id to it]?.maxByOrNull { l -> l.updatedAt }) }
    }.sortedWith(compareBy({ it.dueAt }, { it.routine.titulo }))
}
