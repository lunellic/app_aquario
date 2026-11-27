package br.com.lunelli.lulucoreef.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Close
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lunelli.lulucoreef.domain.Occurrence
import br.com.lunelli.lulucoreef.domain.isOnce
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.lunelli.lulucoreef.data.Product
import br.com.lunelli.lulucoreef.data.Routine
import br.com.lunelli.lulucoreef.data.RoutineLog
import br.com.lunelli.lulucoreef.domain.OccState
import br.com.lunelli.lulucoreef.domain.agendaOn
import br.com.lunelli.lulucoreef.domain.doseFor
import br.com.lunelli.lulucoreef.domain.formatDose
import br.com.lunelli.lulucoreef.domain.toLocalDate
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Cor do tipo de atividade (mesmas cores das areas do menu). */
fun routineColor(tipo: String): Color = when (tipo) {
    "dosagem" -> Luluco.AreaDose
    "tpa" -> Luluco.AreaTpa
    "teste" -> Luluco.AreaTeste
    "manutencao" -> Luluco.AreaEquip
    else -> Luluco.AreaJornal
}

/**
 * Grade do mes no estilo sobrio: mes numa pilula contornada, dias grandes, fim de semana mais apagado,
 * hoje em pilula cheia, semana escolhida numa faixa. Embaixo de cada dia, um pontinho por tipo de atividade.
 */
