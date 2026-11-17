package br.com.lunelli.lulucoreef.domain

import br.com.lunelli.lulucoreef.data.Routine
import br.com.lunelli.lulucoreef.data.RoutineLog
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class UpcomingItem(val routine: Routine, val dueAt: Long, val late: Boolean)

/**
 * Proximos passos: o que esta chegando de manutencao, TPA e testes (as dosagens do dia ficam no bloco
 * "Agora/Proxima"). Olha `days` dias a frente; rotinas por intervalo (ex.: carvao) entram pela data marcada,
 * mesmo que esteja mais longe.
 */
fun upcoming(routines: List<Routine>, logs: List<RoutineLog>, now: Long, zone: ZoneId, days: Int = 14, limit: Int = 6): List<UpcomingItem> {
    val list = routines.filter { it.ativa && it.deletedAt == null && it.tipo != "dosagem" }
    val out = LinkedHashMap<Pair<String, Long>, UpcomingItem>()
    val today = now.toLocalDate(zone)
    for (i in 0..days) {
        for (o in occurrencesOn(list.filter { !it.isInterval() }, logs, today.plusDays(i.toLong()), zone)) {
            if (o.open) out.getOrPut(o.routine.id to o.dueAt) { UpcomingItem(o.routine, o.dueAt, o.isLate(now)) }
        }
    }
    for (r in list.filter { it.isInterval() }) {
        val due = r.proximaEm ?: continue
        out.getOrPut(r.id to due) { UpcomingItem(r, due, due < now) }
    }
    return out.values.sortedBy { it.dueAt }.take(limit)
}

private val WEEKDAYS = listOf("Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado", "Domingo")

/** "Hoje, 19h", "Amanhã, 19h", "Quarta, 19h", "12/12 (em 25 dias)" ou "Atrasada desde 10/11". */
fun whenText(dueAt: Long, now: Long, zone: ZoneId): String {
    val due = Instant.ofEpochMilli(dueAt).atZone(zone)
    val hour = if (due.minute == 0) "${due.hour}h" else "${due.hour}h${due.minute.toString().padStart(2, '0')}"
    val diff = ChronoUnit.DAYS.between(now.toLocalDate(zone), due.toLocalDate())
    val ddmm = "${due.dayOfMonth.toString().padStart(2, '0')}/${due.monthValue.toString().padStart(2, '0')}"
    return when {
        diff < 0 -> "Atrasada desde $ddmm"
        diff == 0L -> if (dueAt < now) "Hoje, $hour (atrasada)" else "Hoje, $hour"
        diff == 1L -> "Amanhã, $hour"
        diff < 7 -> "${WEEKDAYS[due.dayOfWeek.value - 1]}, $hour"
        else -> "$ddmm (em $diff dias)"
    }
}
