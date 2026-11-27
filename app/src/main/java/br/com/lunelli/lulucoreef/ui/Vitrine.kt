package br.com.lunelli.lulucoreef.ui

import android.app.Activity
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lunelli.lulucoreef.domain.agendaOn
import br.com.lunelli.lulucoreef.data.reliable
import br.com.lunelli.lulucoreef.domain.DAY_MS
import br.com.lunelli.lulucoreef.domain.OccState
import br.com.lunelli.lulucoreef.domain.Params
import br.com.lunelli.lulucoreef.domain.Status
import br.com.lunelli.lulucoreef.domain.allAlerts
import br.com.lunelli.lulucoreef.domain.doseFor
import br.com.lunelli.lulucoreef.domain.formatDose
import br.com.lunelli.lulucoreef.domain.healthScore
import br.com.lunelli.lulucoreef.domain.diaDaAgenda
import br.com.lunelli.lulucoreef.domain.rotuloHoje
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

    val today = occurrencesOn(routines, logs, diaDaAgenda(now, zone), zone, now)
    val alertas = allAlerts(allTests, ranges, changes, today, cases, entries, now).take(4)
    val saude = healthScore(allTests, ranges, changes, routines, logs, cases, now, zone)
    val score = saude.score
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
        // a onda encheu o vazio da esquerda enquanto ele existia; agora os parametros ocupam esse espaco,
        // entao ela desce para a faixa de baixo e perde peso, para nao disputar com os numeros

        Column(Modifier.fillMaxSize().offset(dx.dp, dy.dp).padding(horizontal = 40.dp, vertical = 28.dp)) {
            Row(Modifier.weight(1f)) {
                // esquerda: hora, data e, no espaco que antes ficava vazio, os ultimos testes em destaque
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    // Encostado a esquerda, alinhado com a data e os parametros embaixo. Centrado
                    // numa caixa de 62% da coluna ele flutuava no meio, sem ancora nenhuma.
                    // Topo: relogio e data a esquerda; saude e ultima TPA a direita deles, encostados
                    // na lista. A coluna da direita fica inteira para o que fazer.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            GlassClock(fmtTime(now), Modifier.fillMaxWidth(0.92f), maxFont = 124f, alinhamento = Alignment.CenterStart)
                            Eyebrow(zdt.format(DateTimeFormatter.ofPattern("EEEE · d 'de' MMMM", PtBr)), Modifier.padding(start = 4.dp))
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            VIndicador("Saúde", scoreColor(score, saude.critico), score?.toString() ?: "-", null)
                            val dias = lastTpa?.let { ((now - it.feitaEm) / DAY_MS).toInt() }
                            VIndicador("Última TPA", corDaTpa(dias), dias?.let { if (it == 1) "1 dia" else "$it dias" } ?: "-",
                                lastTpa?.let { "${numShort(it.litros)} L trocados" })
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    // Os quatro parametros mais relogio mais avisos nao cabiam em 500 px de
                    // altura: a segunda linha de numeros saia cortada ao meio. Alem de encolher
                    // o conteudo, o bloco agora recorta no proprio limite, para um dado a mais
                    // nunca voltar a invadir o bloco de baixo sem ninguem perceber.
                    // Com ate quatro parametros eles descem para o meio da altura que sobra; com NO3
                    // ou PO4 ligados (cinco ou seis) precisam do espaco todo e voltam a encostar em cima.
                    if (shown.isNotEmpty()) Column(Modifier.weight(1f).clipToBounds(),
                        verticalArrangement = if (shown.size <= 4) Arrangement.spacedBy(22.dp, Alignment.CenterVertically) else Arrangement.spacedBy(14.dp)) {
                        shown.chunked(2).forEach { linha ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                                linha.forEach { p ->
                                    val last = tests.latestOf(p)
                                    val r = ranges[p]
                                    val serie = remember(tests, p) { tests.filter { it.parametro == p }.sortedBy { it.medidoEm }.takeLast(12).map { it.medidoEm to it.valor } }
                                    VParam(Modifier.weight(1f), p, last?.valor, last?.medidoEm, r, serie, now)
                                }
                                // linha de um item so (5 ou 1 parametro escolhido): nao esticar o card
                                if (linha.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    } else Spacer(Modifier.weight(1f))
                    // Dois avisos, e o resto vira contagem. Com quatro, o bloco crescia para
                    // baixo e cobria a segunda linha de parametros: os numeros saiam cortados
                    // pela metade. Aviso que esconde a medicao nao esta avisando nada.
                    if (alertas.isNotEmpty()) Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        alertas.take(2).forEach { a ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(7.dp).clip(CircleShape).background(statusColor(a.severity)))
                                Spacer(Modifier.width(10.dp))
                                Text(a.fato, fontSize = 15.sp, color = Luluco.Cream, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        val resto = alertas.size - 2
                        if (resto > 0) Text(
                            if (resto == 1) "e mais 1 aviso" else "e mais $resto avisos",
                            fontSize = 13.sp, color = Luluco.TextFaint, modifier = Modifier.padding(start = 17.dp),
                        )
                    }
                }
                // direita: hoje e proximos, em lista com linhas finas
                Column(Modifier.width(300.dp).fillMaxHeight().padding(start = 24.dp)) {
                  // A lista ocupa o que sobra e recorta no limite: num dia com dez rotinas ela nao
                  // pode empurrar a saude e a TPA para fora da tela.
                  Column(Modifier.weight(1f).clipToBounds()) {
                    Eyebrow(rotuloHoje(now, zone) + if (today.isEmpty()) "" else "  ·  $doneToday/${today.size}")
                    Spacer(Modifier.height(8.dp))
                    if (today.isEmpty()) Text("Nada para hoje.", fontSize = 15.sp, color = Luluco.TextSoft)
                    today.forEachIndexed { i, o ->
                        if (i > 0) HorizontalDivider(color = Luluco.Divider, thickness = 1.dp)
                        val pr = o.routine.productId?.let { productById[it] }
                        val dose = if (o.routine.tipo == "dosagem" && pr != null) formatDose(doseFor(pr, vm.volume), pr.doseUnidade) else null
                        // atrasada arrastada de outro dia: mostra de quando e, nao a hora
                        val quando = when {
                            o.state == OccState.FEITO -> "feito"
                            o.state == OccState.PULADO -> "pulou"
                            o.carried -> fmtDayShort(o.dueAt)
                            o.semHorario -> "hoje"
                            else -> fmtTime(o.effectiveDueAt)
                        }
                        VRow(quando, o.routine.titulo, dose, done = !o.open, late = o.isDue(now))
                    }
                    if (next.isNotEmpty()) {
                        Spacer(Modifier.height(22.dp))
                        Eyebrow("Próximos")
                        Spacer(Modifier.height(8.dp))
                        next.forEachIndexed { i, item ->
                            if (i > 0) HorizontalDivider(color = Luluco.Divider, thickness = 1.dp)
                            VRow(whenText(item.dueAt, now, zone, item.routine.comHorario), item.routine.titulo, null, done = false, late = item.late, wide = true)
                        }
                    }
                  }
                }
            }
        }
    }
}

