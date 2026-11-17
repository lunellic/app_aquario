package br.com.lunelli.lulucoreef

import br.com.lunelli.lulucoreef.data.ParamRange
import br.com.lunelli.lulucoreef.data.Product
import br.com.lunelli.lulucoreef.data.Routine
import br.com.lunelli.lulucoreef.data.RoutineLog
import br.com.lunelli.lulucoreef.data.Settings
import br.com.lunelli.lulucoreef.data.WaterChange
import br.com.lunelli.lulucoreef.data.WaterTest
import br.com.lunelli.lulucoreef.domain.DAY_MS
import br.com.lunelli.lulucoreef.domain.HOUR_MS
import br.com.lunelli.lulucoreef.domain.OccState
import br.com.lunelli.lulucoreef.domain.Occurrence
import br.com.lunelli.lulucoreef.domain.Status
import br.com.lunelli.lulucoreef.domain.compliance
import br.com.lunelli.lulucoreef.domain.doseFor
import br.com.lunelli.lulucoreef.domain.formatDose
import br.com.lunelli.lulucoreef.domain.khSwing
import br.com.lunelli.lulucoreef.domain.nextAnchor
import br.com.lunelli.lulucoreef.domain.nextDueAfter
import br.com.lunelli.lulucoreef.domain.occurrencesOn
import br.com.lunelli.lulucoreef.domain.paramAlerts
import br.com.lunelli.lulucoreef.domain.parseNum
import br.com.lunelli.lulucoreef.domain.reminderText
import br.com.lunelli.lulucoreef.domain.statusOf
import br.com.lunelli.lulucoreef.domain.tpaAlert
import br.com.lunelli.lulucoreef.domain.trendOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class RulesTest {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private fun ms(date: String, time: String = "12:00") = java.time.LocalDateTime.parse("${date}T$time").atZone(zone).toInstant().toEpochMilli()
    private val kh = ParamRange("kh", 7.5, 9.0, 7.0, 10.0, "dKH")
    private fun test(p: String, v: Double, at: Long) = WaterTest(at, p, v, "x")

    @Test fun statusPorFaixa() {
        assertEquals(Status.IDEAL, statusOf(8.0, kh))
        assertEquals(Status.IDEAL, statusOf(7.5, kh))
        assertEquals(Status.ATENCAO, statusOf(7.2, kh))
        assertEquals(Status.ATENCAO, statusOf(9.5, kh))
        assertEquals(Status.CRITICO, statusOf(6.9, kh))
        assertEquals(Status.CRITICO, statusOf(10.1, kh))
    }

    @Test fun casasExtrasSoQuandoPrecisa() {
        assertEquals("1,025", br.com.lunelli.lulucoreef.domain.Params.format("salinidade", 1.025))
        assertEquals("1,0225", br.com.lunelli.lulucoreef.domain.Params.format("salinidade", 1.0225))
        assertEquals("7,85", br.com.lunelli.lulucoreef.domain.Params.format("kh", 7.85))
        assertEquals("7,7", br.com.lunelli.lulucoreef.domain.Params.format("kh", 7.7))
        assertEquals("500", br.com.lunelli.lulucoreef.domain.Params.format("ca", 500.0))
    }

    @Test fun leituraDeBaixaConfiancaFicaForaDasRegras() {
        val now = ms("2026-11-16")
        val ranges = mapOf("kh" to kh)
        val ruim = test("kh", 6.0, now).copy(baixaConfianca = true)
        val boa = test("kh", 8.0, now - DAY_MS)
        val alerts = br.com.lunelli.lulucoreef.domain.allAlerts(listOf(ruim, boa), ranges, listOf(WaterChange(now, 10.0)), emptyList(), emptyList(), emptyList(), now)
        assertFalse(alerts.any { it.key == "range_kh" || it.key == "kh_swing" })
        // projecao ignora a leitura vazada
        val serie = listOf(test("kh", 8.4, now - 4 * DAY_MS), test("kh", 8.2, now - 2 * DAY_MS), test("kh", 8.0, now), test("kh", 12.0, now - DAY_MS).copy(baixaConfianca = true))
        assertEquals(-0.1, trendOf(serie, "kh", kh, now)!!.slopePerDay, 1e-6)
    }

    @Test fun numeroComVirgula() {
        assertEquals(1.025, parseNum("1,025")!!, 1e-9)
        assertEquals(8.2, parseNum(" 8.2 ")!!, 1e-9)
        assertNull(parseNum("abc"))
    }

    @Test fun projecaoCaindoSaiDaFaixa() {
        val now = ms("2026-11-16")
        // cai 0,1 dKH por dia: 8,4 / 8,2 / 8,0 hoje -> chega a 7,5 em ~5 dias
        val tests = listOf(test("kh", 8.4, now - 4 * DAY_MS), test("kh", 8.2, now - 2 * DAY_MS), test("kh", 8.0, now))
        val t = trendOf(tests, "kh", kh, now)!!
        assertEquals(-0.1, t.slopePerDay, 1e-6)
        assertEquals(5.0, t.daysToExit!!, 0.01)
    }

    @Test fun projecaoExigeTresMedicoesEHorizonteDe14Dias() {
        val now = ms("2026-11-16")
        assertNull(trendOf(listOf(test("kh", 8.4, now - DAY_MS), test("kh", 8.0, now)), "kh", kh, now))
        val lento = listOf(test("kh", 8.02, now - 4 * DAY_MS), test("kh", 8.01, now - 2 * DAY_MS), test("kh", 8.0, now))
        assertNull(trendOf(lento, "kh", kh, now)!!.daysToExit)
    }

    @Test fun variacaoDeKhUsaTempoReal() {
        val now = ms("2026-11-16")
        assertNotNull(khSwing(listOf(test("kh", 8.0, now - 12 * HOUR_MS), test("kh", 9.2, now))))
        // 1,2 dKH em 3 dias = 0,4 por 24 h: sem alerta
        assertNull(khSwing(listOf(test("kh", 8.0, now - 3 * DAY_MS), test("kh", 9.2, now))))
        // reteste 10 min depois nao conta
        assertNull(khSwing(listOf(test("kh", 8.0, now - 600_000), test("kh", 9.5, now))))
    }

    @Test fun alertasNuncaSugeremSuplementoSeparado() {
        val now = ms("2026-11-16")
        val ranges = mapOf("kh" to kh, "ca" to ParamRange("ca", 400.0, 450.0, 380.0, 480.0, "ppm"), "mg" to ParamRange("mg", 1250.0, 1400.0, 1200.0, 1450.0, "ppm"))
        val alerts = paramAlerts(listOf(test("kh", 6.5, now), test("ca", 370.0, now), test("mg", 1100.0, now)), ranges, now)
        assertEquals(3, alerts.count { it.severity == Status.CRITICO })
        assertTrue(alerts.any { it.key == "stale_salinidade" })
        val proibido = Regex("(?i)buffer|tamponador|cloreto|bicarbonato|suplemento de (ca|kh|mg|cálcio|magnésio)|balling|kalk")
        alerts.forEach { assertFalse(it.acao, proibido.containsMatchIn(it.acao)) }
    }

    @Test fun no3SoContaComMedicaoRecente() {
        val now = ms("2026-11-16")
        val ranges = mapOf("no3" to ParamRange("no3", 2.0, 10.0, 0.5, 25.0, "ppm"))
        assertTrue(paramAlerts(listOf(test("no3", 40.0, now - 2 * DAY_MS)), ranges, now).any { it.key == "range_no3" })
        assertFalse(paramAlerts(listOf(test("no3", 40.0, now - 20 * DAY_MS)), ranges, now).any { it.key == "range_no3" })
    }

    @Test fun tpaAtrasadaDepoisDe9Dias() {
        val now = ms("2026-11-16")
        assertNull(tpaAlert(listOf(WaterChange(now - 8 * DAY_MS, 10.0)), now))
        assertNotNull(tpaAlert(listOf(WaterChange(now - 10 * DAY_MS, 10.0)), now))
    }

    private val goni = Product("Goni Supreme", doseQuantidade = 1.0, doseUnidade = "colher", doseBaseLitros = 400.0)
    private val amino = Product("Amino Coral", doseQuantidade = 2.0, doseUnidade = "gotas", doseBaseLitros = 100.0)
    private val multivit = Product("Multivit Coral", doseQuantidade = 1.0, doseUnidade = "g", doseBaseLitros = 200.0)
    private val iodine = Product("Iodine", doseQuantidade = 1.0, doseUnidade = "gotas")

    @Test fun dosesPara100Litros() {
        assertEquals("1/4 de colher", formatDose(doseFor(goni, 100.0), goni.doseUnidade))
        assertEquals("2 gotas", formatDose(doseFor(amino, 100.0), amino.doseUnidade))
        assertEquals("0,5 g", formatDose(doseFor(multivit, 100.0), multivit.doseUnidade))
        assertEquals("1 gota", formatDose(doseFor(iodine, 100.0), iodine.doseUnidade))
        assertEquals("2 colheres", formatDose(2.0, "colher"))
        // volume muda -> dose recalcula
        assertEquals("1/2 de colher", formatDose(doseFor(goni, 200.0), "colher"))
        // 125 L (volume atual do Lucas): arredondamento pratico
        assertEquals("~1/3 de colher", formatDose(doseFor(goni, 125.0), "colher"))
        assertEquals("2,5 gotas", formatDose(doseFor(amino, 125.0), "gotas"))
        assertEquals("~0,6 g", formatDose(doseFor(multivit, 125.0), "g"))
        assertEquals("2,5 colheres", formatDose(2.5, "colher"))
        assertEquals("2,5 g", formatDose(2.5, "g"))
        assertEquals("1 gota", formatDose(doseFor(iodine, 125.0), "gotas"))
        assertEquals(0.3125, doseFor(goni, 125.0)!!, 1e-9) // o valor gravado continua exato
        assertEquals("dose a definir", formatDose(null, "ml"))
    }

    private val rGoni = Routine("Goni Supreme", tipo = "dosagem", productId = goni.id, recorrencia = "diaria", horario = "20:00:00")
    private val rAmino = Routine("Amino Coral", tipo = "dosagem", productId = amino.id, recorrencia = "dias_da_semana", diasSemana = listOf(1, 3, 5), horario = "20:00:00")
    private val rTpa = Routine("TPA", tipo = "tpa", recorrencia = "dias_da_semana", diasSemana = listOf(3), horario = "19:00")

    @Test fun rotinasDoDiaEAvisoAgrupado() {
        val seg = LocalDate.parse("2026-11-16") // segunda
        val ter = seg.plusDays(1)
        val qua = seg.plusDays(2)
        assertEquals(listOf("Amino Coral", "Goni Supreme"), occurrencesOn(listOf(rGoni, rAmino, rTpa), emptyList(), seg, zone).map { it.routine.titulo })
        assertEquals(listOf("Goni Supreme"), occurrencesOn(listOf(rGoni, rAmino, rTpa), emptyList(), ter, zone).map { it.routine.titulo })
        assertEquals("TPA", occurrencesOn(listOf(rGoni, rAmino, rTpa), emptyList(), qua, zone).first().routine.titulo)
        val occ = occurrencesOn(listOf(rGoni, rAmino), emptyList(), seg, zone)
        val text = reminderText(occ, mapOf(goni.id to goni, amino.id to amino), 100.0, zone)
        assertEquals("20h: Amino Coral 2 gotas, Goni Supreme 1/4 de colher", text)
    }

    @Test fun estadoDaOcorrenciaEAdiamento() {
        val seg = LocalDate.parse("2026-11-16")
        val due = ms("2026-11-16", "20:00")
        val adiado = RoutineLog(rGoni.id, due, "adiado", updatedAt = due + 5 * 60_000)
        val o = occurrencesOn(listOf(rGoni), listOf(adiado), seg, zone).single()
        assertEquals(OccState.ADIADO, o.state)
        assertFalse(o.isDue(due + 20 * 60_000))
        assertTrue(o.isDue(due + 36 * 60_000))
        val feito = occurrencesOn(listOf(rGoni), listOf(RoutineLog(rGoni.id, due, "feito")), seg, zone).single()
        assertEquals(OccState.FEITO, feito.state)
        assertFalse(feito.isDue(due + DAY_MS))
        assertTrue(Occurrence(rGoni, due, null).isLate(due + 3 * HOUR_MS, 2 * HOUR_MS))
    }

    @Test fun proximoAlarme() {
        val now = ms("2026-11-16", "21:00") // segunda, depois das 20h
        assertEquals(ms("2026-11-17", "20:00"), nextDueAfter(rGoni, now, zone))
        assertEquals(ms("2026-11-18", "20:00"), nextDueAfter(rAmino, now, zone))
        assertEquals(ms("2026-11-18", "19:00"), nextDueAfter(rTpa, now, zone))
        assertNull(nextDueAfter(rGoni.copy(ativa = false), now, zone))
    }

    @Test fun rotinaPorIntervaloFicaAtrasadaAteSerFeita() {
        val carvao = Routine("Carvão", recorrencia = "a_cada_n_semanas", intervaloN = 4, horario = "19:00")
        val hoje = LocalDate.parse("2026-11-16")
        assertTrue(occurrencesOn(listOf(carvao), emptyList(), hoje, zone).isEmpty()) // sem ancora nao agenda
        val vencida = carvao.copy(proximaEm = ms("2026-11-10", "19:00"))
        assertEquals(ms("2026-11-10", "19:00"), occurrencesOn(listOf(vencida), emptyList(), hoje, zone).single().dueAt)
        val now = ms("2026-11-16", "10:00")
        assertEquals(ms("2026-12-14", "19:00"), nextAnchor(vencida, done = true, now = now, zone = zone))
        assertEquals(ms("2026-12-08", "19:00"), nextAnchor(vencida, done = false, now = now, zone = zone))
    }

    @Test fun cumprimentoDaSemana() {
        val now = ms("2026-11-16", "21:00")
        val logs = listOf(
            RoutineLog(rGoni.id, ms("2026-11-16", "20:00"), "feito"),
            RoutineLog(rGoni.id, ms("2026-11-15", "20:00"), "feito"),
            RoutineLog(rGoni.id, ms("2026-11-14", "20:00"), "pulado"),
        )
        assertEquals(2 to 7, compliance(listOf(rGoni), logs, now, zone))
    }

    @Test fun modoNoite() {
        assertTrue(Settings.isNight(0, 0, 7))
        assertTrue(Settings.isNight(6, 0, 7))
        assertFalse(Settings.isNight(7, 0, 7))
        assertTrue(Settings.isNight(23, 22, 6))
        assertTrue(Settings.isNight(3, 22, 6))
        assertFalse(Settings.isNight(12, 22, 6))
    }
}

