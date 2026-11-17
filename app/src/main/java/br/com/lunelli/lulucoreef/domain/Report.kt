package br.com.lunelli.lulucoreef.domain

import br.com.lunelli.lulucoreef.data.Dosing
import br.com.lunelli.lulucoreef.data.Event
import br.com.lunelli.lulucoreef.data.HealthCase
import br.com.lunelli.lulucoreef.data.Product
import br.com.lunelli.lulucoreef.data.Routine
import br.com.lunelli.lulucoreef.data.RoutineLog
import br.com.lunelli.lulucoreef.data.TankConfig
import br.com.lunelli.lulucoreef.data.WaterChange
import br.com.lunelli.lulucoreef.data.WaterTest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

enum class ReportSection(val label: String) {
    RESUMO("Resumo"), TESTES("Testes"), CONSUMO("Consumo"), TPAS("TPAs"), PRODUTOS("Produtos usados"), ROTINAS("Rotinas e manutenção"), NOTAS("Notas e saúde")
}

class ReportData(
    val tank: TankConfig?, val tests: List<WaterTest>, val changes: List<WaterChange>, val dosings: List<Dosing>,
    val products: List<Product>, val routines: List<Routine>, val logs: List<RoutineLog>, val events: List<Event>, val cases: List<HealthCase>,
)

/**
 * Relatorio em texto simples do periodo [from, to], pronto para e-mail. So usa o que esta registrado.
 * Estatisticas e consumo ignoram leituras de baixa confianca (que aparecem marcadas na lista).
 */
