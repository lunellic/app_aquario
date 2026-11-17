package br.com.lunelli.lulucoreef.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lunelli.lulucoreef.data.Dosing
import br.com.lunelli.lulucoreef.data.Event
import br.com.lunelli.lulucoreef.data.WaterChange
import br.com.lunelli.lulucoreef.data.WaterTest
import br.com.lunelli.lulucoreef.domain.Occurrence
import br.com.lunelli.lulucoreef.domain.Params
import br.com.lunelli.lulucoreef.domain.doseFor
import br.com.lunelli.lulucoreef.domain.numShort
import br.com.lunelli.lulucoreef.domain.parseNum

@Composable
fun RegistrarScreen(nav: Nav) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader("Registrar")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton("Teste de água", Modifier.weight(1f).height(110.dp)) { nav.go(Screen.TestForm()) }
            BigButton("TPA", Modifier.weight(1f).height(110.dp)) { nav.go(Screen.TpaForm()) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton("Dosagem", Modifier.weight(1f).height(110.dp), color = Luluco.Royal) { nav.go(Screen.DoseForm()) }
            BigButton("Evento / nota", Modifier.weight(1f).height(110.dp), color = Luluco.Royal) { nav.go(Screen.EventForm()) }
        }
    }
}

@Composable
private fun FormColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
private fun SaveRow(enabled: Boolean, onDelete: (() -> Unit)?, onSave: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BigButton("Salvar", Modifier.weight(1f), enabled = enabled, onClick = onSave)
        if (onDelete != null) BigButton("Apagar", Modifier.width(140.dp), color = Luluco.Critical) { confirmDelete = true }
    }
    if (confirmDelete && onDelete != null) {
        ConfirmDialog("Apagar registro", "Este registro será removido do histórico.", "Apagar", onConfirm = { confirmDelete = false; onDelete() }, onDismiss = { confirmDelete = false })
    }
}

