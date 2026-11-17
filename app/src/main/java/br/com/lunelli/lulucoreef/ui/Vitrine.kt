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
import br.com.lunelli.lulucoreef.domain.Params
import br.com.lunelli.lulucoreef.domain.Status
import br.com.lunelli.lulucoreef.domain.allAlerts
import br.com.lunelli.lulucoreef.domain.healthScore
import br.com.lunelli.lulucoreef.domain.latestOf
import br.com.lunelli.lulucoreef.domain.occurrencesOn
import br.com.lunelli.lulucoreef.domain.reminderText
import br.com.lunelli.lulucoreef.domain.statusOf
import br.com.lunelli.lulucoreef.domain.toLocalDate
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.format.DateTimeFormatter

/** Bloco arredondado da vitrine, legivel de longe sobre a foto ou o fundo de agua. */
@Composable
private fun VBlock(modifier: Modifier = Modifier, borderColor: Color = Luluco.CardBorder, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Column(modifier.clip(shape).background(Luluco.Navy.copy(alpha = 0.55f)).border(1.5.dp, borderColor, shape).padding(18.dp), verticalArrangement = Arrangement.Center, content = content)
}

/**
 * Modo vitrine: entra sozinho depois de um tempo sem toque. Relogio, nota de saude em anel,
 * KH/Ca/Mg/salinidade com mini grafico de 30 dias, temperatura, proxima rotina e o alerta mais grave.
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
    val nextGroup = today.filter { it.open }.groupBy { it.effectiveDueAt / 60_000 }.minByOrNull { it.key }?.value
    val worst = allAlerts(allTests, ranges, changes, today, cases, entries, now).firstOrNull()
    val score = healthScore(allTests, ranges, changes, routines, logs, cases, now, zone).score
    val zdt = Instant.ofEpochMilli(now).atZone(zone)
    val foto = tank?.fotoVitrinePath

    Box(Modifier.fillMaxSize()) {
        if (foto != null) {
            // foto em baixa resolucao esticada = desfoque leve, sem custo; degrade azul da marca por cima
            Photo(foto, Modifier.fillMaxSize(), maxPx = 120)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Luluco.DeepTop.copy(alpha = 0.70f), Luluco.DeepBottom.copy(alpha = 0.82f)))))
        } else {
            WaterBackground(Modifier.fillMaxSize(), strength = 1.3f)
        }

        val clock: @Composable (Modifier) -> Unit = { m ->
            VBlock(m) {
                // troca de minuto com transicao suave
                AnimatedContent(fmtTime(now), transitionSpec = { (fadeIn(tween(600)) + slideInVertically(tween(600)) { it / 6 }) togetherWith (fadeOut(tween(400)) + slideOutVertically(tween(600)) { -it / 6 }) }, label = "relogio") { t ->
                    Text(t, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 104.sp, color = Luluco.Cream, maxLines = 1)
                }
                Text(zdt.format(DateTimeFormatter.ofPattern("EEEE", PtBr)).replaceFirstChar { it.uppercase() }, fontFamily = Fredoka, fontSize = 28.sp, color = Luluco.PoolText)
                Text(zdt.format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", PtBr)), fontSize = 18.sp, color = Luluco.Cream)
            }
        }
        val ring: @Composable (Modifier) -> Unit = { m ->
            VBlock(m) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { HealthRing(score, 128.dp) }
            }
        }
        val temp: @Composable (Modifier) -> Unit = { m ->
            VBlock(m) {
                Text("Temperatura", fontSize = 16.sp, color = Luluco.TextSoft)
                Text("-", fontFamily = Fredoka, fontSize = 52.sp, color = Luluco.Neutral)
                Text("sem sensor", fontSize = 14.sp, color = Luluco.TextSoft)
            }
        }
        val param: @Composable (Modifier, String) -> Unit = { m, p ->
            val last = tests.latestOf(p)
            val r = ranges[p]
            val st = if (last == null || r == null) Status.SEM_MEDICAO else statusOf(last.valor, r)
            val series = remember(tests, now / 3_600_000) { tests.filter { it.parametro == p && now - it.medidoEm <= 30 * DAY_MS }.sortedBy { it.medidoEm }.map { it.medidoEm to it.valor } }
            VBlock(m, borderColor = if (st == Status.CRITICO || st == Status.ATENCAO) statusColor(st) else Luluco.CardBorder) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Params.label(p), fontSize = 17.sp, color = Luluco.Cream, modifier = Modifier.weight(1f), maxLines = 1)
                    Badge(statusLabel(st), statusColor(st))
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(last?.let { Params.format(p, it.valor) } ?: "-", fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 46.sp, color = statusColor(st), maxLines = 1)
                    Spacer(Modifier.width(6.dp))
                    Text(Params.unit(p), fontSize = 15.sp, color = Luluco.TextSoft, modifier = Modifier.padding(bottom = 9.dp))
                }
                Sparkline(series, now - 30 * DAY_MS, now, statusColor(st), Modifier.fillMaxWidth().height(34.dp), band = r?.let { it.idealMin to it.idealMax })
            }
        }
        val routine: @Composable (Modifier) -> Unit = { m ->
            val due = nextGroup?.first()?.isDue(now) == true
            VBlock(m, borderColor = if (due) Luluco.Coral else Luluco.CardBorder) {
                Text(if (nextGroup == null) "Rotinas de hoje" else if (due) "Agora" else "Próxima rotina", fontSize = 16.sp, color = if (due) Luluco.CoralText else Luluco.TextSoft)
                Text(nextGroup?.let { reminderText(it, products.associateBy { p -> p.id }, vm.volume, zone) } ?: "Tudo em dia",
                    fontFamily = Fredoka, fontSize = 22.sp, color = Luluco.Cream, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        val alert: @Composable (Modifier) -> Unit = { m ->
            if (worst != null) VBlock(m, borderColor = statusColor(worst.severity)) {
                Text(statusLabel(worst.severity), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = statusColor(worst.severity))
                Text(worst.fato, fontSize = 18.sp, color = Luluco.Cream, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }

        BoxWithConstraints(Modifier.fillMaxSize().offset(dx.dp, dy.dp).padding(26.dp)) {
            val gap = 14.dp
            if (maxWidth > maxHeight) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                        clock(Modifier.fillMaxWidth().weight(1.25f))
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                            ring(Modifier.weight(1f).fillMaxHeight())
                            temp(Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                    Column(Modifier.weight(1.25f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                        Params.MAIN.chunked(2).forEach { pair ->
                            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                                pair.forEach { p -> param(Modifier.weight(1f).fillMaxHeight(), p) }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            routine(Modifier.weight(1f))
                            if (worst != null) alert(Modifier.weight(1f))
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    clock(Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        ring(Modifier.weight(1f))
                        temp(Modifier.weight(1f))
                    }
                    Params.MAIN.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) { pair.forEach { p -> param(Modifier.weight(1f), p) } }
                    }
                    routine(Modifier.fillMaxWidth())
                    alert(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** Modo noite: quase preto, brilho minimo, so hora e temperatura em cinza escuro. Sem animacao, sem foto. */
@Composable
fun NightScreen(vm: MainViewModel) {
    val now by vm.now.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? Activity
    DisposableEffect(Unit) {
        setBrightness(activity, 0.01f)
        onDispose { setBrightness(activity, WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) }
    }
    var shift by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(180_000)
            shift = (shift + 1) % 4
        }
    }
    val align = listOf(Alignment.Center, Alignment.TopStart, Alignment.BottomEnd, Alignment.TopEnd)[shift]
    Box(Modifier.fillMaxSize().background(Color.Black).padding(60.dp), contentAlignment = align) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(fmtTime(now), fontFamily = Fredoka, fontSize = 64.sp, color = Color(0xFF2A2A2A))
            Text("sem sensor", fontSize = 16.sp, color = Color(0xFF222222))
        }
    }
}

fun setBrightness(activity: Activity?, value: Float) {
    val w = activity?.window ?: return
    val lp = w.attributes
    lp.screenBrightness = value
    w.attributes = lp
}
