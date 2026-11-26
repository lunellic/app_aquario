package br.com.lunelli.lulucoreef.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import br.com.lunelli.lulucoreef.BuildConfig
import br.com.lunelli.lulucoreef.update.Atualizacao
import br.com.lunelli.lulucoreef.update.VersaoRemota
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
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
import br.com.lunelli.lulucoreef.domain.diaDaAgenda
import br.com.lunelli.lulucoreef.domain.rotuloHoje
import br.com.lunelli.lulucoreef.domain.healthDelta
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

    // "hoje" da agenda vira as 6h, nao a meia-noite (ver diaDaAgenda)
    val today = remember(routines, logs, now) { occurrencesOn(routines, logs, diaDaAgenda(now, zone), zone, now) }
    // Navegacao de dias no "Hoje": voltar para marcar o que ficou de ontem (ex.: dose feita de madrugada) ou ver amanha.
    var dayOffset by remember { mutableIntStateOf(0) }
    val realToday = diaDaAgenda(now, zone)
    val selDate = realToday.plusDays(dayOffset.toLong())
    val dayList = remember(routines, logs, now, dayOffset) { if (dayOffset == 0) today else occurrencesOn(routines, logs, selDate, zone, now) }
    val yesterdayOpen = remember(routines, logs, now) { occurrencesOn(routines, logs, realToday.minusDays(1), zone).count { it.open && it.dueAt.toLocalDate(zone) == realToday.minusDays(1) } }
    val entries by vm.entries.collectAsStateWithLifecycle()
    val alerts = remember(tests, ranges, changes, today, cases, entries, now) { allAlerts(tests, ranges, changes, today, cases, entries, now) }
    val health = remember(tests, ranges, changes, routines, logs, cases, now) { healthScore(tests, ranges, changes, routines, logs, cases, now, zone) }
    val healthVar = remember(tests, ranges, changes, routines, logs, cases, now / 3_600_000) { healthDelta(now, 7, tests, ranges, changes, routines, logs, cases, zone) }
    var showScore by remember { mutableStateOf(false) }
    val needAnchor = routines.filter { it.ativa && it.isInterval() && it.proximaEm == null }
    val toReview = livestock.count { it.revisar && it.status == "ativo" }

    val reliableTests = remember(tests) { tests.reliable() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > 640.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Header(Modifier.cascade(0), tank?.nome ?: "Luluco Reef", tank?.fotoVitrinePath, now, health, healthVar, volume, livestock.filter { it.status == "ativo" }.sumOf { it.quantidade }) { showScore = true }

            AvisoDeQueda()
            AvisoDeAtualizacao()

            val hoje: @Composable (Modifier) -> Unit = { m ->
                Column(m, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { dayOffset-- }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Dia anterior", tint = Luluco.Cream) }
                        SectionTitle(dayLabel(dayOffset, selDate, now, zone))
                        IconButton(onClick = { dayOffset++ }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Próximo dia", tint = Luluco.Cream) }
                        Spacer(Modifier.weight(1f))
                        if (dayOffset != 0) TextButton(onClick = { dayOffset = 0 }) { Text("Voltar para hoje") }
                    }
                    if (dayOffset == 0 && yesterdayOpen > 0) {
                        Text("Ontem ficaram $yesterdayOpen pendente${if (yesterdayOpen > 1) "s" else ""}. Toque para marcar.",
                            color = Luluco.CoralText, style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { dayOffset = -1 }.padding(vertical = 4.dp))
                    }
                    if (dayList.isEmpty()) Panel(Modifier.fillMaxWidth()) { Muted("Nenhuma rotina neste dia.") }
                    else Panel(Modifier.fillMaxWidth(), padding = 0.dp) {
                        dayList.forEachIndexed { i, o ->
                            if (i > 0) HorizontalDivider(color = Luluco.Divider, thickness = 1.dp)
                            TodayItem(vm, nav, o, productById[o.routine.productId], volume, now, selDate)
                        }
                    }
                }
            }
            val metricas: @Composable (Modifier) -> Unit = { m ->
                Column(m, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("Métricas principais")
                    val shown = Params.ALL.filter { it in settings.shownParams }
                    if (shown.isEmpty()) Panel(Modifier.fillMaxWidth()) { Muted("Nenhum parâmetro escolhido. Ajuste em Ajustes > O que aparece nas telas.") }
                    // um so card, dividido por linhas finas (sem um bloco por parametro)
                    if (shown.isNotEmpty()) Panel(Modifier.fillMaxWidth(), padding = 0.dp) {
                        shown.chunked(2).forEachIndexed { ri, pair ->
                            if (ri > 0) HorizontalDivider(color = Luluco.Divider, thickness = 1.dp)
                            Row(Modifier.height(IntrinsicSize.Min)) {
                                pair.forEachIndexed { ci, p ->
                                    if (ci > 0) VerticalDivider(color = Luluco.Divider, thickness = 1.dp)
                                    key(p) {
                                        val last = reliableTests.latestOf(p)
                                        val exit = remember(tests, ranges, now) { trendOf(tests, p, ranges[p], now)?.daysToExit }
                                        val series = remember(reliableTests, p) { reliableTests.filter { it.parametro == p }.sortedBy { it.medidoEm }.takeLast(12).map { it.medidoEm to it.valor } }
                                        MetricCard(Modifier.weight(1f).testTag("card_$p").clickable { nav.go(Screen.TestForm(only = p)) }, p, last?.valor, last?.medidoEm, ranges[p], exit, now, series)
                                    }
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    FinanceBlock(vm, nav)
                    // logo abaixo das financas, como ele pediu: um toque e o timer esta contando
                    TimerBlock(nav)
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
                BigButton("Registrar TPA", Modifier.weight(1f), color = Luluco.Solid) { nav.go(Screen.TpaForm()) }
                BigButton("Adicionar habitante", Modifier.weight(1f), color = Luluco.Solid) { nav.go(Screen.LivestockForm()) }
                BigButton("Nova nota", Modifier.weight(1f), color = Luluco.Solid) { nav.go(Screen.EventForm()) }
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

            Column(Modifier.cascade(4), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("Calendário")
                CalendarCard(nav, routines, logs, productById, volume, now, zone, wide)
            }

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
                    Stat(Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).clickable { nav.go(Screen.HistoricoDe("teste")) }, tests.count { it.medidoEm >= weekAgo }.toString(), "medições")
                    Stat(Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).clickable { nav.go(Screen.HistoricoDe("tpa")) }, changes.count { it.feitaEm >= weekAgo }.toString(), "TPAs")
                    Stat(Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).clickable { nav.go(Screen.HistoricoDe("dose")) }, "$doseDone/$doseExpected", "doses feitas / previstas")
                    Stat(Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).clickable { nav.go(Screen.HistoricoDe("dose")) }, dosings.count { it.dosadoEm >= weekAgo }.toString(), "doses registradas")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    if (showScore) ScoreDialog(health) { showScore = false }
}

/** Topo do Painel: saudacao pelo horario e a nota de saude em anel, com ondas na base. */
@Composable
private fun Header(modifier: Modifier, nome: String, foto: String?, now: Long, health: HealthScore, variacao: Int?, volume: Double, count: Int, onScore: () -> Unit) {
    val zdt = Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault())
    val greeting = when (zdt.hour) { in 5..11 -> "Bom dia"; in 12..17 -> "Boa tarde"; else -> "Boa noite" }
    val date = zdt.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", PtBr)).replaceFirstChar { it.uppercase() }
    val shape = RoundedCornerShape(20.dp)
    val ring = scoreColor(health.score, health.critico)
    Box(modifier.fillMaxWidth().height(148.dp).clip(shape).background(Luluco.Card).border(1.dp, Luluco.CardBorder, shape)) {
        if (foto != null) {
            Photo(foto, Modifier.fillMaxSize(), maxPx = 1024)
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Luluco.Card, Luluco.Card.copy(alpha = 0.92f), Luluco.Card.copy(alpha = 0.55f)))))
        }
        // brilho bem discreto da cor da nota atras do anel
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(ring.copy(alpha = 0.08f), androidx.compose.ui.graphics.Color.Transparent), center = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, 0f), radius = 520f)))
        Row(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            MenuButton()
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Eyebrow(date)
                Text("$greeting, Lucas", fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, letterSpacing = (-0.6).sp, color = Luluco.Cream, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$nome  ·  ${numShort(volume)} L  ·  $count habitantes", color = Luluco.TextSoft, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.clip(CircleShape).clickable(onClick = onScore).padding(4.dp)) {
                    HealthRing(health.score, if (variacao != null) 84.dp else 104.dp, stroke = 5.dp, critico = health.critico)
                }
                // Quanto a nota andou na semana. Aqui a cor tem sinal: na saude subir e bom.
                if (variacao != null) DeltaBadge("${kotlin.math.abs(variacao)} em 7 dias", variacao.coerceIn(-1, 1), comSinal = true)
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

/** "Hoje" (ou "Ainda quarta" de madrugada), "Ontem", "Amanhã" ou "Seg, 16/11". */
private fun dayLabel(offset: Int, date: java.time.LocalDate, now: Long, zone: java.time.ZoneId): String = when (offset) {
    0 -> rotuloHoje(now, zone)
    -1 -> "Ontem"
    1 -> "Amanhã"
    else -> date.format(DateTimeFormatter.ofPattern("EEE, dd/MM", PtBr)).replaceFirstChar { it.uppercase() }.replace(".", "")
}

@Composable
private fun TodayItem(vm: MainViewModel, nav: Nav, o: Occurrence, product: Product?, volume: Double, now: Long, day: java.time.LocalDate) {
    val isToday = day == diaDaAgenda(now, vm.zone)
    val due = o.isDue(now)
    val detail = when {
        o.routine.tipo == "dosagem" && product != null -> formatDose(doseFor(product, volume), product.doseUnidade)
        else -> o.routine.instrucao.orEmpty()
    }
    var editar by remember { mutableStateOf(false) }
    if (editar) DoneDialog(vm, o) { editar = false }
    Column(Modifier.fillMaxWidth().then(if (!o.open) Modifier.clickable { editar = true } else Modifier).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(fmtTime(o.dueAt), fontSize = 17.sp, fontWeight = FontWeight.Medium, color = if (due) Luluco.CoralText else if (o.open) Luluco.Cream else Luluco.TextFaint, modifier = Modifier.width(62.dp))
            Column(Modifier.weight(1f)) {
                Text(o.routine.titulo, fontWeight = FontWeight.Medium, color = if (o.open) Luluco.Cream else Luluco.TextSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (detail.isNotEmpty()) Muted(detail, maxLines = 2)
                if (o.dueAt.toLocalDate(vm.zone) != day) Muted("vencida em ${fmtDay(o.dueAt)}")
            }
            when (o.state) {
                // mostra a hora real em que foi marcado (com a data, se foi em outro dia)
                OccState.FEITO -> Badge(o.log?.feitoEm?.let { f -> "feito " + (if (f.toLocalDate(vm.zone) != o.dueAt.toLocalDate(vm.zone)) fmtDateTime(f) else fmtTime(f)) } ?: "feito", Luluco.ZoneIdeal)
                OccState.PULADO -> Badge("pulado", Luluco.Neutral)
                OccState.ADIADO -> if (!due) Badge("adiado até ${fmtTime(o.effectiveDueAt)}", Luluco.Pool) else Badge("atrasada", Luluco.ZoneWarn)
                OccState.PENDENTE -> if (due) Badge("atrasada", Luluco.ZoneWarn)
            }
        }

        if (o.open) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.padding(start = 62.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigButton("Feito", Modifier.weight(1.1f), color = Luluco.Pool) {
                    when {
                        o.routine.tipo == "tpa" -> nav.go(Screen.TpaForm(occurrence = o))
                        // teste sem medicoes de hoje: abre as roletas; quando KH, Ca e Mg forem salvos, a rotina se marca sozinha
                        o.routine.tipo == "teste" && !vm.testsDoneToday(o.routine) -> nav.go(Screen.TestForm())
                        else -> vm.markDone(o)
                    }
                }
                if (isToday) BigButton("Adiar 30 min", Modifier.weight(1.4f), color = Luluco.Solid) { vm.snooze(o) }
                BigButton("Pular", Modifier.weight(0.8f), color = Luluco.Solid) { vm.skip(o) }
            }
        }
    }
}

