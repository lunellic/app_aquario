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

data class Occurrence(val routine: Routine, val dueAt: Long, val log: RoutineLog?) {
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

fun occurrencesOn(routines: List<Routine>, logs: List<RoutineLog>, date: LocalDate, zone: ZoneId): List<Occurrence> {
    val byKey = logs.filter { it.deletedAt == null }.groupBy { it.routineId to it.previstoPara }
    return routines.mapNotNull { r ->
        val due = dueOn(r, date, zone) ?: return@mapNotNull null
        Occurrence(r, due, byKey[r.id to due]?.maxByOrNull { it.updatedAt })
    }.sortedWith(compareBy({ it.dueAt }, { it.routine.titulo }))
}

/** Proxima ocorrencia estritamente depois de `after` (para o alarme). */
fun nextDueAfter(r: Routine, after: Long, zone: ZoneId): Long? {
    if (!r.ativa || r.deletedAt != null) return null
    if (r.isInterval()) return r.proximaEm?.takeIf { it > after }
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

fun formatDose(q: Double?, unit: String?): String {
    if (q == null) return "dose a definir"
    val u = unit ?: ""
    val frac = when {
        abs(q - 0.25) < 1e-6 -> "1/4"
        abs(q - 0.5) < 1e-6 -> "1/2"
        abs(q - 0.75) < 1e-6 -> "3/4"
        else -> null
    }
    return when (u) {
        "colher" -> if (frac != null) "$frac de colher" else "${numShort(q)} ${if (q > 1.0) "colheres" else "colher"}"
        "gotas" -> "${numShort(q)} ${if (abs(q - 1.0) < 1e-6) "gota" else "gotas"}"
        "", "outra" -> numShort(q)
        else -> "${numShort(q)} $u"
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
