package br.com.lunelli.lulucoreef.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lunelli.lulucoreef.data.reliable
import br.com.lunelli.lulucoreef.domain.DAY_MS
import br.com.lunelli.lulucoreef.domain.OccState
import br.com.lunelli.lulucoreef.domain.Params
import br.com.lunelli.lulucoreef.domain.Status
import br.com.lunelli.lulucoreef.domain.allAlerts
import br.com.lunelli.lulucoreef.domain.doseFor
import br.com.lunelli.lulucoreef.domain.formatDose
import br.com.lunelli.lulucoreef.domain.healthScore
import br.com.lunelli.lulucoreef.domain.latestOf
import br.com.lunelli.lulucoreef.domain.occurrencesOn
import br.com.lunelli.lulucoreef.domain.statusOf
import br.com.lunelli.lulucoreef.domain.numShort
import br.com.lunelli.lulucoreef.domain.toLocalDate
import br.com.lunelli.lulucoreef.domain.upcoming
import br.com.lunelli.lulucoreef.domain.whenText
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.format.DateTimeFormatter

/** Bloco arredondado da vitrine, legivel de longe sobre a foto ou o fundo de agua. */
@Composable
private fun VBlock(modifier: Modifier = Modifier, borderColor: Color = Luluco.CardBorder, pad: androidx.compose.ui.unit.Dp = 18.dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Column(modifier.clip(shape).background(Luluco.Navy.copy(alpha = 0.55f)).border(1.5.dp, borderColor, shape).padding(pad), verticalArrangement = Arrangement.Center, content = content)
}

/**
 * Standby (vitrine): entra sozinho depois de um tempo sem toque. Nota de saude em anel, ultima TPA,
 * proximos passos, os parametros escolhidos com mini grafico de 30 dias, proxima rotina e o alerta mais grave.
 */
