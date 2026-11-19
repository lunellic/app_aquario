package br.com.lunelli.lulucoreef.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lunelli.lulucoreef.BuildConfig
import br.com.lunelli.lulucoreef.data.ParamRange
import br.com.lunelli.lulucoreef.data.TankConfig
import br.com.lunelli.lulucoreef.domain.Params
import br.com.lunelli.lulucoreef.domain.numShort
import br.com.lunelli.lulucoreef.domain.parseNum
import br.com.lunelli.lulucoreef.reminders.Reminders

@Composable
fun ConfigScreen(vm: MainViewModel) {
    val tank by vm.tank.collectAsStateWithLifecycle()
    val ranges by vm.ranges.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val sync by vm.syncStatus.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var editRange by remember { mutableStateOf<ParamRange?>(null) }
    var confirmLogout by remember { mutableStateOf(false) }
    val t = tank ?: TankConfig()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.importPhoto(uri) { path -> if (path != null) vm.save((vm.tank.value ?: TankConfig()).copy(fotoVitrinePath = path)) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader("Configurações")

        SectionTitle("Aquário")
        Panel(Modifier.fillMaxWidth()) {
            key(t.id, t.updatedAt) {
                var nome by remember { mutableStateOf(t.nome) }
                var volume by remember { mutableStateOf(numShort(t.volumeDosagemL)) }
                var sal by remember { mutableStateOf(t.salAtual.orEmpty()) }
                var montado by remember { mutableStateOf(t.montadoEm) }
                var obs by remember { mutableStateOf(t.observacoes.orEmpty()) }
                val v = parseNum(volume)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextInput("Nome", nome, Modifier.weight(2f)) { nome = it }
                        NumInput("Volume de dosagem", volume, Modifier.weight(1f), suffix = "L") { volume = it }
                    }
                    Muted("Todas as doses usam este volume, sem somar o sump.")
                    TextInput("Sal em uso", sal) { sal = it }
                    DateField("Montado em", montado) { montado = it }
                    TextInput("Observações", obs, singleLine = false) { obs = it }
                    Button(enabled = nome.isNotBlank() && v != null && v > 0, onClick = {
                        vm.save(t.copy(nome = nome.trim(), volumeDosagemL = v!!, salAtual = sal.trim().ifBlank { null }, montadoEm = montado, observacoes = obs.trim().ifBlank { null }))
                    }) { Text("Salvar dados do aquário") }
                }
            }
        }

        SectionTitle("Foto do aquário")
        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (t.fotoVitrinePath != null) Photo(t.fotoVitrinePath, Modifier.size(width = 160.dp, height = 100.dp).clip(RoundedCornerShape(12.dp)), maxPx = 320)
                OutlinedButton(onClick = { picker.launch("image/*") }) { Text(if (t.fotoVitrinePath != null) "Trocar foto" else "Escolher foto") }
                if (t.fotoVitrinePath != null) TextButton(onClick = { vm.save(t.copy(fotoVitrinePath = null)) }) { Text("Remover") }
            }
            Muted("Aparece no Painel e de fundo na tela de standby. É reduzida para 1280 px antes de subir.")
        }

        SectionTitle("Faixas dos parâmetros")
        Panel(Modifier.fillMaxWidth()) {
            Params.ALL.forEach { p ->
                val r = ranges[p] ?: return@forEach
                Row(Modifier.fillMaxWidth().clickable { editRange = r }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(Params.label(p), Modifier.width(120.dp), fontWeight = FontWeight.Bold)
                    Text("ideal ${Params.format(p, r.idealMin)} a ${Params.format(p, r.idealMax)} ${r.unidade}", Modifier.weight(1f))
                    Muted("alerta < ${Params.format(p, r.alertaMin)} ou > ${Params.format(p, r.alertaMax)}")
                }
            }
            if (ranges.isEmpty()) Muted("Faixas ainda não sincronizadas.")
        }

        SectionTitle("O que aparece nas telas")
        Panel(Modifier.fillMaxWidth()) {
            Muted("Parâmetros mostrados no Painel e na tela de standby. NO3 e PO4 ficam desligados até você começar a testar.")
            Spacer(Modifier.height(8.dp))
            ChipRow(Params.ALL.map { it to Params.label(it) }, null, selectedSet = settings.shownParams) { p ->
                vm.updateSettings { it.copy(shownParams = if (p in it.shownParams) it.shownParams - p else it.shownParams + p) }
            }
            Spacer(Modifier.height(6.dp))
            Muted("Ligados: " + (Params.ALL.filter { it in settings.shownParams }.joinToString(", ") { Params.label(it) }.ifEmpty { "nenhum" }))
        }

        SectionTitle("Standby e modo noite")
        Panel(Modifier.fillMaxWidth()) {
            Stepper("Standby após (min sem toque; 0 desliga)", settings.vitrineMinutes, 0, 60) { v -> vm.updateSettings { it.copy(vitrineMinutes = v) } }
            // o horario do modo noite tambem vai para o servidor (tank_config), que o respeita no push
            Stepper("Modo noite começa às (h)", settings.nightStartHour, 0, 23) { v -> vm.updateSettings { it.copy(nightStartHour = v) }; vm.save(t.copy(noiteInicio = v)) }
            Stepper("Modo noite termina às (h)", settings.nightEndHour, 0, 23) { v -> vm.updateSettings { it.copy(nightEndHour = v) }; vm.save(t.copy(noiteFim = v)) }
            Muted("No modo noite a tela fica em azul escuro com ondas suaves, brilho baixo, e os lembretes não tocam. Início igual ao fim desliga.")
        }

        SectionTitle("Visual e modo quiosque")
        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Switch(settings.waterFx, { on -> vm.updateSettings { it.copy(waterFx = on) } })
                Column(Modifier.weight(1f)) {
                    Text("Animações de água")
                    Muted("Fundo em movimento, reflexos e ondas. Desligue se o tablet ficar lento.")
                }
            }
            var home by remember { mutableStateOf(br.com.lunelli.lulucoreef.MainActivity.isHomeEnabled(ctx)) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Switch(home, { on -> br.com.lunelli.lulucoreef.MainActivity.setHomeEnabled(ctx, on); home = on })
                Column(Modifier.weight(1f)) {
                    Text("Oferecer como tela inicial do tablet")
                    Muted("Depois de ligar, aperte o botão Home: se o tablet perguntar, escolha Luluco Reef e \"Sempre\". O Fire OS pode ignorar; nesse caso deixe desligado.")
                }
            }
            Muted("O app já fica em tela cheia, abre ao ligar o tablet e aparece por cima da tela de bloqueio (sem PIN). Para sair: voltar duas vezes no Painel.")
        }

        SectionTitle("Som dos lembretes")
        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Switch(settings.soundOn, { on -> vm.updateSettings { it.copy(soundOn = on) } })
                Text(if (settings.soundOn) "Ligado" else "Desligado")
                Slider(settings.soundVolume / 100f, { f -> vm.updateSettings { it.copy(soundVolume = (f * 100).toInt()) } }, Modifier.weight(1f), enabled = settings.soundOn)
                OutlinedButton(onClick = { Reminders.playSound(ctx, settings.soundVolume) }) { Text("Testar") }
            }
        }

        SectionTitle("Avisos no iPhone")
        Panel(Modifier.fillMaxWidth()) {
            val busy by vm.aiBusy.collectAsStateWithLifecycle()
            var feedback by remember { mutableStateOf<String?>(null) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Switch(t.ntfyAtivo, { on -> vm.save(t.copy(ntfyAtivo = on)) })
                Text("Push pelo ntfy (rotina atrasada e alerta crítico)", Modifier.weight(1f))
                OutlinedButton(enabled = "test_ntfy" !in busy, onClick = { vm.sendTest("test_ntfy") { feedback = it } }) { Text("Enviar teste") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Switch(t.emailSemanalAtivo, { on -> vm.save(t.copy(emailSemanalAtivo = on)) })
                Text("E-mail semanal (domingo, 10h)", Modifier.weight(1f))
                OutlinedButton(enabled = "test_email" !in busy, onClick = { vm.sendTest("test_email") { feedback = it } }) { Text("Enviar teste") }
            }
            feedback?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Luluco.PoolText) }
            Muted("O push não é enviado no horário do modo noite, exceto alerta crítico.")
        }

        SectionTitle("Backup e exportação")
        Panel(Modifier.fillMaxWidth()) {
            val busy by vm.aiBusy.collectAsStateWithLifecycle()
            var feedback by remember { mutableStateOf<String?>(null) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Exportar meus dados")
                    Muted("Manda para o seu e-mail um arquivo com tudo: testes, TPAs, rotinas, produtos, aquário, saúde e finanças.")
                }
                OutlinedButton(enabled = "export" !in busy, onClick = { vm.sendTest("export") { feedback = it } }) { Text(if ("export" in busy) "Enviando..." else "Exportar") }
            }
            feedback?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Luluco.PoolText) }
            Muted("Backup automático: todo dia às 3h o mesmo arquivo chega no seu e-mail.")
        }

        SectionTitle("Conta e sincronização")
        Panel(Modifier.fillMaxWidth()) {
            Text(vm.email ?: "-", fontWeight = FontWeight.Bold)
            Muted(when {
                sync.running -> "Sincronizando..."
                sync.lastOkAt != null -> "Última sincronização: ${fmtDateTime(sync.lastOkAt!!)}"
                else -> "Ainda não sincronizou."
            })
            Muted(if (sync.pending > 0) "${sync.pending} registro(s) aguardando envio." else "Nada pendente de envio.")
            sync.lastError?.let { Text("Último erro: $it", color = Luluco.CoralText, style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { vm.syncNow() }, enabled = !sync.running) { Text("Sincronizar agora") }
                OutlinedButton(onClick = { confirmLogout = true }) { Text("Sair da conta") }
            }
        }

        Muted("Luluco Reef ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
        Spacer(Modifier.height(8.dp))
    }

    editRange?.let { r -> RangeDialog(r, onSave = { vm.save(it); editRange = null }, onDismiss = { editRange = null }) }
    if (confirmLogout) {
        ConfirmDialog("Sair da conta",
            if (sync.pending > 0) "Há ${sync.pending} registro(s) que ainda não subiram e serão PERDIDOS. Sincronize antes de sair." else "Os dados locais são apagados deste aparelho e voltam no próximo login.",
            "Sair", onConfirm = { confirmLogout = false; vm.logout() }, onDismiss = { confirmLogout = false })
    }
}

