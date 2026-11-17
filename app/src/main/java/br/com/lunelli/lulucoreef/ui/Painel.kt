package br.com.lunelli.lulucoreef.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.Brush
import br.com.lunelli.lulucoreef.data.ParamRange
import br.com.lunelli.lulucoreef.data.Product
import br.com.lunelli.lulucoreef.data.Routine
import br.com.lunelli.lulucoreef.data.reliable
import br.com.lunelli.lulucoreef.domain.Alert
import br.com.lunelli.lulucoreef.domain.allAlerts
import br.com.lunelli.lulucoreef.domain.DAY_MS
import br.com.lunelli.lulucoreef.domain.HealthScore
import br.com.lunelli.lulucoreef.domain.OccState
import br.com.lunelli.lulucoreef.domain.Occurrence
import br.com.lunelli.lulucoreef.domain.Params
import br.com.lunelli.lulucoreef.domain.Status
import br.com.lunelli.lulucoreef.domain.compliance
import br.com.lunelli.lulucoreef.domain.doseFor
import br.com.lunelli.lulucoreef.domain.formatDose
import br.com.lunelli.lulucoreef.domain.healthScore
import br.com.lunelli.lulucoreef.domain.intervalDays
import br.com.lunelli.lulucoreef.domain.isInterval
import br.com.lunelli.lulucoreef.domain.lateRoutineAlerts
import br.com.lunelli.lulucoreef.domain.latestOf
import br.com.lunelli.lulucoreef.domain.numShort
import br.com.lunelli.lulucoreef.domain.occurrencesOn
import br.com.lunelli.lulucoreef.domain.paramAlerts
import br.com.lunelli.lulucoreef.domain.recurrenceText
import br.com.lunelli.lulucoreef.domain.statusOf
import br.com.lunelli.lulucoreef.domain.time
import br.com.lunelli.lulucoreef.domain.toLocalDate
import br.com.lunelli.lulucoreef.domain.tpaAlert
import br.com.lunelli.lulucoreef.domain.trendOf
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun PainelScreen(vm: MainViewModel, nav: Nav) {
    val now by vm.now.collectAsStateWithLifecycle()
    val tank by vm.tank.collectAsStateWithLifecycle()
    val ranges by vm.ranges.collectAsStateWithLifecycle()
    val tests by vm.tests.collectAsStateWithLifecycle()
    val changes by vm.changes.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    val dosings by vm.dosings.collectAsStateWithLifecycle()
    val routines by vm.routines.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    val livestock by vm.livestock.collectAsStateWithLifecycle()
    val cases by vm.cases.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val zone = vm.zone
    val volume = tank?.volumeDosagemL ?: 100.0
    val productById = remember(products) { products.associateBy { it.id } }

    val today = remember(routines, logs, now) { occurrencesOn(routines, logs, now.toLocalDate(zone), zone) }
    val entries by vm.entries.collectAsStateWithLifecycle()
    val alerts = remember(tests, ranges, changes, today, cases, entries, now) { allAlerts(tests, ranges, changes, today, cases, entries, now) }
    val health = remember(tests, ranges, changes, routines, logs, cases, now) { healthScore(tests, ranges, changes, routines, logs, cases, now, zone) }
    var showScore by remember { mutableStateOf(false) }
    val needAnchor = routines.filter { it.ativa && it.isInterval() && it.proximaEm == null }
    val toReview = livestock.count { it.revisar && it.status == "ativo" }

    val reliableTests = remember(tests) { tests.reliable() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > 640.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Header(Modifier.cascade(0), tank?.nome ?: "Luluco Reef", tank?.fotoVitrinePath, now, health, volume, livestock.filter { it.status == "ativo" }.sumOf { it.quantidade }) { showScore = true }

            val hoje: @Composable (Modifier) -> Unit = { m ->
                Column(m, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("Hoje")
                    if (today.isEmpty()) Panel(Modifier.fillMaxWidth()) { Muted("Nenhuma rotina para hoje.") }
                    today.forEach { o -> TodayItem(vm, nav, o, productById[o.routine.productId], volume, now) }
                }
            }
            val metricas: @Composable (Modifier) -> Unit = { m ->
                Column(m, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("Métricas principais")
                    val shown = Params.ALL.filter { it in settings.shownParams }
                    if (shown.isEmpty()) Panel(Modifier.fillMaxWidth()) { Muted("Nenhum parâmetro escolhido. Ajuste em Ajustes > O que aparece nas telas.") }
                    shown.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { p ->
                                key(p) {
                                    val last = reliableTests.latestOf(p)
                                    val exit = remember(tests, ranges, now) { trendOf(tests, p, ranges[p], now)?.daysToExit }
                                    MetricCard(Modifier.weight(1f), p, last?.valor, last?.medidoEm, ranges[p], exit, now)
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    hoje(Modifier.weight(1f).cascade(1))
                    metricas(Modifier.weight(1f).cascade(2))
                }
            } else {
                hoje(Modifier.fillMaxWidth().cascade(1))
                metricas(Modifier.fillMaxWidth().cascade(2))
            }

            Row(Modifier.cascade(3), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton("Nova medição", Modifier.weight(1f)) { nav.go(Screen.TestForm()) }
                BigButton("Registrar TPA", Modifier.weight(1f)) { nav.go(Screen.TpaForm()) }
                BigButton("Adicionar habitante", Modifier.weight(1f), color = Luluco.Royal) { nav.go(Screen.LivestockForm()) }
                BigButton("Nova nota", Modifier.weight(1f), color = Luluco.Royal) { nav.go(Screen.EventForm()) }
            }

            if (!settings.inventoryNoticeSeen && toReview > 0) {
                Panel(Modifier.fillMaxWidth(), borderColor = Luluco.Pool) {
                    Text("Revise o inventário", fontWeight = FontWeight.Bold)
                    Muted("$toReview itens foram pré-cadastrados e estão marcados com \"confirmar\". Abra Meu Aquário e ajuste nomes e quantidades.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { vm.updateSettings { it.copy(inventoryNoticeSeen = true) }; nav.top(Screen.Aquario) }) { Text("Abrir Meu Aquário") }
                        TextButton(onClick = { vm.updateSettings { it.copy(inventoryNoticeSeen = true) } }) { Text("Depois") }
                    }
                }
            }
            needAnchor.forEach { r -> AnchorQuestion(vm, r) }

            if (alerts.isNotEmpty()) {
                Column(Modifier.cascade(4), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("Alertas ativos")
                    alerts.forEach { AlertCard(it) }
                }
            }

            Column(Modifier.cascade(5), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("Resumo da semana")
                val weekAgo = now - 7 * DAY_MS
                val (doseDone, doseExpected) = remember(routines, logs, now) { compliance(routines.filter { it.tipo == "dosagem" }, logs, now, zone) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat(Modifier.weight(1f), tests.count { it.medidoEm >= weekAgo }.toString(), "medições")
                    Stat(Modifier.weight(1f), changes.count { it.feitaEm >= weekAgo }.toString(), "TPAs")
                    Stat(Modifier.weight(1f), "$doseDone/$doseExpected", "doses feitas / previstas")
                    Stat(Modifier.weight(1f), dosings.count { it.dosadoEm >= weekAgo }.toString(), "doses registradas")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    if (showScore) ScoreDialog(health) { showScore = false }
}

/** Topo do Painel: saudacao pelo horario e a nota de saude em anel, com ondas na base. */
@Composable
private fun Header(modifier: Modifier, nome: String, foto: String?, now: Long, health: HealthScore, volume: Double, count: Int, onScore: () -> Unit) {
    val zdt = Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault())
    val greeting = when (zdt.hour) { in 5..11 -> "Bom dia"; in 12..17 -> "Boa tarde"; else -> "Boa noite" }
    val date = zdt.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", PtBr)).replaceFirstChar { it.uppercase() }
    val shape = RoundedCornerShape(28.dp)
    Box(modifier.fillMaxWidth().height(148.dp).clip(shape).background(Brush.horizontalGradient(listOf(Luluco.Royal, Luluco.Pool.copy(alpha = 0.75f))))) {
        if (foto != null) {
            Photo(foto, Modifier.fillMaxSize(), maxPx = 1024)
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Luluco.Navy.copy(alpha = 0.85f), Luluco.Royal.copy(alpha = 0.45f)))))
        }
        if (LocalWaterFx.current) Waves(Modifier.fillMaxWidth().height(46.dp).align(Alignment.BottomCenter), color = Luluco.Cream)
        Row(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            MenuButton()
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("$greeting, Lucas", fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, color = Luluco.Cream, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(date, color = Luluco.Cream, maxLines = 1)
                Text("$nome  ·  ${numShort(volume)} L  ·  $count habitantes", color = Luluco.Cream.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
            Box(Modifier.clip(CircleShape).clickable(onClick = onScore).padding(4.dp)) {
                HealthRing(health.score, 112.dp)
            }
        }
    }
}

@Composable
private fun ScoreDialog(h: HealthScore, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
        title = { Text(if (h.score != null) "Saúde: ${h.score}" else "Saúde: -") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (h.score == null) {
                    Text("Ainda faltam dados para calcular a nota:")
                    h.missing.forEach { Text("- $it") }
                } else {
                    Text("Começa em 100 e desconta:")
                    if (h.items.isEmpty()) Text("Nenhum desconto. Tudo em dia.")
                }
                h.items.forEach { Text("${numShort(it.points)}  ${it.label}") }
                Muted("Regra fixa, sem IA.")
            }
        },
    )
}

