package br.com.lunelli.lulucoreef.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lunelli.lulucoreef.data.Equipment
import br.com.lunelli.lulucoreef.domain.recurrenceText

val EQUIPMENT_TYPES = listOf("skimmer" to "Skimmer", "bomba_circulacao" to "Bomba de circulação", "bomba_retorno" to "Bomba de retorno",
    "luminaria" to "Luminária", "termostato" to "Termostato", "osmose" to "Osmose", "outro" to "Outro")
fun equipmentType(t: String) = EQUIPMENT_TYPES.firstOrNull { it.first == t }?.second ?: t
fun equipmentName(e: Equipment) = listOfNotNull(e.marca, e.modelo).joinToString(" ").ifBlank { equipmentType(e.tipo) }

@Composable
fun EquipamentosScreen(vm: MainViewModel, nav: Nav) {
    val equipment by vm.equipment.collectAsStateWithLifecycle()
    val routines by vm.routines.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var aba by remember { mutableStateOf("equip") }
        ScreenHeader("Equipamentos e produtos") {
            Button(onClick = { nav.go(if (aba == "equip") Screen.EquipmentForm() else Screen.ProductForm()) }) { Text(if (aba == "equip") "Novo equipamento" else "Novo produto") }
        }
        ChipRow(listOf("equip" to "Equipamentos", "produtos" to "Produtos"), aba) { aba = it }
        if (aba == "produtos") { ProductsList(vm, nav); return@Column }
        Muted("Só cadastro e manutenção. O app nunca controla equipamento.")
        if (equipment.isEmpty()) Muted("Nenhum equipamento cadastrado.")
        equipment.forEach { e ->
            val maint = routines.filter { it.equipmentId == e.id }
            Panel(Modifier.fillMaxWidth().clickable { nav.go(Screen.EquipmentForm(e.id)) }, padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(equipmentName(e), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Muted(listOfNotNull(equipmentType(e.tipo), e.configuracao, e.observacao).filter { it.isNotBlank() }.joinToString(" · "), maxLines = 2)
                        maint.forEach { r -> Muted("Manutenção: ${r.titulo} (${recurrenceText(r)})", maxLines = 1) }
                    }
                    if (!e.ativo) Badge("inativo", MaterialTheme.colorScheme.outline)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun EquipmentForm(vm: MainViewModel, nav: Nav, id: String?) {
    val equipment by vm.equipment.collectAsStateWithLifecycle()
    val routines by vm.routines.collectAsStateWithLifecycle()
    val edit = remember(id) { equipment.firstOrNull { it.id == id } }
    var tipo by remember { mutableStateOf(edit?.tipo ?: "outro") }
    var marca by remember { mutableStateOf(edit?.marca.orEmpty()) }
    var modelo by remember { mutableStateOf(edit?.modelo.orEmpty()) }
    var instalado by remember { mutableStateOf(edit?.instaladoEm) }
    var config by remember { mutableStateOf(edit?.configuracao.orEmpty()) }
    var obs by remember { mutableStateOf(edit?.observacao.orEmpty()) }
    var ativo by remember { mutableStateOf(edit?.ativo ?: true) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader(if (edit != null) "Editar equipamento" else "Novo equipamento", onBack = { nav.back() })
        ChipRow(EQUIPMENT_TYPES, tipo) { tipo = it }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextInput("Marca", marca, Modifier.weight(1f)) { marca = it }
            TextInput("Modelo", modelo, Modifier.weight(1f)) { modelo = it }
        }
        DateField("Instalado em", instalado) { instalado = it }
        TextInput(if (tipo == "luminaria") "Configuração (fotoperíodo, % de potência, altura)" else "Configuração (texto livre)", config, singleLine = false) { config = it }
        TextInput("Observação", obs, singleLine = false) { obs = it }
        Row(verticalAlignment = Alignment.CenterVertically) { Switch(ativo, { ativo = it }); Spacer(Modifier.width(10.dp)); Text("Em uso") }
        if (edit != null) {
            val maint = routines.filter { it.equipmentId == edit.id }
            SectionTitle("Manutenção periódica")
            maint.forEach { r ->
                Text("${r.titulo} (${recurrenceText(r)})", Modifier.clickable { nav.go(Screen.RoutineForm(r.id)) }.padding(vertical = 8.dp))
            }
            OutlinedButton(onClick = { nav.go(Screen.RoutineForm(equipmentId = edit.id)) }) { Text("Criar rotina de manutenção") }
        } else {
            Muted("Depois de salvar, abra o equipamento para criar a rotina de manutenção.")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton("Salvar", Modifier.weight(1f), enabled = marca.isNotBlank() || modelo.isNotBlank() || tipo != "outro") {
                val b = edit ?: Equipment()
                vm.save(b.copy(tipo = tipo, marca = marca.trim().ifBlank { null }, modelo = modelo.trim().ifBlank { null }, instaladoEm = instalado,
                    configuracao = config.trim().ifBlank { null }, observacao = obs.trim().ifBlank { null }, ativo = ativo))
                nav.back()
            }
            if (edit != null) BigButton("Apagar", Modifier.width(140.dp), color = Luluco.Critical) { confirmDelete = true }
        }
    }
    if (confirmDelete && edit != null) {
        ConfirmDialog("Apagar equipamento", "Se ele só saiu de uso, prefira desmarcar \"Em uso\".", "Apagar",
            onConfirm = { vm.delete(edit); confirmDelete = false; nav.back() }, onDismiss = { confirmDelete = false })
    }
}