class HealthScoreTest {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val now = java.time.LocalDateTime.parse("2026-11-16T12:00").atZone(zone).toInstant().toEpochMilli()
    private val ranges = mapOf(
        "kh" to ParamRange("kh", 7.5, 9.0, 7.0, 10.0, "dKH"),
        "ca" to ParamRange("ca", 400.0, 450.0, 380.0, 480.0, "ppm"),
        "mg" to ParamRange("mg", 1250.0, 1400.0, 1200.0, 1450.0, "ppm"),
        "salinidade" to ParamRange("salinidade", 1.024, 1.026, 1.023, 1.027, "SG"),
        "no3" to ParamRange("no3", 2.0, 10.0, 0.5, 25.0, "ppm"),
    )
    private fun t(p: String, v: Double, daysAgo: Int = 1) = WaterTest(now - daysAgo * DAY_MS, p, v, "x")
    private fun score(tests: List<WaterTest>, tpaDaysAgo: Int? = 2, openCases: Int = 0) = br.com.lunelli.lulucoreef.domain.healthScore(
        tests, ranges, listOfNotNull(tpaDaysAgo?.let { WaterChange(now - it * DAY_MS, 10.0) }), emptyList(), emptyList(),
        List(openCases) { br.com.lunelli.lulucoreef.data.HealthCase("caso", abertoEm = now) }, now, zone,
    )
    private val bons = listOf(t("kh", 8.0), t("ca", 420.0), t("mg", 1300.0), t("salinidade", 1.025))