fun buildReport(d: ReportData, from: Long, to: Long, now: Long, zone: ZoneId, sections: Set<ReportSection> = ReportSection.values().toSet()): String {
    val dt = DateTimeFormatter.ofPattern("dd/MM HH:mm")
    val day = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    fun f(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).format(dt)
    fun fd(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).format(day)
    val tests = d.tests.filter { it.medidoEm in from..to }.sortedBy { it.medidoEm }
    val changes = d.changes.filter { it.feitaEm in from..to }.sortedBy { it.feitaEm }
    val dosings = d.dosings.filter { it.dosadoEm in from..to }.sortedBy { it.dosadoEm }
    val events = d.events.filter { it.ocorridoEm in from..to }.sortedBy { it.ocorridoEm }
    val names = d.products.associate { it.id to it.nome }
    val sb = StringBuilder()
    fun title(t: String) { sb.append('\n').append(t.uppercase()).append('\n').append("-".repeat(t.length)).append('\n') }

    sb.append("LULUCO REEF - RELATÓRIO\n")
    sb.append("Período: ${fd(from)} a ${fd(minOf(to, now))}\n")
    d.tank?.let { sb.append("${it.nome}, ${numShort(it.volumeDosagemL)} L, sal ${it.salAtual ?: "não informado"}\n") }

    if (ReportSection.RESUMO in sections) {
        title("Resumo")
        val lastTpa = d.changes.filter { it.feitaEm <= now }.maxByOrNull { it.feitaEm }
        sb.append(if (lastTpa == null) "Última TPA: nenhuma registrada\n" else "Última TPA: ${f(lastTpa.feitaEm)}, ${numShort(lastTpa.litros)} L (há ${(now - lastTpa.feitaEm) / DAY_MS} dias)\n")
        sb.append("Testes no período: ${tests.size}\n")
        sb.append("TPAs no período: ${changes.size} (${numShort(changes.sumOf { it.litros })} L no total)\n")
        sb.append("Doses registradas no período: ${dosings.size}\n")
        Params.ALL.forEach { p ->
            d.tests.filter { it.parametro == p && !it.baixaConfianca && it.medidoEm <= now }.maxByOrNull { it.medidoEm }?.let {
                sb.append("Último ${Params.label(p)}: ${Params.format(p, it.valor)} ${it.unidade} em ${f(it.medidoEm)}\n")
            }
        }
    }

    if (ReportSection.TESTES in sections) {
        title("Testes")
        if (tests.isEmpty()) sb.append("Nenhum teste no período.\n")
        Params.ALL.forEach { p ->
            val list = tests.filter { it.parametro == p }
            if (list.isEmpty()) return@forEach
            val good = list.filter { !it.baixaConfianca }
            sb.append("\n${Params.label(p)} (${list.first().unidade}): ${list.size} medições")
            if (good.isNotEmpty()) sb.append(", mín. ${Params.format(p, good.minOf { it.valor })}, máx. ${Params.format(p, good.maxOf { it.valor })}, média ${Params.format(p, roundTo(good.map { it.valor }.average(), Params.decimals(p) + 1))}")
            sb.append('\n')
            list.forEach { t ->
                sb.append("  ${f(t.medidoEm)}  ${Params.format(p, t.valor)}")
                if (t.baixaConfianca) sb.append("  (baixa confiança)")
                t.observacao?.takeIf { it.isNotBlank() }?.let { sb.append("  - $it") }
                sb.append('\n')
            }
        }
    }

    if (ReportSection.CONSUMO in sections) {
        title("Consumo e tendência")
        var any = false
        listOf("kh", "ca", "mg").forEach { p ->
            val good = tests.filter { it.parametro == p && !it.baixaConfianca }
            if (good.size < 2) return@forEach
            val a = good.first()
            val b = good.last()
            val days = (b.medidoEm - a.medidoEm).toDouble() / DAY_MS
            if (days < 0.5) return@forEach
            any = true
            val delta = b.valor - a.valor
            val perDay = delta / days
            val dec = Params.decimals(p)
            sb.append("${Params.label(p)}: de ${Params.format(p, a.valor)} para ${Params.format(p, b.valor)} em ${numShort(roundTo(days, 1))} dias ")
            sb.append("(${if (delta >= 0) "+" else "-"}${num(abs(perDay), dec + 2)} por dia, ${if (delta >= 0) "+" else "-"}${num(abs(perDay) * 7, dec + 1)} por semana)\n")
        }
        if (any) sb.append("Variação líquida entre a primeira e a última leitura confiável do período. Já inclui o que as TPAs e as doses repuseram, então não é o consumo puro dos corais.\n")
        else sb.append("Faltam leituras confiáveis no período para estimar (pelo menos 2 por parâmetro).\n")
    }

    if (ReportSection.TPAS in sections) {
        title("TPAs")
        if (changes.isEmpty()) sb.append("Nenhuma TPA no período.\n")
        changes.forEach { c ->
            sb.append("  ${f(c.feitaEm)}  ${numShort(c.litros)} L")
            listOfNotNull(c.sal, c.salinidadeNovaAgua?.let { "água nova a ${Params.format("salinidade", it)}" }, c.metodo, c.observacao).filter { it.isNotBlank() }.forEach { sb.append(", $it") }
            sb.append('\n')
        }
    }

    if (ReportSection.PRODUTOS in sections) {
        title("Produtos usados")
        if (dosings.isEmpty()) sb.append("Nenhuma dose registrada no período.\n")
        dosings.groupBy { it.productId }.entries.sortedByDescending { it.value.size }.forEach { (id, list) ->
            val unit = list.firstNotNullOfOrNull { it.unidade }
            val total = list.sumOf { it.quantidade ?: 0.0 }
            sb.append("  ${names[id] ?: "Produto"}: ${list.size} vez${if (list.size == 1) "" else "es"}")
            if (total > 0) sb.append(", total ${numShort(roundTo(total, 2))} ${unit ?: ""}".trimEnd())
            sb.append(", última em ${f(list.last().dosadoEm)}\n")
        }
    }

    if (ReportSection.ROTINAS in sections) {
        title("Rotinas e manutenção")
        val (done, expected) = complianceBetween(d.routines, d.logs, from, minOf(to, now), zone)
        sb.append("Cumprimento: $done de $expected previstas")
        if (expected > 0) sb.append(" (${done * 100 / expected}%)")
        sb.append('\n')
        val byId = d.routines.associateBy { it.id }
        val maint = d.logs.filter { it.deletedAt == null && it.status == "feito" && (it.feitoEm ?: it.previstoPara) in from..to && byId[it.routineId]?.tipo.let { t -> t != null && t != "dosagem" } }
            .sortedBy { it.feitoEm ?: it.previstoPara }
        if (maint.isEmpty()) sb.append("Nenhuma manutenção marcada como feita no período.\n")
        maint.forEach { sb.append("  ${f(it.feitoEm ?: it.previstoPara)}  ${byId[it.routineId]?.titulo}\n") }
        val skipped = d.logs.count { it.deletedAt == null && it.status == "pulado" && it.previstoPara in from..to }
        if (skipped > 0) sb.append("Puladas: $skipped\n")
    }

    if (ReportSection.NOTAS in sections) {
        title("Notas e saúde")
        if (events.isEmpty()) sb.append("Nenhuma nota no período.\n")
        events.forEach { sb.append("  ${f(it.ocorridoEm)}  ${it.texto}\n") }
        val open = d.cases.filter { it.fechadoEm == null && it.deletedAt == null }
        if (open.isNotEmpty()) {
            sb.append("Casos de saúde abertos:\n")
            open.forEach { sb.append("  ${it.titulo} (${it.tipo}), aberto em ${fd(it.abertoEm)}\n") }
        }
    }

    sb.append("\nGerado pelo app Luluco Reef em ${f(now)}.\n")
    return sb.toString()
}

private fun roundTo(v: Double, decimals: Int): Double {
    val m = Math.pow(10.0, decimals.toDouble())
    return Math.round(v * m) / m
}

/** [feitas, previstas] entre duas datas. Pulada conta como nao cumprida. */
fun complianceBetween(routines: List<Routine>, logs: List<RoutineLog>, from: Long, to: Long, zone: ZoneId): Pair<Int, Int> {
    var date = from.toLocalDate(zone)
    val last = to.toLocalDate(zone)
    var expected = 0
    var done = 0
    val seen = HashSet<Pair<String, Long>>()
    var guard = 0
    while (!date.isAfter(last) && guard++ < 800) {
        for (o in occurrencesOn(routines, logs, date, zone)) {
            if (o.dueAt < from || o.dueAt > to || !seen.add(o.routine.id to o.dueAt)) continue
            expected++
            if (o.state == OccState.FEITO) done++
        }
        date = date.plusDays(1)
    }
    return done to expected
}
