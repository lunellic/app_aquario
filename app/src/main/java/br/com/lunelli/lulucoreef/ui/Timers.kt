package br.com.lunelli.lulucoreef.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lunelli.lulucoreef.data.Alarm
import br.com.lunelli.lulucoreef.reminders.Clocks
import br.com.lunelli.lulucoreef.domain.DIAS_CURTOS
import br.com.lunelli.lulucoreef.domain.diasTexto

private fun mmss(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

private fun horaTexto(a: Alarm) = "%02d:%02d".format(a.hora, a.minuto)

/**
 * Bloco de timer do Painel: os minutos que ele usa de verdade num toque, mais "Definir" para um tempo
 * qualquer e "Alarme" para a pagina dos alarmes.
 *
 * Para que serve: desligar a bomba ou o skimmer por alguns minutos e nao esquecer de religar. Por
 * isso o toque usa o som de alarme do tablet em laco, e nao um "bip": a tela pode estar apagada e ele
 * pode estar com a mao dentro do aquario, do outro lado da sala.
 */
@Composable
fun TimerBlock(nav: Nav, compact: Boolean = true) {
    val ctx = LocalContext.current
    val timers by Clocks.timers.collectAsStateWithLifecycle()
    var agora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var definir by remember { mutableStateOf(false) }
    // so conta na tela enquanto ha timer rodando (o disparo em si e do AlarmManager)
    LaunchedEffect(timers.isNotEmpty()) {
        while (timers.isNotEmpty()) {
            agora = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }
    LaunchedEffect(Unit) { Clocks.load(ctx) }
    if (definir) DefinirTimerDialog(onStart = { min -> Clocks.startTimer(ctx, min); definir = false }, onDismiss = { definir = false })

    Panel(Modifier.fillMaxWidth(), padding = 0.dp) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconeRedondo(Icons.Outlined.Refresh, Luluco.AreaTpa)
                Spacer(Modifier.width(10.dp))
                Eyebrow("Timer")
                Spacer(Modifier.weight(1f))
                // todo timer avisa no iPhone tambem: ele sai de perto do aquario e nao ouve o tablet
                Text("toca no iPhone também", color = Luluco.TextSoft, fontSize = 13.sp)
            }
            // Tres tempos no Painel, cinco na tela propria. Com seis botoes na coluna estreita do
            // Painel cada um ficava com uns 50 px e o rotulo quebrava no meio: "5 mi", "1 0",
            // "D efi". Botao ilegivel nao e atalho, e adivinhacao.
            val presets = if (compact) listOf(5, 15, 30) else Clocks.PRESETS
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { min ->
                    BigButton("$min min", Modifier.weight(1f), color = Luluco.Solid) { Clocks.startTimer(ctx, min) }
                }
                BigButton("Definir", Modifier.weight(1f), color = Luluco.Solid) { definir = true }
            }
            if (timers.isEmpty()) Muted("Toque num tempo para começar. O alarme toca no fim, mesmo com a tela apagada.")
            else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                timers.sortedBy { it.endAt }.forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(Luluco.Pool))
                        Spacer(Modifier.width(10.dp))
                        Text(mmss(t.endAt - agora), fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = Luluco.Cream, modifier = Modifier.width(78.dp))
                        Text(t.label.ifBlank { "termina às " + fmtTime(t.endAt) }, Modifier.weight(1f), color = Luluco.TextSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { Clocks.cancelTimer(ctx, t.id) }) { Text("Cancelar") }
                    }
                }
            }
            // alarmes num bloco proprio dentro do mesmo cartao: antes era so a palavra "Alarme" no
            // canto e ele quase nao achava
            if (compact) Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Luluco.Solid)
                    .clickable { nav.top(Screen.Timers) }.padding(horizontal = 14.dp, vertical = 12.dp).testTag("bloco_alarmes"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconeRedondo(Icons.Outlined.Notifications, Luluco.AreaTpa)
                Spacer(Modifier.width(10.dp))
                Text("Alarmes", color = Luluco.Cream, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text("criar ou editar ›", color = Luluco.TextSoft, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun DefinirTimerDialog(onStart: (Int) -> Unit, onDismiss: () -> Unit) {
    var min by remember { mutableIntStateOf(45) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Definir um timer") },
        text = {
            Column {
                Muted("Quantos minutos?")
                Spacer(Modifier.height(8.dp))
                WheelPicker(remember { (1..240).map { "$it min" } }, min - 1, { i -> min = i + 1 })
            }
        },
        confirmButton = { TextButton(onClick = { onStart(min) }) { Text("Começar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Tela do menu: o bloco de timer em cima e a lista de alarmes salvos embaixo. */
@Composable
fun TimersScreen(vm: MainViewModel, nav: Nav) {
    val alarms by vm.alarms.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader("Timer e alarmes") {
            Button(onClick = { nav.go(Screen.AlarmForm()) }) { Text("Novo alarme") }
        }
        TimerBlock(nav, compact = false)

        SectionTitle("Alarmes")
        if (alarms.isEmpty()) Muted("Nenhum alarme salvo. Um alarme toca no horário marcado e pode repetir todos os dias ou nos dias que você escolher.")
        else Panel(Modifier.fillMaxWidth(), padding = 0.dp) {
            alarms.forEachIndexed { i, a ->
                if (i > 0) HorizontalDivider(color = Luluco.Divider)
                Row(
                    Modifier.fillMaxWidth().clickable { nav.go(Screen.AlarmForm(a.id)) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(horaTexto(a), fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 26.sp,
                        color = if (a.ativo) Luluco.Cream else Luluco.TextFaint, modifier = Modifier.width(92.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.titulo.ifBlank { "Alarme" }, color = if (a.ativo) Luluco.Cream else Luluco.TextSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Muted(
                            listOf(
                                when (a.recorrencia) {
                                    "diaria" -> "todos os dias"
                                    "dias_da_semana" -> diasTexto(a.diasSemana)
                                    else -> "uma vez só"
                                },
                                if (a.alto) "alto" else "normal",
                            ).joinToString(" · "),
                            maxLines = 1,
                        )
                    }
                    Switch(a.ativo, { on -> vm.save(a.copy(ativo = on)) })
                }
            }
        }
        Muted("Os alarmes ficam só neste tablet: não sincronizam e não entram no backup.")
        Spacer(Modifier.height(12.dp))
    }
}

/** Criar ou editar um alarme: horário, repetição e volume. */
@Composable
fun AlarmForm(vm: MainViewModel, nav: Nav, id: String?) {
    val alarms by vm.alarms.collectAsStateWithLifecycle()
    val atual = remember(alarms, id) { alarms.firstOrNull { it.id == id } }
    var carregado by remember { mutableStateOf(false) }
    var titulo by remember { mutableStateOf("") }
    var hora by remember { mutableIntStateOf(7) }
    var minuto by remember { mutableIntStateOf(0) }
    var recorrencia by remember { mutableStateOf("diaria") }
    var dias by remember { mutableStateOf(listOf(3)) }
    var alto by remember { mutableStateOf(true) }
    var iphone by remember { mutableStateOf(false) }
    LaunchedEffect(atual) {
        if (atual != null && !carregado) {
            titulo = atual.titulo; hora = atual.hora; minuto = atual.minuto
            recorrencia = atual.recorrencia; dias = atual.diasSemana.orEmpty().ifEmpty { listOf(3) }; alto = atual.alto
            iphone = atual.avisarIphone
            carregado = true
        }
    }
    // As roletas so entram depois de carregar o alarme. Antes elas nasciam em 07:00 e, ao assentar,
    // gravavam 07:00 por cima do horario que acabara de ser lido: editar um alarme das 8h20 abria em 7h.
    val pronto = id == null || carregado

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader(if (atual == null) "Novo alarme" else "Alarme", onBack = { nav.back() })
        Panel(Modifier.fillMaxWidth()) {
            TextInput("Nome (opcional)", titulo, Modifier.fillMaxWidth()) { titulo = it }
            Muted("Ex.: religar a bomba, trocar o perlon, dar comida.")
        }

        SectionTitle("Horário")
        if (pronto) Panel(Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Eyebrow("Hora")
                    WheelPicker(remember { (0..23).map { "%02d".format(it) } }, hora, { i -> hora = i })
                }
                Column(Modifier.weight(1f)) {
                    Eyebrow("Minuto")
                    WheelPicker(remember { (0..55 step 5).map { "%02d".format(it) } }, minuto / 5, { i -> minuto = i * 5 })
                }
            }
        }

        SectionTitle("Repetir")
        Panel(Modifier.fillMaxWidth()) {
            ChipRow(listOf("unica" to "Uma vez só", "diaria" to "Todos os dias", "dias_da_semana" to "Dias da semana"), recorrencia) { recorrencia = it }
            if (recorrencia == "dias_da_semana") {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DIAS_CURTOS.forEachIndexed { i, nome ->
                        val d = i + 1
                        val on = d in dias
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                .background(if (on) Luluco.Cream else Luluco.Solid)
                                .clickable { dias = if (on) dias - d else dias + d }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(nome, color = if (on) Luluco.Navy else Luluco.TextSoft, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
                    }
                }
                if (dias.isEmpty()) Muted("Escolha pelo menos um dia.")
            }
            if (recorrencia == "unica") Muted("Toca no próximo horário e depois se desliga sozinho.")
        }

        SectionTitle("Som")
        Panel(Modifier.fillMaxWidth()) {
            ChipRow(listOf("alto" to "Alto", "normal" to "Normal"), if (alto) "alto" else "normal") { alto = it == "alto" }
            Muted("Usa o som de alarme do próprio tablet, em laço, até você apertar Parar. No alto o volume de alarme vai ao máximo.")
        }

        SectionTitle("iPhone")
        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Avisar também no iPhone", Modifier.weight(1f), color = Luluco.Cream)
                Switch(iphone, { iphone = it }, Modifier.testTag("alarme_iphone"))
            }
            Muted("Chega no iPhone na hora em que tocar aqui. Precisa dos avisos ligados no app do iPhone.")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton("Salvar", Modifier.weight(1f), color = Luluco.Pool, enabled = pronto && (recorrencia != "dias_da_semana" || dias.isNotEmpty())) {
                val base = atual ?: Alarm()
                vm.save(
                    base.copy(
                        titulo = titulo.trim(), hora = hora, minuto = minuto, recorrencia = recorrencia,
                        diasSemana = if (recorrencia == "dias_da_semana") dias.sorted() else null,
                        alto = alto, ativo = true, avisarIphone = iphone,
                        diaUnico = if (recorrencia == "unica") System.currentTimeMillis() else null,
                    ),
                )
                nav.back()
            }
            if (atual != null) BigButton("Apagar", Modifier.weight(0.6f), color = Luluco.Solid) { vm.delete(atual); nav.back() }
        }
        Spacer(Modifier.height(12.dp))
    }
}

/**
 * Aviso de que algo esta tocando, por cima de tudo, com um botao grande de Parar.
 * Fica fora das telas porque pode tocar no descanso, no modo noite ou em qualquer tela.
 */
@Composable
fun RingingOverlay() {
    val ctx = LocalContext.current
    val r by Clocks.ringing.collectAsStateWithLifecycle()
    val atual = r ?: return
    Box(Modifier.fillMaxSize().background(Luluco.Navy.copy(alpha = 0.92f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(atual.titulo, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, color = Luluco.Cream)
            Text(fmtTime(System.currentTimeMillis()), fontSize = 16.sp, color = Luluco.TextSoft)
            BigButton("Parar", Modifier.width(260.dp), color = Luluco.Pool) { Clocks.stop(ctx) }
        }
    }
}
