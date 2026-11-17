package br.com.lunelli.lulucoreef.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lunelli.lulucoreef.data.Product
import br.com.lunelli.lulucoreef.data.Routine
import br.com.lunelli.lulucoreef.domain.doseFor
import br.com.lunelli.lulucoreef.domain.formatDose
import br.com.lunelli.lulucoreef.domain.isInterval
import br.com.lunelli.lulucoreef.domain.numShort
import br.com.lunelli.lulucoreef.domain.parseNum
import br.com.lunelli.lulucoreef.domain.recurrenceText
import br.com.lunelli.lulucoreef.domain.time
import br.com.lunelli.lulucoreef.domain.toLocalDate
import java.time.LocalDate

private val PRODUCT_TYPES = listOf("suplemento" to "Suplemento", "aditivo" to "Aditivo", "tratamento" to "Tratamento", "midia_filtrante" to "Mídia filtrante", "outro" to "Outro")
private val ROUTINE_TYPES = listOf("dosagem" to "Dosagem", "tpa" to "TPA", "teste" to "Teste", "manutencao" to "Manutenção", "outro" to "Outro")
private val RECURRENCES = listOf("diaria" to "Todos os dias", "dias_da_semana" to "Dias da semana", "a_cada_n_dias" to "A cada N dias", "a_cada_n_semanas" to "A cada N semanas")
private val UNITS = listOf("gotas", "ml", "g", "colher", "outra").map { it to it }
private val WEEKDAYS = listOf("seg", "ter", "qua", "qui", "sex", "sáb", "dom")

@Composable
fun RotinasScreen(vm: MainViewModel, nav: Nav) {
    val routines by vm.routines.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    val tank by vm.tank.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf("rotinas") }
    val volume = tank?.volumeDosagemL ?: 100.0
    val byId = products.associateBy { it.id }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ScreenHeader("Produtos e Rotinas") {
            Button(onClick = { nav.go(if (tab == "rotinas") Screen.RoutineForm() else Screen.ProductForm()) }) { Text(if (tab == "rotinas") "Nova rotina" else "Novo produto") }
        }
        ChipRow(listOf("rotinas" to "Rotinas (${routines.size})", "produtos" to "Produtos (${products.size})"), tab) { tab = it }
        if (tab == "rotinas") {
            routines.forEach { r ->
                val p = r.productId?.let { byId[it] }
                Panel(Modifier.fillMaxWidth().clickable { nav.go(Screen.RoutineForm(r.id)) }, padding = 10.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(r.titulo, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val dose = if (r.tipo == "dosagem" && p != null) formatDose(doseFor(p, volume), p.doseUnidade) + " · " else ""
                            Muted(dose + recurrenceText(r) + (if (r.isInterval()) r.proximaEm?.let { " · próxima em ${fmtDay(it)}" } ?: " · sem data definida" else ""), maxLines = 2)
                        }
                        TextButton(onClick = { nav.go(Screen.RoutineHistory(r.id)) }) { Text("histórico") }
                        Switch(checked = r.ativa, onCheckedChange = { vm.save(r.copy(ativa = it)) })
                    }
                }
            }
        } else {
            Muted("Doses mostradas para ${numShort(volume)} L.")
            products.forEach { p ->
                Panel(Modifier.fillMaxWidth().clickable { nav.go(Screen.ProductForm(p.id)) }, padding = 10.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(listOfNotNull(p.nome, p.marca).joinToString(" · "), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Muted((PRODUCT_TYPES.firstOrNull { it.first == p.tipo }?.second ?: p.tipo) + " · " + formatDose(doseFor(p, volume), p.doseUnidade), maxLines = 1)
                        }
                        if (!p.ativo) Badge("inativo", MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun ProductForm(vm: MainViewModel, nav: Nav, id: String?) {
    val products by vm.products.collectAsStateWithLifecycle()
    val edit = remember(id) { products.firstOrNull { it.id == id } }
    var nome by remember { mutableStateOf(edit?.nome.orEmpty()) }
    var marca by remember { mutableStateOf(edit?.marca.orEmpty()) }
    var tipo by remember { mutableStateOf(edit?.tipo ?: "suplemento") }
    var dose by remember { mutableStateOf(edit?.doseQuantidade?.let { numShort(it) }.orEmpty()) }
    var unidade by remember { mutableStateOf(edit?.doseUnidade) }
    var base by remember { mutableStateOf(edit?.doseBaseLitros?.let { numShort(it) }.orEmpty()) }
    var obs by remember { mutableStateOf(edit?.observacao.orEmpty()) }
    var ativo by remember { mutableStateOf(edit?.ativo ?: true) }
    var confirmDelete by remember { mutableStateOf(false) }
    val numbersOk = (dose.isBlank() || parseNum(dose) != null) && (base.isBlank() || (parseNum(base) ?: 0.0) > 0)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader(if (edit != null) "Editar produto" else "Novo produto", onBack = { nav.back() })
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextInput("Nome", nome, Modifier.weight(1f)) { nome = it }
            TextInput("Marca", marca, Modifier.weight(1f)) { marca = it }
        }
        ChipRow(PRODUCT_TYPES, tipo) { tipo = it }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumInput("Dose", dose, Modifier.weight(1f), suffix = unidade) { dose = it }
            NumInput("Para quantos litros", base, Modifier.weight(1f), suffix = "L") { base = it }
        }
        ChipRow(UNITS, unidade) { unidade = it }
        val preview = parseNum(dose)?.let { d -> parseNum(base)?.takeIf { it > 0 }?.let { b -> d * vm.volume / b } ?: d }
        Muted(if (base.isBlank()) "Sem \"para quantos litros\" a dose é fixa. Use a bula do fabricante." else "No seu aquário (${numShort(vm.volume)} L): ${formatDose(preview, unidade)}. Recalcula sozinho se o volume mudar.")
        TextInput("Observação", obs, singleLine = false) { obs = it }
        Row(verticalAlignment = Alignment.CenterVertically) { Switch(ativo, { ativo = it }); Spacer(Modifier.width(10.dp)); Text("Ativo") }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton("Salvar", Modifier.weight(1f), enabled = nome.isNotBlank() && numbersOk) {
                val b = edit ?: Product(nome = nome.trim())
                vm.save(b.copy(nome = nome.trim(), marca = marca.trim().ifBlank { null }, tipo = tipo, doseQuantidade = parseNum(dose), doseUnidade = unidade,
                    doseBaseLitros = parseNum(base), observacao = obs.trim().ifBlank { null }, ativo = ativo))
                nav.back()
            }
            if (edit != null) BigButton("Apagar", Modifier.width(140.dp), color = Luluco.Critical) { confirmDelete = true }
        }
    }
    if (confirmDelete && edit != null) {
        ConfirmDialog("Apagar produto", "As doses já registradas continuam no histórico. Se ele só saiu de uso, prefira desativar.", "Apagar",
            onConfirm = { vm.delete(edit); confirmDelete = false; nav.back() }, onDismiss = { confirmDelete = false })
    }
}