@Composable
fun MetricCard(modifier: Modifier, param: String, value: Double?, at: Long?, range: ParamRange?, daysToExit: Double?, now: Long, series: List<Pair<Long, Double>> = emptyList()) {
    val status = when {
        value == null || at == null -> Status.SEM_MEDICAO
        range == null -> Status.SEM_MEDICAO
        else -> statusOf(value, range)
    }
    Column(modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(Params.label(param), Modifier.weight(1f))
            Badge(statusLabel(status), statusColor(status))
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value?.let { animatedParam(param, it) } ?: "-", fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 36.sp, letterSpacing = (-1).sp, color = Luluco.Cream)
            Spacer(Modifier.width(6.dp))
            Text(Params.unit(param), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
            Spacer(Modifier.weight(1f))
            // minilinha das ultimas medicoes + quanto mudou desde a anterior
            if (series.size >= 2) Column(horizontalAlignment = Alignment.End) {
                Sparkline(series, series.first().first, series.last().first, Luluco.TextSoft, Modifier.width(78.dp).height(26.dp), band = range?.let { it.idealMin to it.idealMax }, colorOf = range?.let { r -> { v: Double -> zoneColor(v, r) } })
                val d = series.last().second - series[series.size - 2].second
                DeltaBadge(Params.format(param, kotlin.math.abs(d)), if (d > 0) 1 else if (d < 0) -1 else 0)
            }
        }
        if (range != null) RangeBar(value, range, statusColor(status))
        Muted(if (at != null) "${fmtDateTime(at)} (${fmtAgo(at, now)})" else "sem medição", maxLines = 1)
        if (daysToExit != null) Text("sai da faixa em ~${maxOf(1, daysToExit.roundToInt())} dias", color = Luluco.CoralText, fontSize = 12.sp)
    }
}