@Composable
private fun Stepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        OutlinedButton(onClick = { if (value > min) onChange(value - 1) }) { Text("-") }
        Text(value.toString(), Modifier.width(56.dp), fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        OutlinedButton(onClick = { if (value < max) onChange(value + 1) }) { Text("+") }
    }
}

@Composable
private fun RangeDialog(r: ParamRange, onSave: (ParamRange) -> Unit, onDismiss: () -> Unit) {
    val p = r.parametro
    var iMin by remember { mutableStateOf(Params.format(p, r.idealMin)) }
    var iMax by remember { mutableStateOf(Params.format(p, r.idealMax)) }
    var aMin by remember { mutableStateOf(Params.format(p, r.alertaMin)) }
    var aMax by remember { mutableStateOf(Params.format(p, r.alertaMax)) }
    val v = listOf(aMin, iMin, iMax, aMax).map { parseNum(it) }
    val ok = v.all { it != null } && v[0]!! <= v[1]!! && v[1]!! < v[2]!! && v[2]!! <= v[3]!!
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Faixa de ${Params.label(p)} (${r.unidade})") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumInput("Ideal mín.", iMin, Modifier.weight(1f)) { iMin = it }
                    NumInput("Ideal máx.", iMax, Modifier.weight(1f)) { iMax = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumInput("Alerta abaixo de", aMin, Modifier.weight(1f)) { aMin = it }
                    NumInput("Alerta acima de", aMax, Modifier.weight(1f)) { aMax = it }
                }
                if (!ok) Text("Precisa valer: alerta mín. <= ideal mín. < ideal máx. <= alerta máx.", color = Luluco.CoralText, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(enabled = ok, onClick = { onSave(r.copy(idealMin = v[1]!!, idealMax = v[2]!!, alertaMin = v[0]!!, alertaMax = v[3]!!)) }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