    @Test fun tudoIdealDa100() = assertEquals(100, score(bons).score)

    @Test fun descontosDaSpec() {
        // KH atencao (-8), Ca critico (-15), Mg velho (-5), TPA ha 12 dias (-10), 4 casos (-15, teto), NO3 critico pela metade (-7,5)
        val tests = listOf(t("kh", 7.2), t("ca", 370.0), t("mg", 1300.0, daysAgo = 9), t("salinidade", 1.025), t("no3", 40.0))
        val h = score(tests, tpaDaysAgo = 12, openCases = 4)
        assertEquals(40, h.score) // 100 - 60,5 = 39,5, arredonda para 40
        assertEquals(6, h.items.size)
    }

    @Test fun semDadosMostraOQueFalta() {
        val h = score(listOf(t("kh", 8.0)), tpaDaysAgo = null)
        assertNull(h.score)
        assertEquals(listOf("medir Cálcio", "medir Magnésio", "medir Salinidade", "registrar uma TPA"), h.missing)
    }

    @Test fun tpaMuitoAtrasada() = assertEquals(80, score(bons, tpaDaysAgo = 17).score)
}

class ReportAndUpcomingTest {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private fun ms(date: String, time: String = "12:00") = java.time.LocalDateTime.parse("${date}T$time").atZone(zone).toInstant().toEpochMilli()
    private val now = ms("2026-11-16", "22:00") // segunda