/** Barra da faixa: zona de alerta ao fundo, faixa ideal destacada e marcador do valor. */
@Composable
fun RangeBar(value: Double?, r: ParamRange, markerColor: Color) {
    val pad = (r.alertaMax - r.alertaMin) * 0.25
    val lo = r.alertaMin - pad
    val hi = r.alertaMax + pad
    fun frac(v: Double) = ((v - lo) / (hi - lo)).coerceIn(0.0, 1.0).toFloat()
    Canvas(Modifier.fillMaxWidth().height(16.dp).padding(vertical = 6.dp)) {
        val h = size.height
        val radius = CornerRadius(h / 2, h / 2)
        // pontas: critico em vermelho forte, atencao em vermelho apagado; meio (ideal) cinza claro
        drawRoundRect(Luluco.ZoneCrit, size = size, cornerRadius = radius)
        drawRect(Luluco.ZoneWarn, topLeft = Offset(frac(r.alertaMin) * size.width, 0f), size = Size((frac(r.alertaMax) - frac(r.alertaMin)) * size.width, h))
        drawRect(Luluco.ZoneIdeal, topLeft = Offset(frac(r.idealMin) * size.width, 0f), size = Size((frac(r.idealMax) - frac(r.idealMin)) * size.width, h))
        if (value != null) {
            val x = frac(value) * size.width
            drawCircle(Luluco.Card, radius = h * 1.6f, center = Offset(x, h / 2))
            drawCircle(markerColor, radius = h * 1.05f, center = Offset(x, h / 2))
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
    Panel(modifier, padding = 14.dp) {
        Text(value, fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 28.sp, letterSpacing = (-0.8).sp, color = Luluco.Cream)
        Muted(label, maxLines = 2)
    }
}

/** "▲ 0,05 desde a anterior" no formato do parametro. */
/** Cor da zona onde o valor cai: verde (ideal), laranja (fora do ideal), vermelho (critico). */
fun zoneColor(v: Double, r: ParamRange): Color = when {
    v >= r.idealMin && v <= r.idealMax -> Luluco.ZoneIdeal
    v >= r.alertaMin && v <= r.alertaMax -> Luluco.ZoneWarn
    else -> Luluco.ZoneCrit
}

/** Atividade ja marcada: corrigir a hora em que foi feita, ou desfazer. */
@Composable
private fun DoneDialog(vm: MainViewModel, o: Occurrence, close: () -> Unit) {
    var quando by remember { mutableLongStateOf(o.log?.feitoEm ?: o.dueAt) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(o.routine.titulo) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (o.state == OccState.FEITO) {
                    DateTimeField("Feito em", quando) { quando = it }
                    Muted("Ajuste se você fez em outro horário.")
                } else Muted(if (o.state == OccState.PULADO) "Marcada como pulada." else "Adiada.")
                TextButton(onClick = { vm.undo(o); close() }) { Text(if (o.state == OccState.FEITO) "Desfazer (não fiz)" else "Desfazer", color = Luluco.CoralText) }
            }
        },
        confirmButton = { TextButton(onClick = { if (o.state == OccState.FEITO) vm.changeDoneTime(o, quando); close() }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = close) { Text("Cancelar") } },
    )
}