@Composable
fun RoutineForm(vm: MainViewModel, nav: Nav, id: String?, equipmentIdInicial: String? = null) {
    val routines by vm.routines.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    val equipment by vm.equipment.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val edit = remember(id) { routines.firstOrNull { it.id == id } }
    var titulo by remember { mutableStateOf(edit?.titulo.orEmpty()) }
    var tipo by remember { mutableStateOf(edit?.tipo ?: if (equipmentIdInicial != null) "manutencao" else "dosagem") }
    var productId by remember { mutableStateOf(edit?.productId) }
    var equipmentId by remember { mutableStateOf(edit?.equipmentId ?: equipmentIdInicial) }
    var rec by remember { mutableStateOf(edit?.recorrencia ?: if (equipmentIdInicial != null) "a_cada_n_semanas" else "diaria") }
    val dias = remember { mutableStateListOf<Int>().apply { addAll(edit?.diasSemana.orEmpty()) } }
    var n by remember { mutableStateOf((edit?.intervaloN ?: 1).toString()) }
    var horario by remember { mutableStateOf(edit?.horario?.take(5) ?: "20:00") }
    var proxima by remember { mutableStateOf(edit?.proximaEm?.toLocalDate(vm.zone)?.toString()) }
    var instrucao by remember { mutableStateOf(edit?.instrucao.orEmpty()) }
    var ativa by remember { mutableStateOf(edit?.ativa ?: true) }
    var confirmDelete by remember { mutableStateOf(false) }
    val interval = rec == "a_cada_n_dias" || rec == "a_cada_n_semanas"
    val valid = titulo.isNotBlank() && (rec != "dias_da_semana" || dias.isNotEmpty()) && (!interval || (n.toIntOrNull() ?: 0) > 0)
    val product = products.firstOrNull { it.id == productId }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader(if (edit != null) "Editar rotina" else "Nova rotina", onBack = { nav.back() })
        TextInput("Título", titulo) { titulo = it }
        ChipRow(ROUTINE_TYPES, tipo) { tipo = it }
        if (tipo == "dosagem") {
            PickerField("Produto", products.filter { it.ativo || it.id == productId }.map { it.id to it.nome }, productId) { pid ->
                productId = pid
                if (titulo.isBlank()) titulo = products.firstOrNull { it.id == pid }?.nome.orEmpty()
            }
            if (product != null) Muted("Dose para ${numShort(vm.volume)} L: ${formatDose(doseFor(product, vm.volume), product.doseUnidade)}. Ao marcar Feito, a dose entra no histórico.")
            if (product != null && product.doseQuantidade == null) Text("Este produto está sem dose. Preencha a dose no produto antes de ativar a rotina.", color = Luluco.CoralText)
        }
        if (tipo == "manutencao" && equipment.isNotEmpty()) {
            PickerField("Equipamento (opcional)", equipment.map { it.id to equipmentName(it) }, equipmentId) { equipmentId = it }
        }
        ChipRow(RECURRENCES, rec) { rec = it }
        if (rec == "dias_da_semana") {
            ChipRow(WEEKDAYS.mapIndexed { i, d -> (i + 1).toString() to d }, null) { v -> val d = v.toInt(); if (d in dias) dias.remove(d) else dias.add(d) }
            Muted("Selecionados: " + (dias.sorted().joinToString(", ") { WEEKDAYS[it - 1] }.ifEmpty { "nenhum" }))
        }
        if (interval) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumInput(if (rec == "a_cada_n_dias") "A cada quantos dias" else "A cada quantas semanas", n, Modifier.width(240.dp)) { v -> n = v.filter { it.isDigit() } }
                DateField("Próxima em", proxima) { proxima = it }
            }
            if (proxima == null) Muted("Sem a data da próxima, a rotina não é cobrada. O Painel pergunta a data da última vez.")
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Horário", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = {
                val t = try { java.time.LocalTime.parse(horario) } catch (_: Exception) { java.time.LocalTime.of(20, 0) }
                TimePickerDialog(ctx, { _, h, m -> horario = "%02d:%02d".format(h, m) }, t.hour, t.minute, true).show()
            }) { Text(horario) }
        }
        TextInput("Instrução (opcional)", instrucao, singleLine = false) { instrucao = it }
        Row(verticalAlignment = Alignment.CenterVertically) { Switch(ativa, { ativa = it }); Spacer(Modifier.width(10.dp)); Text("Ativa") }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton("Salvar", Modifier.weight(1f), enabled = valid) {
                val b = edit ?: Routine(titulo = titulo.trim())
                val time = try { java.time.LocalTime.parse(horario) } catch (_: Exception) { java.time.LocalTime.of(20, 0) }
                val anchor = if (interval) proxima?.let { LocalDate.parse(it).atTime(time).atZone(vm.zone).toInstant().toEpochMilli() } else null
                vm.save(b.copy(titulo = titulo.trim(), tipo = tipo, productId = if (tipo == "dosagem") productId else null,
                    equipmentId = if (tipo == "manutencao") equipmentId else null, recorrencia = rec,
                    diasSemana = if (rec == "dias_da_semana") dias.sorted() else null, intervaloN = if (interval) n.toIntOrNull() else null,
                    horario = horario, proximaEm = anchor, instrucao = instrucao.trim().ifBlank { null }, ativa = ativa))
                nav.back()
            }
            if (edit != null) BigButton("Apagar", Modifier.width(140.dp), color = Luluco.Critical) { confirmDelete = true }
        }
    }
    if (confirmDelete && edit != null) {
        ConfirmDialog("Apagar rotina", "O histórico de cumprimento desta rotina deixa de aparecer.", "Apagar",
            onConfirm = { vm.delete(edit); confirmDelete = false; nav.back() }, onDismiss = { confirmDelete = false })
    }
}

/** Historico de cumprimento de uma rotina. */
@Composable
fun RoutineHistory(vm: MainViewModel, nav: Nav, id: String) {
    val routines by vm.routines.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    val r = routines.firstOrNull { it.id == id }
    val mine = logs.filter { it.routineId == id }.sortedByDescending { it.previstoPara }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ScreenHeader("Histórico: ${r?.titulo ?: "rotina"}", onBack = { nav.back() })
        val done = mine.count { it.status == "feito" }
        val skipped = mine.count { it.status == "pulado" }
        Muted("$done feitas, $skipped puladas em ${mine.size} registros.")
        if (mine.isEmpty()) Muted("Nenhum registro ainda.")
        mine.take(120).forEach { l ->
            Panel(Modifier.fillMaxWidth(), padding = 10.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Previsto: ${fmtDateTime(l.previstoPara)}", Modifier.weight(1f))
                    if (l.feitoEm != null) Muted("feito ${fmtDateTime(l.feitoEm)}  ")
                    Badge(l.status, when (l.status) { "feito" -> Luluco.Ok; "adiado" -> Luluco.Pool; else -> MaterialTheme.colorScheme.outline })
                }
            }
        }
    }
}