/**
 * Um parametro no descanso, em destaque: valor grande, quando foi medido, variacao desde a leitura
 * anterior, minigrafico com os pontos na cor da zona e a barra da faixa.
 *
 * Antes os parametros eram seis itenzinhos apertados numa fileira no pe da tela, com grafico de
 * 84 x 18 dp, enquanto a metade esquerda ficava vazia embaixo do relogio. Agora ocupam esse espaco.
 */
@Composable
private fun VParam(modifier: Modifier, param: String, valor: Double?, medidoEm: Long?, range: br.com.lunelli.lulucoreef.data.ParamRange?, serie: List<Pair<Long, Double>>, now: Long) {
    val st = if (valor == null || range == null) Status.SEM_MEDICAO else statusOf(valor, range)
    val delta = if (serie.size >= 2) serie[serie.size - 1].second - serie[serie.size - 2].second else null
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(Params.label(param))
            Spacer(Modifier.width(8.dp))
            Badge(statusLabel(st), statusColor(st))
            Spacer(Modifier.weight(1f))
            if (medidoEm != null) Text(fmtAgoShort(medidoEm, now), fontSize = 12.sp, color = Luluco.TextFaint, maxLines = 1)
        }
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(valor?.let { Params.format(param, it) } ?: "-", fontFamily = Display, fontWeight = FontWeight.SemiBold,
                fontSize = 34.sp, letterSpacing = (-1.1).sp, color = Luluco.Cream, maxLines = 1)
            Params.unit(param)?.let { Text(" $it", fontSize = 15.sp, color = Luluco.TextSoft, modifier = Modifier.padding(bottom = 8.dp)) }
            Spacer(Modifier.weight(1f))
            if (delta != null) Box(Modifier.padding(bottom = 6.dp)) { DeltaBadge(Params.format(param, kotlin.math.abs(delta)), if (delta > 0) 1 else if (delta < 0) -1 else 0) }
        }
        if (serie.size >= 2) Sparkline(serie, serie.first().first, serie.last().first, Luluco.TextSoft,
            Modifier.fillMaxWidth().height(20.dp), band = range?.let { it.idealMin to it.idealMax },
            colorOf = range?.let { r -> { v: Double -> zoneColor(v, r) } })
        if (range != null) RangeBar(valor, range, statusColor(st))
    }
}