@Composable
fun MonthGrid(
    month: YearMonth, onMonth: (YearMonth) -> Unit, selected: LocalDate?, today: LocalDate,
    routines: List<Routine>, logs: List<RoutineLog>, zone: ZoneId, modifier: Modifier = Modifier, big: Boolean = false, onPick: (LocalDate) -> Unit,
) {
    val types = remember(routines, logs, month) {
        val first = month.atDay(1).minusDays(7); val last = month.atEndOfMonth().plusDays(7)
        generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }
            .associateWith { d -> agendaOn(routines, logs, d, zone).map { it.routine.tipo }.distinct().sortedBy { listOf("tpa", "teste", "manutencao", "dosagem").indexOf(it).let { i -> if (i < 0) 9 else i } }.take(4) }
    }
    val cellH = if (big) 56.dp else 44.dp
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonth(month.minusMonths(1)) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Mês anterior", tint = Luluco.TextSoft) }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", PtBr)).replaceFirstChar { it.uppercase() }, fontSize = if (big) 20.sp else 17.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, Luluco.CardBorder, RoundedCornerShape(50)).clickable { onMonth(YearMonth.from(today)); onPick(today) }.padding(horizontal = 18.dp, vertical = 6.dp))
            }
            IconButton(onClick = { onMonth(month.plusMonths(1)) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Próximo mês", tint = Luluco.TextSoft) }
        }
        Spacer(Modifier.height(8.dp))
        Row {
            listOf("DOM", "SEG", "TER", "QUA", "QUI", "SEX", "SÁB").forEachIndexed { i, d ->
                Text(d, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, letterSpacing = 0.8.sp, fontWeight = FontWeight.Medium, color = if (i == 0 || i == 6) Luluco.TextFaint else Luluco.TextSoft)
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Luluco.Divider)
        val start = month.atDay(1).minusDays((month.atDay(1).dayOfWeek.value % 7).toLong()) // semana comeca no domingo
        for (w in 0 until 6) {
            val weekStart = start.plusDays(w * 7L)
            if (w == 5 && weekStart.month != month.month) break
            val selWeek = selected != null && !selected.isBefore(weekStart) && selected.isBefore(weekStart.plusDays(7))
            Row(Modifier.padding(vertical = 2.dp).clip(RoundedCornerShape(50)).background(if (selWeek) Color.White.copy(alpha = 0.045f) else Color.Transparent)) {
                for (c in 0..6) {
                    val date = weekStart.plusDays(c.toLong())
                    val inMonth = date.month == month.month
                    val isToday = date == today
                    val isSel = date == selected
                    val weekend = c == 0 || c == 6
                    Box(Modifier.weight(1f).height(cellH).clickable { onPick(date) }, contentAlignment = Alignment.Center) {
                        val pill = Modifier.size(if (big) 44.dp else 36.dp).clip(CircleShape)
                        Box(
                            when {
                                isToday -> pill.background(Luluco.Royal)
                                isSel -> pill.border(1.dp, Luluco.TextSoft, CircleShape)
                                else -> pill
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(date.dayOfMonth.toString(), fontSize = if (big) 19.sp else 16.sp,
                                fontWeight = if (isToday || isSel) FontWeight.SemiBold else FontWeight.Normal,
                                color = when { isToday -> Color.White; !inMonth -> Luluco.TextFaint.copy(alpha = 0.6f); weekend -> Luluco.TextSoft; else -> Luluco.Cream })
                        }
                        val t = types[date].orEmpty()
                        if (t.isNotEmpty() && inMonth) Row(Modifier.align(Alignment.BottomCenter).padding(bottom = if (big) 2.dp else 1.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            t.forEach { Box(Modifier.size(4.dp).clip(CircleShape).background(routineColor(it).copy(alpha = 0.85f))) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Legend4() {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf("dosagem" to "Doses", "tpa" to "TPA", "teste" to "Testes", "manutencao" to "Manutenção", "outro" to "Outras").forEach { (t, l) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(routineColor(t)))
                Spacer(Modifier.width(5.dp))
                Text(l, fontSize = 12.sp, color = Luluco.TextSoft)
            }
        }
    }
}

/** Card do Painel: so a grade. Tocar num dia abre a tela Calendario naquele dia. */
@Composable
fun CalendarCard(nav: Nav, routines: List<Routine>, logs: List<RoutineLog>, products: Map<String, Product>, volume: Double, now: Long, zone: ZoneId, wide: Boolean) {
    val today = now.toLocalDate(zone)
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    Panel(Modifier.fillMaxWidth()) {
        MonthGrid(month, { month = it }, null, today, routines, logs, zone) { d -> nav.go(Screen.Calendario(d.toString())) }
        Spacer(Modifier.height(6.dp))
        Legend4()
    }
}

/** Tela Calendario: mes grande a esquerda, o dia escolhido a direita com adicionar e retirar. */
@Composable
fun CalendarioScreen(vm: MainViewModel, nav: Nav, dia: String?) {
    val routines by vm.routines.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val zone = vm.zone
    val today = now.toLocalDate(zone)
    var selected by remember(dia) { mutableStateOf(dia?.let { LocalDate.parse(it) } ?: today) }
    var month by remember(dia) { mutableStateOf(YearMonth.from(selected)) }
    val productById = remember(products) { products.associateBy { it.id } }
    val agenda = remember(routines, logs, selected) { agendaOn(routines, logs, selected, zone).sortedBy { it.dueAt } }
    var removing by remember { mutableStateOf<Occurrence?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Calendário", onBack = if (dia != null) ({ nav.back() }) else null)
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Panel(Modifier.weight(1.1f)) {
                MonthGrid(month, { month = it }, selected, today, routines, logs, zone, big = true) { selected = it; month = YearMonth.from(it) }
                Spacer(Modifier.height(8.dp))
                Legend4()
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val label = when (selected) {
                    today -> "Hoje"
                    today.plusDays(1) -> "Amanhã"
                    today.minusDays(1) -> "Ontem"
                    else -> selected.format(DateTimeFormatter.ofPattern("EEEE", PtBr)).replaceFirstChar { it.uppercase() }
                }
                Column {
                    Eyebrow(selected.format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", PtBr)))
                    Text(label, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp)
                }
                if (agenda.isEmpty()) Muted("Nada marcado neste dia.")
                else Panel(Modifier.fillMaxWidth(), padding = 0.dp) {
                    agenda.forEachIndexed { i, o ->
                        if (i > 0) HorizontalDivider(color = Luluco.Divider)
                        val p = o.routine.productId?.let { productById[it] }
                        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(routineColor(o.routine.tipo)))
                            Spacer(Modifier.width(10.dp))
                            Text(if (o.semHorario) "" else fmtTime(o.dueAt), color = Luluco.TextSoft, fontSize = 14.sp, modifier = Modifier.width(48.dp))
                            Column(Modifier.weight(1f)) {
                                Text(o.routine.titulo, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (o.state == OccState.PULADO) Luluco.TextFaint else Luluco.Cream,
                                    textDecoration = if (o.state == OccState.PULADO) TextDecoration.LineThrough else null)
                                if (o.routine.tipo == "dosagem" && p != null) Muted(formatDose(doseFor(p, vm.volume), p.doseUnidade))
                            }
                            when (o.state) {
                                OccState.FEITO -> Badge("feito", Luluco.Ok)
                                OccState.PULADO -> Badge("retirada", Luluco.Neutral)
                                else -> if (o.lateAt < now) Badge("pendente", Luluco.CoralText)
                            }
                            if (o.state != OccState.FEITO && o.state != OccState.PULADO) IconButton(onClick = { removing = o }) { Icon(Icons.Rounded.Close, "Retirar", tint = Luluco.TextSoft) }
                            else if (o.state == OccState.PULADO) TextButton(onClick = { vm.undo(o) }) { Text("Voltar") }
                        }
                    }
                }
                OutlinedButton(onClick = { nav.go(Screen.RoutineForm(onceDate = selected.toString())) }) { Text("Adicionar atividade neste dia") }
            }
        }
    }
    removing?.let { o ->
        val once = o.routine.isOnce()
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Retirar ${o.routine.titulo}") },
            text = { Muted(if (once) "Atividade de uma vez só: ela é apagada." else "É uma rotina que se repete. Retire só deste dia ou apague a rotina inteira.") },
            confirmButton = {
                TextButton(onClick = { if (once) vm.delete(o.routine) else vm.skip(o); removing = null }) { Text(if (once) "Apagar" else "Só deste dia") }
            },
            dismissButton = {
                Row {
                    if (!once) TextButton(onClick = { vm.delete(o.routine); removing = null }) { Text("Apagar rotina", color = Luluco.CoralText) }
                    TextButton(onClick = { removing = null }) { Text("Cancelar") }
                }
            },
        )
    }
}