@Composable
private fun AnchorQuestion(vm: MainViewModel, r: Routine) {
    Panel(Modifier.fillMaxWidth(), borderColor = Luluco.Pool) {
        Text("Quando foi a última vez: ${r.titulo}?", fontWeight = FontWeight.Bold)
        Muted("Preciso da data para calcular a próxima (${recurrenceText(r)}).")
        DateField("Última vez", null) { iso ->
            if (iso != null) {
                val next = LocalDate.parse(iso).plusDays(r.intervalDays())
                val anchor = next.atTime(r.time()).atZone(vm.zone).toInstant().toEpochMilli()
                vm.save(r.copy(proximaEm = anchor))
            }
        }
    }
}

@Composable
private fun TodayItem(vm: MainViewModel, nav: Nav, o: Occurrence, product: Product?, volume: Double, now: Long) {
    val due = o.isDue(now)
    val detail = when {
        o.routine.tipo == "dosagem" && product != null -> formatDose(doseFor(product, volume), product.doseUnidade)
        else -> o.routine.instrucao.orEmpty()
    }
    Panel(Modifier.fillMaxWidth(), borderColor = if (due) Luluco.Coral else null, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(fmtTime(o.dueAt), fontFamily = Fredoka, fontSize = 20.sp, color = if (due) Luluco.CoralText else Luluco.PoolText, modifier = Modifier.width(66.dp))
            Column(Modifier.weight(1f)) {
                Text(o.routine.titulo, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (detail.isNotEmpty()) Muted(detail, maxLines = 2)
                if (o.dueAt.toLocalDate(vm.zone) != now.toLocalDate(vm.zone)) Muted("vencida em ${fmtDay(o.dueAt)}")
            }
            when (o.state) {
                OccState.FEITO -> Badge("feito", Luluco.Ok)
                OccState.PULADO -> Badge("pulado", Luluco.Neutral)
                OccState.ADIADO -> if (!due) Badge("adiado até ${fmtTime(o.effectiveDueAt)}", Luluco.Pool) else Badge("atrasada", Luluco.Coral)
                OccState.PENDENTE -> if (due) Badge("atrasada", Luluco.Coral)
            }
        }
        if (o.open) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigButton("Feito", Modifier.weight(1.1f), color = Luluco.Ok) {
                    if (o.routine.tipo == "tpa") nav.go(Screen.TpaForm(occurrence = o)) else vm.markDone(o)
                }
                BigButton("Adiar 30 min", Modifier.weight(1.4f), color = Luluco.SolidHigh) { vm.snooze(o) }
                BigButton("Pular", Modifier.weight(0.8f), color = Luluco.SolidHigh) { vm.skip(o) }
            }
        }
    }
}