@Composable
fun VitrineScreen(vm: MainViewModel) {
    val now by vm.now.collectAsStateWithLifecycle()
    val tank by vm.tank.collectAsStateWithLifecycle()
    val ranges by vm.ranges.collectAsStateWithLifecycle()
    val allTests by vm.tests.collectAsStateWithLifecycle()
    val changes by vm.changes.collectAsStateWithLifecycle()
    val routines by vm.routines.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    val cases by vm.cases.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val zone = vm.zone
    val tests = remember(allTests) { allTests.reliable() }

    // Anti burn-in: desloca o conteudo alguns pixels a cada 3 minutos.
    var shift by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(180_000)
            shift = (shift + 1) % 8
        }
    }
    val dx = listOf(0, 6, 12, 6, 0, -6, -12, -6)[shift]
    val dy = listOf(0, 5, 0, -5, -10, -5, 0, 5)[shift]

    val today = occurrencesOn(routines, logs, now.toLocalDate(zone), zone)
    val worst = allAlerts(allTests, ranges, changes, today, cases, entries, now).firstOrNull()
    val score = healthScore(allTests, ranges, changes, routines, logs, cases, now, zone).score
    val foto = tank?.fotoVitrinePath

    Box(Modifier.fillMaxSize()) {
        if (foto != null) {
            // foto em baixa resolucao esticada = desfoque leve, sem custo; degrade azul da marca por cima
            Photo(foto, Modifier.fillMaxSize(), maxPx = 120)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Luluco.DeepTop.copy(alpha = 0.70f), Luluco.DeepBottom.copy(alpha = 0.82f)))))
        } else {
            WaterBackground(Modifier.fillMaxSize(), strength = 1.3f)
        }

        val shown = Params.ALL.filter { it in settings.shownParams }
        val worstNow = worst
        val compact = shown.size > 4 || today.size > 3 || worstNow != null
        val next = remember(routines, logs, now / 60_000) { upcoming(routines, logs, now, zone, limit = 3) }
        val zdt = Instant.ofEpochMilli(now).atZone(zone)
        val productById = remember(products) { products.associateBy { it.id } }

        // Hora, dia da semana e data: texto solto, sem bloco em volta
        val clock: @Composable (Modifier) -> Unit = { m ->
            Column(m.padding(horizontal = 8.dp)) {
                AnimatedContent(fmtTime(now), transitionSpec = { (fadeIn(tween(600)) + slideInVertically(tween(600)) { it / 6 }) togetherWith (fadeOut(tween(400)) + slideOutVertically(tween(600)) { -it / 6 }) }, label = "relogio") { t ->
                    Text(t, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 84.sp, lineHeight = 88.sp, color = Luluco.Cream, maxLines = 1)
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(zdt.format(DateTimeFormatter.ofPattern("EEEE", PtBr)).replaceFirstChar { it.uppercase() }, fontFamily = Fredoka, fontSize = 24.sp, color = Luluco.PoolText)
                    Spacer(Modifier.width(10.dp))
                    Text(zdt.format(DateTimeFormatter.ofPattern("d 'de' MMMM", PtBr)), fontSize = 17.sp, color = Luluco.Cream, modifier = Modifier.padding(bottom = 3.dp))
                }
            }
        }
        // Hoje: a lista completa do dia, com o que ja foi feito
        val hoje: @Composable (Modifier) -> Unit = { m ->
            VBlock(m, borderColor = if (today.any { it.isDue(now) }) Luluco.Coral else Luluco.CardBorder, pad = 14.dp) {
                Text(if (today.isEmpty()) "Hoje" else "Hoje · $doneToday de $totalToday feitas", fontFamily = Fredoka, fontSize = 20.sp, color = Luluco.PoolText)
                if (today.isEmpty()) Text("Nenhuma rotina para hoje.", fontSize = 16.sp, color = Luluco.TextSoft)
                val perRow = if (today.size > 3) 2 else 1
                today.chunked(perRow).forEach { rowItems ->
                    Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        rowItems.forEach { o ->
                            val p = o.routine.productId?.let { productById[it] }
                            val dose = if (o.routine.tipo == "dosagem" && p != null) " " + formatDose(doseFor(p, vm.volume), p.doseUnidade) else ""
                            val color = when {
                                o.state == OccState.FEITO -> Luluco.Ok
                                o.state == OccState.PULADO -> Luluco.Neutral
                                o.isDue(now) -> Luluco.CoralText
                                else -> Luluco.Cream
                            }
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (o.state == OccState.FEITO) "feito" else if (o.state == OccState.PULADO) "pulou" else fmtTime(o.effectiveDueAt), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.width(48.dp), maxLines = 1)
                                Text(o.routine.titulo + dose, fontSize = 16.sp, color = if (o.open) Luluco.Cream else Luluco.TextSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (rowItems.size < perRow) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        val lastTpa = changes.filter { it.feitaEm <= now }.maxByOrNull { it.feitaEm }
        val (doneToday, totalToday) = today.count { it.state == OccState.FEITO } to today.size

        // Saude em anel + resumo do dia
        val ring: @Composable (Modifier) -> Unit = { m ->
            VBlock(m) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HealthRing(score, 104.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column {
                            Text("Última TPA", fontSize = 15.sp, color = Luluco.TextSoft)
                            Text(lastTpa?.let { "há ${(now - it.feitaEm) / DAY_MS} dias · ${numShort(it.litros)} L" } ?: "nenhuma registrada",
                                fontFamily = Fredoka, fontSize = 22.sp, color = if (lastTpa != null && now - lastTpa.feitaEm > 9 * DAY_MS) Luluco.CoralText else Luluco.Cream, maxLines = 1)
                        }
                        Column {
                            Text("Rotinas de hoje", fontSize = 15.sp, color = Luluco.TextSoft)
                            Text(if (totalToday == 0) "nenhuma" else "$doneToday de $totalToday feitas", fontFamily = Fredoka, fontSize = 22.sp, color = Luluco.Cream, maxLines = 1)
                        }
                    }
                }
            }
        }
        // Proximos passos: o que esta chegando
        val steps: @Composable (Modifier) -> Unit = { m ->
            VBlock(m, pad = 14.dp) {
                Text("Próximos passos", fontFamily = Fredoka, fontSize = 20.sp, color = Luluco.PoolText)
                if (next.isEmpty()) Text("Nada agendado nos próximos dias.", fontSize = 17.sp, color = Luluco.TextSoft)
                next.forEach { item ->
                    Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(whenText(item.dueAt, now, zone), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (item.late) Luluco.CoralText else Luluco.TextSoft, modifier = Modifier.width(132.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(item.routine.titulo, fontSize = 16.sp, color = Luluco.Cream, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        val param: @Composable (Modifier, String) -> Unit = { m, p ->
            val last = tests.latestOf(p)
            val r = ranges[p]
            val st = if (last == null || r == null) Status.SEM_MEDICAO else statusOf(last.valor, r)
            val series = remember(tests, now / 3_600_000) { tests.filter { it.parametro == p && now - it.medidoEm <= 30 * DAY_MS }.sortedBy { it.medidoEm }.map { it.medidoEm to it.valor } }
            VBlock(m, borderColor = if (st == Status.CRITICO || st == Status.ATENCAO) statusColor(st) else Luluco.CardBorder, pad = if (compact) 12.dp else 18.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Params.label(p), fontSize = 17.sp, color = Luluco.Cream, modifier = Modifier.weight(1f), maxLines = 1)
                    Badge(statusLabel(st), statusColor(st))
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(last?.let { Params.format(p, it.valor) } ?: "-", fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = if (compact) 34.sp else 46.sp, color = statusColor(st), maxLines = 1)
                    Spacer(Modifier.width(6.dp))
                    Text(Params.unit(p), fontSize = 15.sp, color = Luluco.TextSoft, modifier = Modifier.padding(bottom = if (compact) 5.dp else 9.dp))
                }
                Sparkline(series, now - 30 * DAY_MS, now, statusColor(st), Modifier.fillMaxWidth().height(if (compact) 22.dp else 34.dp), band = r?.let { it.idealMin to it.idealMax })
            }
        }
        val alert: @Composable (Modifier) -> Unit = { m ->
            if (worst != null) VBlock(m, borderColor = statusColor(worst.severity), pad = 12.dp) {
                Text(statusLabel(worst.severity) + ": " + worst.fato, fontSize = 16.sp, color = statusColor(worst.severity), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }

        BoxWithConstraints(Modifier.fillMaxSize().offset(dx.dp, dy.dp).padding(26.dp)) {
            val gap = 14.dp
            if (maxWidth > maxHeight) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                        clock(Modifier.fillMaxWidth())
                        ring(Modifier.fillMaxWidth())
                        steps(Modifier.fillMaxWidth())
                    }
                    Column(Modifier.weight(1.25f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                        shown.chunked(2).forEach { pair ->
                            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                                pair.forEach { p -> key(p) { param(Modifier.weight(1f).fillMaxHeight(), p) } }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                        hoje(Modifier.fillMaxWidth())
                        if (worst != null) alert(Modifier.fillMaxWidth())
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    clock(Modifier.fillMaxWidth())
                    ring(Modifier.fillMaxWidth())
                    shown.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            pair.forEach { p -> key(p) { param(Modifier.weight(1f), p) } }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    hoje(Modifier.fillMaxWidth())
                    alert(Modifier.fillMaxWidth())
                    steps(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/**
 * Modo noite: azul bem escuro (nao preto) com ondas suaves, brilho baixo, sem numeros nem foto.
 * Mostra so o nome e, se houver, o proximo passo, em tom discreto. As ondas se movem, entao nao marcam a tela.
 */
@Composable
fun NightScreen(vm: MainViewModel) {
    val now by vm.now.collectAsStateWithLifecycle()
    val routines by vm.routines.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? Activity
    DisposableEffect(Unit) {
        setBrightness(activity, 0.12f)
        onDispose { setBrightness(activity, WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) }
    }
    val next = remember(routines, logs, now / 600_000) { upcoming(routines, logs, now, vm.zone, limit = 1).firstOrNull() }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF041033), Color(0xFF071F52))))) {
        Waves(Modifier.fillMaxWidth().fillMaxHeight(0.42f).align(Alignment.BottomCenter), color = Luluco.Pool)
        Waves(Modifier.fillMaxWidth().fillMaxHeight(0.22f).align(Alignment.BottomCenter), color = Luluco.Royal)
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Luluco Reef", fontFamily = Fredoka, fontSize = 40.sp, color = Luluco.Cream.copy(alpha = 0.45f))
            Text("modo noite", fontSize = 16.sp, color = Luluco.TextSoft.copy(alpha = 0.55f))
            if (next != null) {
                Spacer(Modifier.height(14.dp))
                Text("${whenText(next.dueAt, now, vm.zone)} · ${next.routine.titulo}", fontSize = 17.sp, color = Luluco.TextSoft.copy(alpha = 0.6f))
            }
        }
    }
}

fun setBrightness(activity: Activity?, value: Float) {
    val w = activity?.window ?: return
    val lp = w.attributes
    lp.screenBrightness = value
    w.attributes = lp
}
