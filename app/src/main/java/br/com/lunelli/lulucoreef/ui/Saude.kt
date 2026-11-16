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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lunelli.lulucoreef.data.HealthCase
import br.com.lunelli.lulucoreef.data.HealthEntry
import br.com.lunelli.lulucoreef.domain.DAY_MS

val CASE_TYPES = listOf("observacao" to "Observação", "quarentena" to "Quarentena", "tratamento" to "Tratamento")
val OUTCOMES = listOf("recuperado" to "Recuperado", "morreu" to "Morreu", "transferido" to "Transferido", "sem_alteracao" to "Sem alteração")
private fun caseType(t: String) = CASE_TYPES.firstOrNull { it.first == t }?.second ?: t

@Composable
fun SaudeScreen(vm: MainViewModel, nav: Nav) {
    val cases by vm.cases.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val livestock by vm.livestock.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val names = livestock.associate { it.id to it.nomePopular }
    val open = cases.filter { it.fechadoEm == null }
    val closed = cases.filter { it.fechadoEm != null }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ScreenHeader("Saúde") { Button(onClick = { nav.go(Screen.CaseForm()) }) { Text("Novo caso") } }
        SectionTitle("Casos abertos (${open.size})")
        if (open.isEmpty()) Muted("Nenhum caso aberto.")
        open.forEach { c -> CaseRow(c, names[c.livestockId], entries.filter { it.caseId == c.id }, now) { nav.go(Screen.CaseDetail(c.id)) } }
        if (closed.isNotEmpty()) {
            SectionTitle("Histórico de casos (${closed.size})")
            closed.forEach { c -> CaseRow(c, names[c.livestockId], entries.filter { it.caseId == c.id }, now) { nav.go(Screen.CaseDetail(c.id)) } }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CaseRow(c: HealthCase, livestockName: String?, entries: List<HealthEntry>, now: Long, onClick: () -> Unit) {
    val last = entries.maxOfOrNull { it.registradoEm } ?: c.abertoEm
    val stale = c.fechadoEm == null && now - last > 7 * DAY_MS
    Panel(Modifier.fillMaxWidth().clickable(onClick = onClick), borderColor = if (stale) Luluco.Coral else null, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(c.titulo, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Muted(listOfNotNull(caseType(c.tipo), livestockName ?: "Aquário todo", "aberto em ${fmtDay(c.abertoEm)}", "${entries.size} entradas").joinToString(" · "), maxLines = 1)
                if (stale) Text("Sem nova entrada há ${(now - last) / DAY_MS} dias", color = Luluco.Coral, style = MaterialTheme.typography.bodySmall)
            }
            if (c.fechadoEm != null) Badge(OUTCOMES.firstOrNull { it.first == c.desfecho }?.second ?: "fechado", MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun CaseForm(vm: MainViewModel, nav: Nav, id: String?) {
    val cases by vm.cases.collectAsStateWithLifecycle()
    val livestock by vm.livestock.collectAsStateWithLifecycle()
    val edit = remember(id) { cases.firstOrNull { it.id == id } }
    var tipo by remember { mutableStateOf(edit?.tipo ?: "observacao") }
    var titulo by remember { mutableStateOf(edit?.titulo.orEmpty()) }
    var livestockId by remember { mutableStateOf(edit?.livestockId ?: "") }
    var obs by remember { mutableStateOf(edit?.observacao.orEmpty()) }
    var at by remember { mutableLongStateOf(edit?.abertoEm ?: System.currentTimeMillis()) }
    var confirmDelete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader(if (edit != null) "Editar caso" else "Novo caso de saúde", onBack = { nav.back() })
        ChipRow(CASE_TYPES, tipo) { tipo = it }
        TextInput("Título (ex.: Duncan fechado)", titulo) { titulo = it }
        PickerField("Habitante", listOf("" to "Aquário todo") + livestock.filter { it.status == "ativo" || it.id == livestockId }.map { it.id to it.nomePopular }, livestockId) { livestockId = it }
        DateTimeField("Aberto em", at) { at = it }
        TextInput("Observação", obs, singleLine = false) { obs = it }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton("Salvar", Modifier.weight(1f), enabled = titulo.isNotBlank()) {
                val base = edit ?: HealthCase(titulo = titulo.trim(), abertoEm = at)
                val saved = base.copy(tipo = tipo, titulo = titulo.trim(), livestockId = livestockId.ifBlank { null }, abertoEm = at, observacao = obs.trim().ifBlank { null })
                vm.save(saved)
                if (edit == null) nav.replace(Screen.CaseDetail(saved.id)) else nav.back()
            }
            if (edit != null) BigButton("Apagar", Modifier.width(140.dp), color = Luluco.Critical) { confirmDelete = true }
        }
    }
    if (confirmDelete && edit != null) {
        ConfirmDialog("Apagar caso", "O caso e suas entradas deixam de aparecer.", "Apagar",
            onConfirm = { vm.delete(edit); confirmDelete = false; nav.top(Screen.Saude) }, onDismiss = { confirmDelete = false })
    }
}

@Composable
fun CaseDetail(vm: MainViewModel, nav: Nav, id: String) {
    val cases by vm.cases.collectAsStateWithLifecycle()
    val allEntries by vm.entries.collectAsStateWithLifecycle()
    val livestock by vm.livestock.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    val c = cases.firstOrNull { it.id == id }
    if (c == null) {
        Column { ScreenHeader("Caso", onBack = { nav.back() }); Muted("Não encontrado.") }
        return
    }
    val entries = allEntries.filter { it.caseId == id }.sortedByDescending { it.registradoEm }
    var texto by remember { mutableStateOf("") }
    var foto by remember { mutableStateOf<String?>(null) }
    var productId by remember { mutableStateOf("") }
    var closing by remember { mutableStateOf(false) }
    var deleteEntry by remember { mutableStateOf<HealthEntry?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.importPhoto(uri) { path -> if (path != null) foto = path }
    }
    val productNames = products.associate { it.id to it.nome }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenHeader(c.titulo, onBack = { nav.back() }) { OutlinedButton(onClick = { nav.go(Screen.CaseForm(c.id)) }) { Text("Editar") } }
        Panel(Modifier.fillMaxWidth()) {
            Text(listOfNotNull(caseType(c.tipo), livestock.firstOrNull { it.id == c.livestockId }?.nomePopular ?: "Aquário todo").joinToString(" · "), fontWeight = FontWeight.Bold)
            Muted("Aberto em ${fmtDateTime(c.abertoEm)}" + (c.fechadoEm?.let { " · fechado em ${fmtDateTime(it)} (${OUTCOMES.firstOrNull { o -> o.first == c.desfecho }?.second ?: "-"})" } ?: ""))
            c.observacao?.let { Text(it) }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (c.fechadoEm == null) OutlinedButton(onClick = { closing = true }) { Text("Fechar caso") }
                else OutlinedButton(onClick = { vm.save(c.copy(fechadoEm = null, desfecho = null)) }) { Text("Reabrir") }
                AiGuidanceButton(vm, c)
            }
        }

        if (c.fechadoEm == null) {
            SectionTitle("Nova entrada")
            Panel(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextInput("O que você observou ou fez?", texto, singleLine = false) { texto = it }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (foto != null) Photo(foto, Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)), maxPx = 200)
                        OutlinedButton(onClick = { picker.launch("image/*") }) { Text(if (foto != null) "Trocar foto" else "Foto") }
                        PickerField("Produto usado", listOf("" to "nenhum") + products.map { it.id to it.nome }, productId) { productId = it }
                    }
                    Button(enabled = texto.isNotBlank() || foto != null, onClick = {
                        vm.save(HealthEntry(caseId = id, registradoEm = System.currentTimeMillis(), texto = texto.trim().ifBlank { null }, fotoPath = foto, productId = productId.ifBlank { null }))
                        texto = ""; foto = null; productId = ""
                    }) { Text("Adicionar entrada") }
                }
            }
        }

        SectionTitle("Entradas (${entries.size})")
        if (entries.isEmpty()) Muted("Nenhuma entrada ainda.")
        entries.forEach { e ->
            Panel(Modifier.fillMaxWidth(), padding = 12.dp) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (e.fotoPath != null) Photo(e.fotoPath, Modifier.size(140.dp).clip(RoundedCornerShape(12.dp)), maxPx = 480, contentScale = ContentScale.Crop)
                    Column(Modifier.weight(1f)) {
                        Muted(fmtDateTime(e.registradoEm) + (e.productId?.let { " · ${productNames[it] ?: "produto"}" } ?: ""))
                        e.texto?.let { Text(it) }
                    }
                    TextButton(onClick = { deleteEntry = e }) { Text("apagar") }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (closing) {
        AlertDialog(
            onDismissRequest = { closing = false },
            title = { Text("Fechar caso") },
            text = {
                Column {
                    Text("Qual foi o desfecho?")
                    OUTCOMES.forEach { (value, label) ->
                        Text(label, Modifier.fillMaxWidth().clickable {
                            vm.save(c.copy(fechadoEm = System.currentTimeMillis(), desfecho = value))
                            closing = false
                        }.padding(vertical = 14.dp), fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { closing = false }) { Text("Cancelar") } },
        )
    }
    deleteEntry?.let { e ->
        ConfirmDialog("Apagar entrada", "Esta entrada será removida do caso.", "Apagar", onConfirm = { vm.delete(e); deleteEntry = null }, onDismiss = { deleteEntry = null })
    }
}

/** Orientacao preliminar por IA dentro do caso (spec 11.3). O texto pode ser guardado como entrada do caso. */
@Composable
fun AiGuidanceButton(vm: MainViewModel, c: HealthCase) {
    val busy by vm.aiBusy.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val running = "saude:${c.id}" in busy
    OutlinedButton(enabled = !running, onClick = { vm.healthGuidance(c.id) { t, e -> text = t; error = e } }) {
        Text(if (running) "Consultando a IA..." else "Orientação preliminar (IA)")
    }
    error?.let { e -> ConfirmDialog("Não deu para consultar a IA", e, "Ok", onConfirm = { error = null }, onDismiss = { error = null }) }
    text?.let { t ->
        AlertDialog(
            onDismissRequest = { text = null },
            title = { Text("Orientação preliminar (IA)") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(t)
                    Spacer(Modifier.height(8.dp))
                    Muted("Gerado por IA a partir dos seus registros. É orientação preliminar: confira antes de agir.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.save(HealthEntry(caseId = c.id, registradoEm = System.currentTimeMillis(), texto = "Orientação preliminar (IA): $t"))
                    text = null
                }) { Text("Guardar no caso") }
            },
            dismissButton = { TextButton(onClick = { text = null }) { Text("Fechar") } },
        )
    }
}
