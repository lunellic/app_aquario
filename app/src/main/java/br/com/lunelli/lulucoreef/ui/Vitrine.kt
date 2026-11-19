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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
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

    val shown = Params.ALL.filter { it in settings.shownParams }
    val lastTpa = changes.filter { it.feitaEm <= now }.maxByOrNull { it.feitaEm }
    val doneToday = today.count { it.state == OccState.FEITO }
    val next = remember(routines, logs, now / 60_000) { upcoming(routines, logs, now, zone, limit = 3) }
    val zdt = Instant.ofEpochMilli(now).atZone(zone)
    val productById = remember(products) { products.associateBy { it.id } }

    // Descanso minimalista: fundo liso, uma unica onda em fita atravessando a tela, texto solto e linhas finas.
    Box(Modifier.fillMaxSize().background(Luluco.Navy)) {
        if (foto != null) {
            Photo(foto, Modifier.fillMaxSize(), maxPx = 120)
            Box(Modifier.fillMaxSize().background(Luluco.Navy.copy(alpha = 0.88f)))
        }
        AtmosWave(Modifier.fillMaxWidth().fillMaxHeight(0.5f).align(Alignment.Center), alpha = 0.9f, fadeAt = 0.8f)

        Column(Modifier.fillMaxSize().offset(dx.dp, dy.dp).padding(horizontal = 40.dp, vertical = 32.dp)) {
        Row(Modifier.weight(1f)) {
            // esquerda: hora, data, saude e o que vem
            Column(Modifier.weight(1f).fillMaxHeight()) {
                AnimatedContent(fmtTime(now), transitionSpec = { (fadeIn(tween(900)) + slideInVertically(tween(900)) { it / 8 }) togetherWith (fadeOut(tween(600)) + slideOutVertically(tween(900)) { -it / 8 }) }, label = "relogio") { t ->
                    Text(t, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 124.sp, lineHeight = 124.sp, letterSpacing = (-4).sp, color = Luluco.Cream, maxLines = 1)
                }
                Eyebrow(zdt.format(DateTimeFormatter.ofPattern("EEEE · d 'de' MMMM", PtBr)), Modifier.padding(start = 4.dp))
                Spacer(Modifier.weight(1f))
                if (worst != null) {
                    Row(Modifier.padding(bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(statusColor(worst.severity)))
                        Spacer(Modifier.width(10.dp))
                        Text(worst.fato, fontSize = 15.sp, color = Luluco.Cream, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            // direita: hoje e proximos, em lista com linhas finas
            Column(Modifier.width(300.dp).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                Eyebrow(if (today.isEmpty()) "Hoje" else "Hoje  ·  $doneToday/${today.size}")
                Spacer(Modifier.height(8.dp))
                if (today.isEmpty()) Text("Nada para hoje.", fontSize = 15.sp, color = Luluco.TextSoft)
                today.forEachIndexed { i, o ->
                    if (i > 0) HorizontalDivider(color = Luluco.Divider, thickness = 1.dp)
                    val p = o.routine.productId?.let { productById[it] }
                    val dose = if (o.routine.tipo == "dosagem" && p != null) formatDose(doseFor(p, vm.volume), p.doseUnidade) else null
                    VRow(if (o.state == OccState.FEITO) "feito" else if (o.state == OccState.PULADO) "pulou" else fmtTime(o.effectiveDueAt),
                        o.routine.titulo, dose, done = !o.open, late = o.isDue(now))
                }
                if (next.isNotEmpty()) {
                    Spacer(Modifier.height(22.dp))
                    Eyebrow("Próximos")
                    Spacer(Modifier.height(8.dp))
                    next.forEachIndexed { i, item ->
                        if (i > 0) HorizontalDivider(color = Luluco.Divider, thickness = 1.dp)
                        VRow(whenText(item.dueAt, now, zone), item.routine.titulo, null, done = false, late = item.late, wide = true)
                    }
                }
            }
        }
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                VStat("Saúde", score?.toString() ?: "-", null, scoreColor(score))
                VStat("Última TPA", lastTpa?.let { "${(now - it.feitaEm) / DAY_MS}d" } ?: "-", lastTpa?.let { numShort(it.litros) + " L" },
                    if (lastTpa != null && now - lastTpa.feitaEm > 9 * DAY_MS) Luluco.CoralText else null)
                shown.forEach { p ->
                    val last = tests.latestOf(p)
                    val r = ranges[p]
                    val st = if (last == null || r == null) Status.SEM_MEDICAO else statusOf(last.valor, r)
                    val serie = remember(tests, p) { tests.filter { it.parametro == p }.sortedBy { it.medidoEm }.takeLast(10).map { it.medidoEm to it.valor } }
                    VStat(Params.label(p), last?.let { Params.format(p, it.valor) } ?: "-", Params.unit(p), statusColor(st), serie, r?.let { it.idealMin to it.idealMax })
                }
            }
        }
    }
}

@Composable
private fun VStat(label: String, value: String, unit: String?, dot: Color?, serie: List<Pair<Long, Double>> = emptyList(), band: Pair<Double, Double>? = null) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (dot != null) { Box(Modifier.size(6.dp).clip(CircleShape).background(dot)); Spacer(Modifier.width(6.dp)) }
            Eyebrow(label)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 30.sp, letterSpacing = (-1).sp, color = Luluco.Cream, maxLines = 1)
            if (unit != null) Text(" $unit", fontSize = 13.sp, color = Luluco.TextSoft, modifier = Modifier.padding(bottom = 5.dp))
        }
        if (serie.size >= 2) Sparkline(serie, serie.first().first, serie.last().first, Luluco.TextSoft, Modifier.padding(top = 4.dp).width(84.dp).height(18.dp), band = band)
    }
}

@Composable
private fun VRow(time: String, title: String, detail: String?, done: Boolean, late: Boolean, wide: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(time, fontSize = 14.sp, color = if (late && !done) Luluco.CoralText else Luluco.TextSoft, modifier = Modifier.width(if (wide) 118.dp else 56.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(title, fontSize = 15.sp, color = if (done) Luluco.TextFaint else Luluco.Cream, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            textDecoration = if (done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
        if (detail != null) Text(detail, fontSize = 13.sp, color = Luluco.TextSoft, maxLines = 1)
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
    val clock = remember(now / 60_000) { java.time.Instant.ofEpochMilli(now).atZone(vm.zone).format(DateTimeFormatter.ofPattern("HH:mm")) }
    Box(Modifier.fillMaxSize().background(Color(0xFF050608))) {
        AtmosWave(Modifier.fillMaxWidth().fillMaxHeight(0.5f).align(Alignment.Center), alpha = 0.35f, lines = 18)
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                Text(clock, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 128.sp, letterSpacing = (-3).sp, color = Luluco.Cream.copy(alpha = 0.6f))
                Text(clock, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 128.sp, letterSpacing = (-3).sp, color = Luluco.Cream.copy(alpha = 0.6f),
                    style = androidx.compose.ui.text.TextStyle(drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f, join = androidx.compose.ui.graphics.StrokeJoin.Round)))
            }
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