    @Test fun proximosPassosSemDosagens() {
        val goni = Routine("Goni Supreme", tipo = "dosagem", recorrencia = "diaria", horario = "20:00")
        val tpa = Routine("TPA", tipo = "tpa", recorrencia = "dias_da_semana", diasSemana = listOf(3), horario = "19:00")
        val vidro = Routine("Limpeza do vidro", tipo = "manutencao", recorrencia = "dias_da_semana", diasSemana = listOf(3), horario = "19:00")
        val carvao = Routine("Troca do carvão ativado", tipo = "manutencao", recorrencia = "a_cada_n_semanas", intervaloN = 4, horario = "19:00", proximaEm = ms("2026-12-11", "19:00"))
        val semData = Routine("Outra", tipo = "manutencao", recorrencia = "a_cada_n_dias", intervaloN = 10, horario = "19:00")
        val items = br.com.lunelli.lulucoreef.domain.upcoming(listOf(goni, tpa, vidro, carvao, semData), emptyList(), now, zone)
        assertEquals(listOf("Limpeza do vidro", "TPA", "Limpeza do vidro", "TPA", "Troca do carvão ativado"), items.map { it.routine.titulo })
        val w = { t: Long -> br.com.lunelli.lulucoreef.domain.whenText(t, now, zone) }
        assertEquals("Quarta, 19h", w(items[0].dueAt))
        assertEquals("11/12 (em 25 dias)", w(items[4].dueAt))
        assertEquals("Amanhã, 18h30", w(ms("2026-11-17", "18:30")))
        assertEquals("Hoje, 23h", w(ms("2026-11-16", "23:00")))
        assertEquals("Hoje, 19h (atrasada)", w(ms("2026-11-16", "19:00")))
        assertEquals("Atrasada desde 10/11", w(ms("2026-11-10", "19:00")))
    }

