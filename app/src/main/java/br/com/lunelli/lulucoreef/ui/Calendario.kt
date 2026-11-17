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

/**
 * Calendario do Painel: toque num dia para ver o que esta marcado e adicionar uma atividade de uma vez so.
 * O ponto no dia indica TPA, teste, manutencao ou atividade marcada (as dosagens diarias nao poluem o mes).
 */
@Composable
fun CalendarCard(nav: Nav, routines: List<Routine>, logs: List<RoutineLog>, products: Map<String, Product>, volume: Double, now: Long, zone: ZoneId, wide: Boolean) {
    val today = now.toLocalDate(zone)
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var selected by remember { mutableStateOf(today) }
    val marks = remember(routines, logs, month) {
        val nonDosing = routines.filter { it.tipo != "dosagem" }
        (1..month.lengthOfMonth()).associateWith { d -> agendaOn(nonDosing, logs, month.atDay(d), zone).size }
    }
    val agenda = remember(routines, logs, selected) { agendaOn(routines, logs, selected, zone) }

    val grid: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Mês anterior", tint = Luluco.Cream) }
                Text(month.format(DateTimeFormatter.ofPattern("MMMM 'de' yyyy", PtBr)).replaceFirstChar { it.uppercase() }, fontFamily = Fredoka, fontSize = 20.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Próximo mês", tint = Luluco.Cream) }
            }
            Row {
                listOf("D", "S", "T", "Q", "Q", "S", "S").forEach { Text(it, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 13.sp, color = Luluco.TextSoft) }
            }
            val offset = month.atDay(1).dayOfWeek.value % 7 // semana comeca no domingo
            val cells = offset + month.lengthOfMonth()
            for (week in 0 until (cells + 6) / 7) {
                Row {
                    for (col in 0..6) {
                        val day = week * 7 + col - offset + 1
                        if (day < 1 || day > month.lengthOfMonth()) {
                            Spacer(Modifier.weight(1f).height(42.dp))
                        } else {
                            val date = month.atDay(day)
                            val isSel = date == selected
                            val isToday = date == today
                            Box(
                                Modifier.weight(1f).height(42.dp).padding(2.dp).clip(RoundedCornerShape(12.dp))
                                    .background(if (isSel) Luluco.Pool else Color.Transparent)
                                    .then(if (isToday && !isSel) Modifier.border(1.5.dp, Luluco.CoralText, RoundedCornerShape(12.dp)) else Modifier)
                                    .clickable { selected = date },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(day.toString(), fontSize = 15.sp, fontWeight = if (isToday || isSel) FontWeight.Bold else FontWeight.Normal, color = if (isSel) Luluco.Navy else Luluco.Cream)
                                if ((marks[day] ?: 0) > 0) {
                                    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(6.dp).clip(CircleShape).background(if (isSel) Luluco.Navy else Luluco.CoralText))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    val list: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val label = when (selected) {
                today -> "Hoje"
                today.plusDays(1) -> "Amanhã"
                else -> selected.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", PtBr)).replaceFirstChar { it.uppercase() }
            }
            Text(label, fontFamily = Fredoka, fontSize = 20.sp, color = Luluco.PoolText)
            if (agenda.isEmpty()) Muted("Nada marcado neste dia.")
            agenda.forEach { o ->
                val p = o.routine.productId?.let { products[it] }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(fmtTime(o.dueAt), fontWeight = FontWeight.Bold, color = Luluco.TextSoft, modifier = Modifier.width(54.dp), fontSize = 14.sp)
                    Text(o.routine.titulo + (if (o.routine.tipo == "dosagem" && p != null) "  ·  " + formatDose(doseFor(p, volume), p.doseUnidade) else ""),
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
                    when (o.state) {
                        OccState.FEITO -> Badge("feito", Luluco.Ok)
                        OccState.PULADO -> Badge("pulado", Luluco.Neutral)
                        else -> if (o.dueAt < now) Badge("pendente", Luluco.CoralText)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = { nav.go(Screen.RoutineForm(onceDate = selected.toString())) }) { Text("Adicionar atividade neste dia") }
        }
    }

    Panel(Modifier.fillMaxWidth()) {
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                grid(Modifier.weight(1f))
                list(Modifier.weight(1f).padding(top = 8.dp))
            }
        } else {
            grid(Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            list(Modifier.fillMaxWidth())
        }
    }
}