@Composable
private fun VStat(label: String, value: String, unit: String?, dot: Color?, serie: List<Pair<Long, Double>> = emptyList(), band: Pair<Double, Double>? = null, range: br.com.lunelli.lulucoreef.data.ParamRange? = null) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (dot != null) { Box(Modifier.size(6.dp).clip(CircleShape).background(dot)); Spacer(Modifier.width(6.dp)) }
            Eyebrow(label)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, letterSpacing = (-1).sp, color = Luluco.Cream, maxLines = 1)
            if (unit != null) Text(" $unit", fontSize = 13.sp, color = Luluco.TextSoft, modifier = Modifier.padding(bottom = 5.dp))
        }
        if (serie.size >= 2) Sparkline(serie, serie.first().first, serie.last().first, Luluco.TextSoft, Modifier.padding(top = 4.dp).width(84.dp).height(18.dp), band = band, colorOf = range?.let { r -> { v: Double -> zoneColor(v, r) } })
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
 * Modo noite: quase preto, brilho baixo, sem numeros nem foto. Mostra so a hora, a data e o que
 * tem amanha, em tom discreto.
 */
@Composable
fun NightScreen(vm: MainViewModel) {
    val now by vm.now.collectAsStateWithLifecycle()
    val routines by vm.routines.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    val clock = remember(now / 60_000) { java.time.Instant.ofEpochMilli(now).atZone(vm.zone).format(DateTimeFormatter.ofPattern("HH:mm")) }
    val amanha = remember(routines, logs, now / 600_000) {
        val d = now.toLocalDate(vm.zone).plusDays(1)
        d to agendaOn(routines, logs, d, vm.zone).sortedBy { it.dueAt }.map { it.routine.titulo }.distinct()
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF050608))) {
        // Luz lateral parada. Antes ela acompanhava a onda e, por isso, redesenhava a tela inteira
        // a cada quadro, a noite toda, so para mudar de tom devagar. Agora e um clarao fixo em
        // violeta, desenhado uma vez: mesma profundidade, nenhum quadro por segundo.
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val rad = size.height * 0.9f
            listOf(0f, size.width).forEach { cx ->
                drawRect(androidx.compose.ui.graphics.Brush.radialGradient(
                    listOf(Luluco.Pool.copy(alpha = 0.10f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(cx, size.height / 2), radius = rad))
            }
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            GlassClock(clock, Modifier.width(520.dp), maxFont = 300f, dim = 0.6f)
            Spacer(Modifier.height(14.dp))
            Text(java.time.Instant.ofEpochMilli(now).atZone(vm.zone).format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", PtBr)).replaceFirstChar { it.uppercase() },
                fontSize = 18.sp, color = Luluco.TextSoft.copy(alpha = 0.6f))
        }
        // amanha, bem discreto, no canto de baixo
        if (amanha.second.isNotEmpty()) Column(Modifier.align(Alignment.BottomStart).padding(start = 40.dp, bottom = 32.dp)) {
            Text("AMANHÃ · " + amanha.first.format(DateTimeFormatter.ofPattern("EEEE", PtBr)).uppercase(), fontSize = 11.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold, color = Luluco.TextSoft.copy(alpha = 0.5f))
            Spacer(Modifier.height(6.dp))
            amanha.second.take(7).forEach { Text(it, fontSize = 14.sp, color = Luluco.TextSoft.copy(alpha = 0.45f), maxLines = 1) }
            if (amanha.second.size > 7) Text("+${amanha.second.size - 7}", fontSize = 13.sp, color = Luluco.TextSoft.copy(alpha = 0.35f))
        }
    }
}

fun setBrightness(activity: Activity?, value: Float) {
    val w = activity?.window ?: return
    val lp = w.attributes
    lp.screenBrightness = value
    w.attributes = lp
}

/**
 * Indicador do descanso: bolinha na cor do estado, rotulo pequeno, numero grande e, se houver, um
 * detalhe numa linha propria embaixo. Antes "3d" e "10 L" ficavam colados na mesma linha e liam
 * como uma coisa so.
 */
@Composable
private fun VIndicador(rotulo: String, cor: Color, valor: String, detalhe: String?) {
    Column(horizontalAlignment = Alignment.Start) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(cor))
            Spacer(Modifier.width(7.dp))
            Eyebrow(rotulo)
        }
        Text(valor, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, letterSpacing = (-0.8).sp, color = Luluco.Cream, maxLines = 1)
        if (detalhe != null) Text(detalhe, fontSize = 12.sp, color = Luluco.TextSoft, maxLines = 1)
    }
}

/** Ate 5 dias da ultima TPA, verde; 6 e 7, laranja; a partir de 8, vermelho: hora de trocar. */
fun corDaTpa(dias: Int?): Color = when {
    dias == null -> Luluco.ZoneNone
    dias <= 5 -> Luluco.ZoneIdeal
    dias <= 7 -> Luluco.ZoneWarn
    else -> Luluco.ZoneCrit
}