    @Test fun relatorioDoPeriodo() {
        val amino = Product("Amino Coral", doseUnidade = "gotas")
        val carvao = Routine("Troca do carvão ativado", tipo = "manutencao", recorrencia = "a_cada_n_semanas", intervaloN = 4, horario = "19:00")
        val data = br.com.lunelli.lulucoreef.domain.ReportData(
            tank = br.com.lunelli.lulucoreef.data.TankConfig(volumeDosagemL = 125.0, salAtual = "Blue Treasure Reef Salt"),
            tests = listOf(
                WaterTest(ms("2026-11-07", "11:53"), "kh", 7.7, "dKH", observacao = "1ª virada"),
                WaterTest(ms("2026-11-06", "22:37"), "kh", 8.2, "dKH", baixaConfianca = true),
                WaterTest(ms("2026-11-11", "22:34"), "kh", 7.85, "dKH"),
                WaterTest(ms("2026-10-01"), "kh", 9.9, "dKH"), // fora do periodo
            ),
            changes = listOf(WaterChange(ms("2026-11-03", "22:30"), 14.5, sal = "Blue Treasure Reef Salt")),
            dosings = listOf(br.com.lunelli.lulucoreef.data.Dosing(ms("2026-11-10", "20:00"), amino.id, quantidade = 2.5, unidade = "gotas"), br.com.lunelli.lulucoreef.data.Dosing(ms("2026-11-12", "20:00"), amino.id, quantidade = 2.5, unidade = "gotas")),
            products = listOf(amino), routines = listOf(carvao),
            logs = listOf(RoutineLog(carvao.id, ms("2026-11-08", "19:00"), "feito", feitoEm = ms("2026-11-08", "19:20"))),
            events = listOf(br.com.lunelli.lulucoreef.data.Event(ms("2026-11-07", "11:00"), texto = "Método do KH mudou")), cases = emptyList(),
        )
        val r = br.com.lunelli.lulucoreef.domain.buildReport(data, ms("2026-11-01", "00:00"), now, now, zone)
        fun has(s: String) = assertTrue("faltou: $s\n$r", r.contains(s))
        has("Período: 01/11/2026 a 16/11/2026")
        has("Última TPA: 03/11 22:30, 14,5 L (há 12 dias)")
        has("KH (dKH): 3 medições, mín. 7,7, máx. 7,85, média 7,7")
        has("06/11 22:37  8,2  (baixa confiança)")
        has("07/11 11:53  7,7  - 1ª virada")
        has("KH: de 7,7 para 7,85 em 4,4 dias (+0,034 por dia, +0,24 por semana)")
        has("Amino Coral: 2 vezes, total 5 gotas, última em 12/11 20:00")
        has("08/11 19:20  Troca do carvão ativado")
        has("07/11 11:00  Método do KH mudou")
        assertFalse(r.contains("9,9"))
        // so as secoes pedidas
        val so = br.com.lunelli.lulucoreef.domain.buildReport(data, ms("2026-11-01", "00:00"), now, now, zone, setOf(br.com.lunelli.lulucoreef.domain.ReportSection.TPAS))
        assertTrue(so.contains("TPAS") && !so.contains("PRODUTOS USADOS") && !so.contains("RESUMO"))
    }
}
