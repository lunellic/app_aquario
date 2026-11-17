package br.com.lunelli.lulucoreef.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lunelli.lulucoreef.domain.DAY_MS
import br.com.lunelli.lulucoreef.domain.ReportData
import br.com.lunelli.lulucoreef.domain.ReportSection
import br.com.lunelli.lulucoreef.domain.buildReport
import java.time.Instant

private val PERIODS = listOf("7" to "7 dias", "30" to "30 dias", "mes" to "Este mês", "mes_passado" to "Mês passado", "90" to "90 dias", "tudo" to "Tudo")

/** Relatorios: escolhe o periodo e o que entra, ve o texto na tela e manda para o e-mail ou compartilha. */
@Composable
fun RelatoriosScreen(vm: MainViewModel) {
    val now by vm.now.collectAsStateWithLifecycle()
    val tank by vm.tank.collectAsStateWithLifecycle()
    val tests by vm.tests.collectAsStateWithLifecycle()
    val changes by vm.changes.collectAsStateWithLifecycle()
    val dosings by vm.dosings.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    val routines by vm.routines.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()
    val cases by vm.cases.collectAsStateWithLifecycle()
    val busy by vm.aiBusy.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val zone = vm.zone
    var period by remember { mutableStateOf("30") }
    var sections by remember { mutableStateOf(ReportSection.values().toSet()) }
    var feedback by remember { mutableStateOf<String?>(null) }

    // limites do periodo (o "agora" e arredondado ao minuto para o texto nao piscar)
    val nowMin = now / 60_000 * 60_000
    val today = Instant.ofEpochMilli(nowMin).atZone(zone).toLocalDate()
    val (from, to) = when (period) {
        "mes" -> today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli() to nowMin
        "mes_passado" -> today.minusMonths(1).withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli() to (today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1)
        "tudo" -> (listOfNotNull(tests.minOfOrNull { it.medidoEm }, changes.minOfOrNull { it.feitaEm }, dosings.minOfOrNull { it.dosadoEm }, events.minOfOrNull { it.ocorridoEm }).minOrNull() ?: nowMin) to nowMin
        else -> today.minusDays(period.toLong()).atStartOfDay(zone).toInstant().toEpochMilli() to nowMin
    }
    val text = remember(period, sections, nowMin, tests, changes, dosings, products, routines, logs, events, cases, tank) {
        buildReport(ReportData(tank, tests, changes, dosings, products, routines, logs, events, cases), from, to, nowMin, zone, sections)
    }
    val subject = "Luluco Reef - relatório (${PERIODS.first { it.first == period }.second.lowercase()})"
    val sending = "report" in busy

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader("Relatórios")
        SectionTitle("Período")
        ChipRow(PERIODS, period) { period = it; feedback = null }
        SectionTitle("O que entra")
        ChipRow(ReportSection.values().map { it.name to it.label }, null, selectedSet = sections.map { it.name }.toSet()) { name ->
            val s = ReportSection.valueOf(name)
            sections = if (s in sections) sections - s else sections + s
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton(if (sending) "Enviando..." else "Enviar para meu e-mail", Modifier.weight(1f), enabled = !sending && sections.isNotEmpty()) {
                feedback = null
                vm.emailReport(subject, text) { feedback = it }
            }
            BigButton("Compartilhar", Modifier.weight(1f), color = Luluco.Royal, enabled = sections.isNotEmpty()) {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, subject).putExtra(Intent.EXTRA_TEXT, text)
                vm.email?.let { send.putExtra(Intent.EXTRA_EMAIL, arrayOf(it)) }
                ctx.startActivity(Intent.createChooser(send, "Enviar relatório"))
            }
        }
        feedback?.let { Panel(Modifier.fillMaxWidth(), borderColor = Luluco.Pool) { Text(it) } }
        Muted("\"Enviar para meu e-mail\" manda para ${vm.email ?: "o e-mail da conta"} e precisa do backend publicado (Bloco C). \"Compartilhar\" abre os apps do aparelho.")

        SectionTitle("Prévia")
        Panel(Modifier.fillMaxWidth()) {
            Text(text, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp)
        }
        Spacer(Modifier.height(8.dp))
    }
}
