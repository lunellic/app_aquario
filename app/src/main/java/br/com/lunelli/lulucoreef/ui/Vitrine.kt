package br.com.lunelli.lulucoreef.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import br.com.lunelli.lulucoreef.domain.Params
import br.com.lunelli.lulucoreef.domain.Status
import br.com.lunelli.lulucoreef.domain.allAlerts
import br.com.lunelli.lulucoreef.domain.healthScore
import br.com.lunelli.lulucoreef.domain.latestOf
import br.com.lunelli.lulucoreef.domain.lateRoutineAlerts
import br.com.lunelli.lulucoreef.domain.occurrencesOn
import br.com.lunelli.lulucoreef.domain.paramAlerts
import br.com.lunelli.lulucoreef.domain.reminderText
import br.com.lunelli.lulucoreef.domain.statusOf
import br.com.lunelli.lulucoreef.domain.toLocalDate
import br.com.lunelli.lulucoreef.domain.tpaAlert
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

private class Bubble(var x: Float, var y: Float, val r: Float, val speed: Float, val phase: Float)

/**
 * Bolhas discretas: poucas particulas, ~25 fps, circulos simples em Canvas.
 * Para sozinho quando o app sai do primeiro plano (repeatOnLifecycle).
 */
@Composable
fun Bubbles(modifier: Modifier = Modifier, count: Int = 12, alpha: Float = 0.18f) {
    val bubbles = remember(count) {
        List(count.coerceAtMost(15)) { Bubble(Random.nextFloat(), Random.nextFloat(), 3f + Random.nextFloat() * 9f, 0.004f + Random.nextFloat() * 0.006f, Random.nextFloat() * 6.28f) }
    }
    var frame by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, bubbles) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                for (b in bubbles) {
                    b.y -= b.speed
                    if (b.y < -0.05f) {
                        b.y = 1.05f
                        b.x = Random.nextFloat()
                    }
                }
                frame++
                delay(40)
            }
        }
    }
    Canvas(modifier) {
        val f = frame // le o estado para redesenhar a cada quadro
        val d = density
        for (b in bubbles) {
            val cx = (b.x + 0.012f * sin(f * 0.05f + b.phase)) * size.width
            drawCircle(Color.White.copy(alpha = alpha), radius = b.r * d, center = Offset(cx, b.y * size.height), style = Stroke(width = 1.2f * d))
        }
    }
}

@Composable
fun VitrineScreen(vm: MainViewModel) {
    val now by vm.now.collectAsStateWithLifecycle()
    val tank by vm.tank.collectAsStateWithLifecycle()
    val ranges by vm.ranges.collectAsStateWithLifecycle()
    val tests by vm.tests.collectAsStateWithLifecycle()
    val changes by vm.changes.collectAsStateWithLifecycle()
    val routines by vm.routines.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    val zone = vm.zone

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
    val cases by vm.cases.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val worst = allAlerts(tests, ranges, changes, today, cases, entries, now).firstOrNull()
    val score = healthScore(tests, ranges, changes, routines, logs, cases, now, zone).score

    Box(Modifier.fillMaxSize().background(Luluco.Abyss)) {
        tank?.fotoVitrinePath?.let { Photo(it, Modifier.fillMaxSize(), maxPx = 1280) }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
        Bubbles(Modifier.fillMaxSize(), count = 14, alpha = 0.22f)
        Column(Modifier.fillMaxSize().offset(dx.dp, dy.dp).padding(36.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(fmtTime(now), fontSize = 72.sp, fontWeight = FontWeight.Bold, color = Luluco.Cream)
                    Text(tank?.nome ?: "Luluco Reef", fontSize = 22.sp, color = Luluco.Cream.copy(alpha = 0.8f))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Saúde", color = Luluco.Cream.copy(alpha = 0.8f))
                    Text(score?.toString() ?: "-", fontSize = 56.sp, fontWeight = FontWeight.Bold, color = scoreColor(score))
                    Text("Temperatura: sem sensor", color = Luluco.Cream.copy(alpha = 0.6f), fontSize = 14.sp)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Params.MAIN.forEach { p ->
                    val last = tests.latestOf(p)
                    val st = if (last == null) Status.SEM_MEDICAO else ranges[p]?.let { statusOf(last.valor, it) } ?: Status.SEM_MEDICAO
                    Column(Modifier.weight(1f)) {
                        Text(Params.label(p), color = Luluco.Cream.copy(alpha = 0.8f), fontSize = 16.sp)
                        Text(last?.let { Params.format(p, it.valor) } ?: "-", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = statusColor(st))
                        Text(Params.unit(p), color = Luluco.Cream.copy(alpha = 0.6f), fontSize = 13.sp)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (nextGroup != null) {
                    val due = nextGroup.first().isDue(now)
                    Text((if (due) "Agora  " else "Próxima  ") + reminderText(nextGroup, products.associateBy { it.id }, vm.volume, zone),
                        fontSize = 20.sp, fontWeight = if (due) FontWeight.Bold else FontWeight.Normal, color = if (due) Luluco.Coral else Luluco.Cream)
                } else {
                    Text("Rotinas de hoje em dia", fontSize = 18.sp, color = Luluco.Cream.copy(alpha = 0.8f))
                }
                if (worst != null) Text(worst.fato, fontSize = 18.sp, color = statusColor(worst.severity))
            }
        }
    }
}

/** Modo noite: quase preto, brilho minimo, so hora e temperatura em cinza escuro. Sem bolhas, sem foto. */
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
            Text(fmtTime(now), fontSize = 64.sp, color = Color(0xFF2A2A2A))
            Text("sem sensor", fontSize = 16.sp, color = Color(0xFF222222))
        }
    }
}

fun scoreColor(score: Int?): Color = when {
    score == null -> Color(0xFF8091AB)
    score >= 85 -> Luluco.Ok
    score >= 65 -> Luluco.Coral
    else -> Luluco.Critical
}

fun setBrightness(activity: Activity?, value: Float) {
    val w = activity?.window ?: return
    val lp = w.attributes
    lp.screenBrightness = value
    w.attributes = lp
}