/** Bloco curto de financas no Painel: caixinha, gastos e cartao do ciclo atual. */
@Composable
private fun FinanceBlock(vm: MainViewModel, nav: Nav) {
    val all by vm.finance.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val ciclo = cycleOf(System.currentTimeMillis(), settings.cicloDia, vm.zone)
    val doCiclo = all.filter { cycleOf(it.lancadoEm, settings.cicloDia, vm.zone) == ciclo }
    val gastos = doCiclo.filter { it.tipo == "despesa" }.sumOf { it.valor }
    val cartao = doCiclo.filter { it.tipo == "despesa" && it.forma == "cartao" }.sumOf { it.valor }
    val caixa = all.sumOf { caixaDelta(it) }
    // Saldo zerado por nunca ter tido movimento e diferente de saldo zerado por ter gastado tudo.
    // Compra no cartao nao mexe na caixinha, entao quem so compra no cartao ve R$ 0,00 e acha que o
    // numero nao esta funcionando. Nesse caso o bloco diz o porque em vez de so mostrar o zero.
    val caixaNunca = all.none { caixaDelta(it) != 0.0 }
    // em aberto conta todas as parcelas futuras, nao so as do ciclo: e o que ainda vai sair
    val aPagar = all.filter { it.emAberto() }.sumOf { it.valor }
    // Tres numeros, e nao cinco. Na coluna estreita do Painel, icone mais quatro celulas davam uns
    // 70 px para cada uma: "R$ 294,66" em 20sp nao cabia e o texto era cortado no meio do primeiro
    // caractere, virando um "R" solto. "No cartao" saiu porque e o unico que da para deduzir dos
    // outros, e a fonte caiu para 17sp com reticencias, para nunca mais cortar calado.
    Panel(Modifier.fillMaxWidth().clickable { nav.top(Screen.Financas) }, padding = 0.dp) {
        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.padding(start = 14.dp)) { IconeRedondo(androidx.compose.material.icons.Icons.Outlined.ShoppingCart, Luluco.AreaFinancas) }
            FinCell(Modifier.weight(1f), "Caixinha", caixa, nota = if (caixaNunca) "nenhum depósito ainda" else null)
            VerticalDivider(color = Luluco.Divider)
            FinCell(Modifier.weight(1f), "Gastos do ciclo", gastos)
            VerticalDivider(color = Luluco.Divider)
            FinCell(Modifier.weight(1f), "A pagar", aPagar, if (aPagar > 0) Luluco.ZoneWarn else null,
                nota = if (cartao > 0) "no cartão ${money(cartao)}" else null)
        }
    }
}