@Composable
fun MetricCard(modifier: Modifier, param: String, value: Double?, at: Long?, range: ParamRange?, daysToExit: Double?, now: Long) {
    val status = when {
        value == null || at == null -> Status.SEM_MEDICAO
        range == null -> Status.SEM_MEDICAO
        else -> statusOf(value, range)
    }
    Panel(modifier, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Params.label(param), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1)
            Badge(statusLabel(status), statusColor(status))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value?.let { animatedParam(param, it) } ?: "-", fontFamily = Fredoka, fontSize = 38.sp, color = statusColor(status))
            Spacer(Modifier.width(6.dp))
            Text(Params.unit(param), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
        }
        if (range != null) RangeBar(value, range, statusColor(status))
        Muted(if (at != null) "${fmtDateTime(at)} (${fmtAgo(at, now)})" else "sem medição", maxLines = 1)
        if (daysToExit != null) Text("Projeção: sai da faixa em ~${maxOf(1, daysToExit.roundToInt())} dias", color = Luluco.CoralText, fontSize = 13.sp)
    }
}

/** Barra da faixa: zona de alerta ao fundo, faixa ideal destacada e marcador do valor. */
@Composable
private fun RangeBar(value: Double?, r: ParamRange, markerColor: Color) {
    val pad = (r.alertaMax - r.alertaMin) * 0.25
    val lo = r.alertaMin - pad
    val hi = r.alertaMax + pad
    fun frac(v: Double) = ((v - lo) / (hi - lo)).coerceIn(0.0, 1.0).toFloat()
    Canvas(Modifier.fillMaxWidth().height(18.dp).padding(vertical = 4.dp)) {
        val h = size.height
        val radius = CornerRadius(h / 2, h / 2)
        drawRoundRect(Luluco.Critical.copy(alpha = 0.45f), size = size, cornerRadius = radius)
        drawRect(Luluco.Coral.copy(alpha = 0.75f), topLeft = Offset(frac(r.alertaMin) * size.width, 0f), size = Size((frac(r.alertaMax) - frac(r.alertaMin)) * size.width, h))
        drawRect(Luluco.Ok.copy(alpha = 0.8f), topLeft = Offset(frac(r.idealMin) * size.width, 0f), size = Size((frac(r.idealMax) - frac(r.idealMin)) * size.width, h))
        if (value != null) {
            val x = frac(value) * size.width
            drawCircle(Luluco.Abyss, radius = h * 0.95f, center = Offset(x, h / 2))
            drawCircle(Luluco.Cream, radius = h * 0.7f, center = Offset(x, h / 2))
            drawCircle(markerColor, radius = h * 0.4f, center = Offset(x, h / 2))
        }
    }
}

@Composable
fun AlertCard(a: Alert) {
    Panel(Modifier.fillMaxWidth(), borderColor = statusColor(a.severity), padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Badge(statusLabel(a.severity), statusColor(a.severity))
            Text(a.fato, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))
        Text(a.acao, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Stat(modifier: Modifier, value: String, label: String) {
    Panel(modifier, padding = 12.dp) {
        Text(value, fontFamily = Fredoka, fontSize = 30.sp, color = Luluco.Cream)
        Muted(label, maxLines = 2)
    }
}