/** Teste: KH, Ca, Mg e salinidade de uma vez (cada um opcional); NO3 e PO4 atras de "mais parametros". */
@Composable
fun TestForm(vm: MainViewModel, nav: Nav, edit: WaterTest?) {
    val params = if (edit != null) listOf(edit.parametro) else Params.ALL
    val values = remember { mutableStateMapOf<String, String>().apply { if (edit != null) put(edit.parametro, Params.format(edit.parametro, edit.valor)) } }
    var at by remember { mutableLongStateOf(edit?.medidoEm ?: System.currentTimeMillis()) }
    var obs by remember { mutableStateOf(edit?.observacao.orEmpty()) }
    var more by remember { mutableStateOf(false) }
    var lowConfidence by remember { mutableStateOf(edit?.baixaConfianca ?: false) }
    var confirmAbsurd by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { try { first.requestFocus() } catch (_: Exception) { } }

    val parsed = params.mapNotNull { p -> values[p]?.takeIf { it.isNotBlank() }?.let { p to parseNum(it) } }
    val valid = parsed.isNotEmpty() && parsed.all { it.second != null }

    fun doSave() {
        parsed.forEach { (p, v) ->
            val base = edit ?: WaterTest(medidoEm = at, parametro = p, valor = v!!, unidade = Params.unit(p), kit = Params.kit(p))
            vm.save(base.copy(medidoEm = at, valor = v!!, observacao = obs.ifBlank { null }, baixaConfianca = lowConfidence))
        }
        nav.back()
    }

    FormColumn {
        ScreenHeader(if (edit != null) "Editar ${Params.label(edit.parametro)}" else "Teste de água", onBack = { nav.back() })
        val visible = if (edit != null) params else Params.MAIN + (if (more) Params.OPTIONAL else emptyList())
        visible.chunked(2).forEachIndexed { row, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEachIndexed { i, p ->
                    NumInput(Params.label(p), values[p].orEmpty(), (if (row == 0 && i == 0) Modifier.focusRequester(first) else Modifier).weight(1f), suffix = Params.unit(p)) { values[p] = it }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (edit == null && !more) TextButton(onClick = { more = true }) { Text("Mais parâmetros (NO3, PO4)") }
        DateTimeField("Medido em", at) { at = it }
        TextInput("Observação (opcional)", obs) { obs = it }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(lowConfidence, { lowConfidence = it })
            Column {
                Text("Leitura de baixa confiança")
                Muted("Fica vazada no gráfico e fora de projeção, alertas e nota de saúde.")
            }
        }
        SaveRow(valid, onDelete = edit?.let { e -> { vm.delete(e); nav.back() } }) {
            val absurd = parsed.filter { Params.isAbsurd(it.first, it.second!!) }
            if (absurd.isNotEmpty()) confirmAbsurd = absurd.joinToString(", ") { "${Params.label(it.first)} ${values[it.first]}" } else doSave()
        }
    }
    confirmAbsurd?.let { text ->
        ConfirmDialog("Valor fora do comum", "$text parece fora do normal. Confirma que digitou certo?", "Salvar assim mesmo",
            onConfirm = { confirmAbsurd = null; doSave() }, onDismiss = { confirmAbsurd = null })
    }
}

@Composable
fun TpaForm(vm: MainViewModel, nav: Nav, edit: WaterChange?, occurrence: Occurrence?) {
    val tank by vm.tank.collectAsStateWithLifecycle()
    var litros by remember { mutableStateOf(edit?.litros?.let { numShort(it) } ?: if (occurrence != null) "10" else "") }
    var sal by remember { mutableStateOf(edit?.sal ?: tank?.salAtual.orEmpty()) }
    var salin by remember { mutableStateOf(edit?.salinidadeNovaAgua?.let { Params.format("salinidade", it) }.orEmpty()) }
    var temp by remember { mutableStateOf(edit?.temperaturaNovaAgua?.let { numShort(it) }.orEmpty()) }
    var metodo by remember { mutableStateOf(edit?.metodo.orEmpty()) }
    var obs by remember { mutableStateOf(edit?.observacao.orEmpty()) }
    var at by remember { mutableLongStateOf(edit?.feitaEm ?: System.currentTimeMillis()) }
    var confirm by remember { mutableStateOf(false) }
    val l = parseNum(litros)
    val optionalOk = (salin.isBlank() || parseNum(salin) != null) && (temp.isBlank() || parseNum(temp) != null)

    fun doSave() {
        val base = edit ?: WaterChange(feitaEm = at, litros = l!!)
        vm.save(base.copy(feitaEm = at, litros = l!!, sal = sal.ifBlank { null }, salinidadeNovaAgua = parseNum(salin), temperaturaNovaAgua = parseNum(temp),
            metodo = metodo.ifBlank { null }, observacao = obs.ifBlank { null }))
        if (occurrence != null) vm.markDone(occurrence, createDosing = false)
        nav.back()
    }

    FormColumn {
        ScreenHeader(if (edit != null) "Editar TPA" else "Registrar TPA", onBack = { nav.back() })
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumInput("Litros trocados", litros, Modifier.weight(1f), suffix = "L") { litros = it }
            NumInput("Salinidade da água nova", salin, Modifier.weight(1f), suffix = "SG") { salin = it }
            NumInput("Temp. da água nova", temp, Modifier.weight(1f), suffix = "°C") { temp = it }
        }
        Muted("Rotina: 10 a 12 L por semana. Salinidade e temperatura são opcionais.")
        TextInput("Sal", sal) { sal = it }
        TextInput("Método (opcional)", metodo) { metodo = it }
        ChipRow(listOf("Balde", "Bomba", "Sifão", "Gotejamento").map { it to it }, metodo) { metodo = it }
        DateTimeField("Feita em", at) { at = it }
        TextInput("Observação (opcional)", obs) { obs = it }
        SaveRow(l != null && l > 0 && optionalOk, onDelete = edit?.let { e -> { vm.delete(e); nav.back() } }) {
            val s = parseNum(salin)
            if (l!! > (tank?.volumeDosagemL ?: 100.0) * 0.5 || (s != null && Params.isAbsurd("salinidade", s))) confirm = true else doSave()
        }
    }
    if (confirm) ConfirmDialog("Valor fora do comum", "Os valores digitados parecem fora do normal para uma TPA. Confirma?", "Salvar assim mesmo",
        onConfirm = { confirm = false; doSave() }, onDismiss = { confirm = false })
}