/**
 * Icone dentro de um circulo tingido, do Gluco. Substitui o icone com brilho atras: o brilho
 * espalhava luz sobre o texto vizinho numa tela que fica acesa o dia inteiro, e o circulo da a
 * mesma ancora visual sem vazar nada.
 */
@Composable
fun IconeRedondo(icon: androidx.compose.ui.graphics.vector.ImageVector, cor: Color, size: Dp = 34.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(cor.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = cor, modifier = Modifier.size(size * 0.52f))
    }
}

@Composable
private fun FinCell(modifier: Modifier, label: String, v: Double, color: Color? = null, nota: String? = null) {
    Column(modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
        Eyebrow(label)
        Text(money(v), fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, letterSpacing = (-0.3).sp,
            color = color ?: Luluco.Cream, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (nota != null) Text(nota, fontSize = 11.sp, color = Luluco.TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Aviso de versao nova, no alto do Painel. Baixa e abre o instalador sem sair do app: o tablet
 * fica longe do computador e passar o APK por cabo a cada build nao se sustenta.
 *
 * Nao atualiza sozinho de proposito. Instalar troca o app que esta na frente, e isso no meio de
 * uma dosagem ou de uma TPA seria pior do que ficar um dia na versao velha. Quem decide a hora e
 * ele, com um toque.
 */
@Composable
private fun AvisoDeAtualizacao() {
    val ctx = LocalContext.current
    var versao by remember { mutableStateOf<VersaoRemota?>(null) }
    val estado by Atualizacao.estado.collectAsStateWithLifecycle()
    // lido uma vez por abertura do app, e nao a cada volta ao Painel
    val anterior = remember { br.com.lunelli.lulucoreef.update.VersaoVista.acabouDeAtualizar(ctx.applicationContext) }
    var mostrarConfirmacao by remember { mutableStateOf(anterior != null) }

    LaunchedEffect(Unit) { versao = Atualizacao.checar(ctx)?.takeIf { it.nova } }

    if (mostrarConfirmacao && versao == null) {
        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Luluco.ZoneIdeal))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Atualizado para a ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.SemiBold, color = Luluco.Cream)
                    Text("Estava na 0.0.$anterior. O app fechou e abriu sozinho para trocar de versão: é o normal.", fontSize = 13.sp, color = Luluco.TextSoft)
                }
                TextButton(onClick = { mostrarConfirmacao = false }) { Text("Ok") }
            }
        }
    }

    val v = versao ?: return
    Panel(Modifier.fillMaxWidth().cascade(0)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (estado is Atualizacao.Estado.Falhou) Luluco.ZoneWarn else Luluco.ZoneIdeal))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Versão ${v.versao} disponível", fontWeight = FontWeight.SemiBold, color = Luluco.Cream)
                val (detalhe, cor) = when (val e = estado) {
                    is Atualizacao.Estado.Baixando ->
                        (if (e.tentativa > 1) "Baixando… ${e.pct}% (tentativa ${e.tentativa})" else "Baixando… ${e.pct}%") to Luluco.TextSoft
                    is Atualizacao.Estado.Falhou -> e.motivo to Luluco.ZoneWarn
                    // Na atualizacao o Android fecha o app que esta rodando para trocar os
                    // arquivos: isso e normal e nao da para evitar sem permissao de sistema.
                    Atualizacao.Estado.Pronto -> "Conferido. Toque em Instalar na tela do sistema; o app vai fechar sozinho, é só abrir de novo." to Luluco.TextSoft
                    Atualizacao.Estado.Parado -> (v.notas.ifBlank { "Você está na ${BuildConfig.VERSION_NAME}." }) to Luluco.TextSoft
                }
                Text(detalhe, fontSize = 13.sp, color = cor, maxLines = 3)
                // A porcentagem e lida AQUI, na composicao, e nao dentro do lambda do progresso.
                // O lambda e chamado no desenho e na acessibilidade, que podem rodar depois de o
                // estado ja ter virado "Pronto": um "estado as Baixando" ali derruba o app.
                val pct = (estado as? Atualizacao.Estado.Baixando)?.pct
                if (estado is Atualizacao.Estado.Baixando) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (estado as Atualizacao.Estado.Baixando).pct / 100f },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                        color = Luluco.Pool, trackColor = Luluco.Solid,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            when (estado) {
                is Atualizacao.Estado.Baixando -> {}
                is Atualizacao.Estado.Falhou -> Button(onClick = { Atualizacao.iniciar(ctx, v) }) { Text("Continuar") }
                Atualizacao.Estado.Pronto -> Button(onClick = { Atualizacao.iniciar(ctx, v) }) { Text("Instalar") }
                Atualizacao.Estado.Parado -> Button(onClick = { Atualizacao.iniciar(ctx, v) }) { Text("Atualizar") }
            }
        }
    }
}

/**
 * Se o app caiu da ultima vez, mostra o erro no alto do Painel ate ser dispensado. E o unico jeito
 * de ver por que ele fechou sozinho no tablet, sem cabo nem computador: tira um print e manda.
 */
@Composable
private fun AvisoDeQueda() {
    val ctx = LocalContext.current
    var texto by remember { mutableStateOf(br.com.lunelli.lulucoreef.diag.Queda.ultima(ctx)) }
    val t = texto ?: return
    Panel(Modifier.fillMaxWidth(), borderColor = Luluco.ZoneWarn.copy(alpha = 0.5f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("O app fechou sozinho da última vez", fontWeight = FontWeight.SemiBold, color = Luluco.Cream)
                Text("Tire um print desta caixa e mande no chat: é o motivo exato.", fontSize = 12.sp, color = Luluco.TextSoft)
            }
            TextButton(onClick = { br.com.lunelli.lulucoreef.diag.Queda.limpar(ctx); texto = null }) { Text("Dispensar") }
        }
        Spacer(Modifier.height(6.dp))
        Text(t.lines().take(9).joinToString("\n"), fontSize = 11.sp, color = Luluco.ZoneWarn, lineHeight = 14.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
    }
}
