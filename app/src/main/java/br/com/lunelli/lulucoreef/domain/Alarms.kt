package br.com.lunelli.lulucoreef.domain

import br.com.lunelli.lulucoreef.data.Alarm
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Conta do alarme, separada de quem toca o som (reminders/Clocks).
 *
 * Fica aqui, sem nada do Android, para o teste unitario rodar na maquina, sem emulador.
 */

/** Nome curto dos dias na ordem ISO (1 = segunda). */
val DIAS_CURTOS = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")

fun diasTexto(dias: List<Int>?): String {
    val d = dias.orEmpty().filter { it in 1..7 }.sorted()
    return when {
        d.isEmpty() -> "nenhum dia"
        d.size == 7 -> "todos os dias"
        d == listOf(1, 2, 3, 4, 5) -> "de segunda a sexta"
        d == listOf(6, 7) -> "fim de semana"
        else -> d.joinToString(", ") { DIAS_CURTOS[it - 1] }
    }
}

/**
 * Proximo toque depois de `after`, ou null se o alarme nao toca mais.
 * "unica" vale so no dia marcado; passado ele, nao toca de novo.
 */
fun nextRing(a: Alarm, after: Long, zone: ZoneId): Long? {
    if (!a.ativo) return null
    val time = LocalTime.of(a.hora.coerceIn(0, 23), a.minuto.coerceIn(0, 59))
    fun at(d: LocalDate) = d.atTime(time).atZone(zone).toInstant().toEpochMilli()
    val hoje = Instant.ofEpochMilli(after).atZone(zone).toLocalDate()
    return when (a.recorrencia) {
        "unica" -> {
            val d = a.diaUnico?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: hoje
            at(d).takeIf { it > after }
        }
        "diaria" -> at(hoje).takeIf { it > after } ?: at(hoje.plusDays(1))
        "dias_da_semana" -> {
            val dias = a.diasSemana.orEmpty().filter { it in 1..7 }
            if (dias.isEmpty()) return null
            (0..7).asSequence()
                .map { hoje.plusDays(it.toLong()) }
                .filter { it.dayOfWeek.value in dias }
                .map { at(it) }
                .firstOrNull { it > after }
        }
        else -> null
    }
}