@Composable
fun DoseForm(vm: MainViewModel, nav: Nav, edit: Dosing?) {
    val products by vm.products.collectAsStateWithLifecycle()
    val units = listOf("gotas", "ml", "g", "colher", "outra")
    var productId by remember { mutableStateOf(edit?.productId) }
    var qty by remember { mutableStateOf(edit?.quantidade?.let { numShort(it) }.orEmpty()) }
    var unit by remember { mutableStateOf(edit?.unidade) }
    var obs by remember { mutableStateOf(edit?.observacao.orEmpty()) }
    var at by remember { mutableLongStateOf(edit?.dosadoEm ?: System.currentTimeMillis()) }
    val choices = products.filter { it.ativo || it.id == productId }

    FormColumn {
        ScreenHeader(if (edit != null) "Editar dosagem" else "Registrar dosagem", onBack = { nav.back() })
        Text("Produto", color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChipRow(choices.map { it.id to it.nome }, productId) { id ->
            productId = id
            // preenche a dose padrao do produto para o volume atual
            products.firstOrNull { it.id == id }?.let { p ->
                qty = doseFor(p, vm.volume)?.let { numShort(it) }.orEmpty()
                unit = p.doseUnidade
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumInput("Quantidade", qty, Modifier.weight(1f), suffix = unit) { qty = it }
        }
        ChipRow(units.map { it to it }, unit) { unit = it }
        DateTimeField("Dosado em", at) { at = it }
        TextInput("Observação (opcional)", obs) { obs = it }
        SaveRow(productId != null && (qty.isBlank() || parseNum(qty) != null), onDelete = edit?.let { e -> { vm.delete(e); nav.back() } }) {
            val base = edit ?: Dosing(dosadoEm = at, productId = productId!!)
            vm.save(base.copy(dosadoEm = at, productId = productId!!, quantidade = parseNum(qty), unidade = unit, observacao = obs.ifBlank { null }))
            nav.back()
        }
    }
}

val EVENT_TYPES = listOf("observacao" to "Observação", "problema" to "Problema", "equipamento" to "Equipamento", "alimentacao" to "Alimentação", "outro" to "Outro")

@Composable
fun EventForm(vm: MainViewModel, nav: Nav, edit: Event?) {
    var tipo by remember { mutableStateOf(edit?.tipo ?: "observacao") }
    var texto by remember { mutableStateOf(edit?.texto.orEmpty()) }
    var at by remember { mutableLongStateOf(edit?.ocorridoEm ?: System.currentTimeMillis()) }
    FormColumn {
        ScreenHeader(if (edit != null) "Editar nota" else "Nova nota", onBack = { nav.back() })
        ChipRow(EVENT_TYPES, tipo) { tipo = it }
        TextInput("O que aconteceu?", texto, singleLine = false, modifier = Modifier.heightIn(min = 120.dp)) { texto = it }
        DateTimeField("Quando", at) { at = it }
        SaveRow(texto.isNotBlank(), onDelete = edit?.let { e -> { vm.delete(e); nav.back() } }) {
            val base = edit ?: Event(ocorridoEm = at, texto = texto.trim())
            vm.save(base.copy(ocorridoEm = at, tipo = tipo, texto = texto.trim()))
            nav.back()
        }
    }
}
