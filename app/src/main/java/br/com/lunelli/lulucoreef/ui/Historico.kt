package br.com.lunelli.lulucoreef.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lunelli.lulucoreef.domain.Params
import br.com.lunelli.lulucoreef.domain.formatDose
import br.com.lunelli.lulucoreef.domain.numShort

private class HistItem(val key: String, val at: Long, val type: String, val title: String, val detail: String, val screen: Screen)

/** Historico unificado por data, com filtro por tipo. Tocar abre o registro para editar ou apagar. */
@Composable
fun HistoricoScreen(vm: MainViewModel, nav: Nav) {
    val tests by vm.tests.collectAsStateWithLifecycle()
    val changes by vm.changes.collectAsStateWithLifecycle()
    val dosings by vm.dosings.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf("todos") }

    val items = remember(tests, changes, dosings, events, products, filter) {
        val names = products.associate { it.id to it.nome }
        val out = ArrayList<HistItem>()
        if (filter == "todos" || filter == "teste") tests.forEach {
            out += HistItem("t" + it.id, it.medidoEm, "Teste", "${Params.label(it.parametro)}: ${Params.format(it.parametro, it.valor)} ${it.unidade}", it.observacao.orEmpty(), Screen.TestForm(it))
        }
        if (filter == "todos" || filter == "tpa") changes.forEach {
            out += HistItem("c" + it.id, it.feitaEm, "TPA", "TPA de ${numShort(it.litros)} L", listOfNotNull(it.sal, it.metodo, it.observacao).joinToString(" · "), Screen.TpaForm(it))
        }
        if (filter == "todos" || filter == "dose") dosings.forEach {
            out += HistItem("d" + it.id, it.dosadoEm, "Dose", "${names[it.productId] ?: "Produto"}: ${formatDose(it.quantidade, it.unidade)}", it.observacao.orEmpty(), Screen.DoseForm(it))
        }
        if (filter == "todos" || filter == "evento") events.forEach {
            out += HistItem("e" + it.id, it.ocorridoEm, EVENT_TYPES.firstOrNull { t -> t.first == it.tipo }?.second ?: "Nota", it.texto, "", Screen.EventForm(it))
        }
        out.sortedByDescending { it.at }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Histórico")
        ChipRow(listOf("todos" to "Todos", "teste" to "Testes", "tpa" to "TPAs", "dose" to "Doses", "evento" to "Notas"), filter, Modifier.padding(bottom = 10.dp)) { filter = it }
        if (items.isEmpty()) Muted("Nada registrado ainda.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(items, key = { it.key }) { item ->
                Panel(Modifier.fillMaxWidth().clickable { nav.go(item.screen) }, padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.width(118.dp)) {
                            Text(fmtDateTime(item.at), style = MaterialTheme.typography.bodySmall)
                            Muted(item.type)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(item.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (item.detail.isNotEmpty()) Muted(item.detail, maxLines = 2)
                        }
                        Muted("editar")
                    }
                }
            }
        }
    }
}
