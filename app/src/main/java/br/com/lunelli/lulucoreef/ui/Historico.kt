package br.com.lunelli.lulucoreef.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
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

private class HistItem(val key: String, val at: Long, val type: String, val title: String, val detail: String, val screen: Screen, val color: androidx.compose.ui.graphics.Color)

/** Jornal: tudo o que aconteceu, por data, cada tipo na cor da sua area. Historico unificado por data, com filtro por tipo. Tocar abre o registro para editar ou apagar. */
@Composable
fun HistoricoScreen(vm: MainViewModel, nav: Nav, inicial: String? = null) {
    val tests by vm.tests.collectAsStateWithLifecycle()
    val changes by vm.changes.collectAsStateWithLifecycle()
    val dosings by vm.dosings.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    val finance by vm.finance.collectAsStateWithLifecycle()
    val livestock by vm.livestock.collectAsStateWithLifecycle()
    val cases by vm.cases.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf(inicial ?: "todos") }

    val rows = remember(tests, changes, dosings, events, products, finance, livestock, cases, filter) {
        val names = products.associate { it.id to it.nome }
        val out = ArrayList<HistItem>()
        if (filter == "todos" || filter == "teste") tests.forEach {
            out += HistItem("t" + it.id, it.medidoEm, "Teste", "${Params.label(it.parametro)}: ${Params.format(it.parametro, it.valor)} ${it.unidade}",
                listOfNotNull(if (it.baixaConfianca) "baixa confiança" else null, it.observacao).joinToString(" · "), Screen.TestForm(it), Luluco.AreaTeste)
        }
        if (filter == "todos" || filter == "tpa") changes.forEach {
            out += HistItem("c" + it.id, it.feitaEm, "TPA", "TPA de ${numShort(it.litros)} L", listOfNotNull(it.sal, it.metodo, it.observacao).joinToString(" · "), Screen.TpaForm(it), Luluco.AreaTpa)
        }
        if (filter == "todos" || filter == "dose") dosings.forEach {
            out += HistItem("d" + it.id, it.dosadoEm, "Dose", "${names[it.productId] ?: "Produto"}: ${formatDose(it.quantidade, it.unidade)}", it.observacao.orEmpty(), Screen.DoseForm(it), Luluco.AreaDose)
        }
        if (filter == "todos" || filter == "evento") events.forEach {
            out += HistItem("e" + it.id, it.ocorridoEm, EVENT_TYPES.firstOrNull { t -> t.first == it.tipo }?.second ?: "Nota", it.texto, "", Screen.EventForm(it), Luluco.AreaJornal)
        }
        if (filter == "todos" || filter == "financas") finance.forEach {
            out += HistItem("f" + it.id, it.lancadoEm, if (it.tipo == "receita") "Venda" else "Gasto", "${it.descricao.ifBlank { catLabel(it.categoria) }}: ${money(it.valor)}",
                listOfNotNull(catLabel(it.categoria), aquarioLabel(it.aquario), it.parcelaTexto()).joinToString(" · "), Screen.FinanceForm(it.id), Luluco.AreaFinancas)
        }
        if (filter == "todos" || filter == "aquario") livestock.forEach {
            out += HistItem("l" + it.id, it.createdAt, "Novo no aquário", "${if (it.quantidade > 1) "${it.quantidade}x " else ""}${it.nomePopular}", it.nomeCientifico.orEmpty(), Screen.LivestockDetail(it.id), Luluco.AreaAnimal)
        }
        if (filter == "todos" || filter == "saude") cases.forEach {
            out += HistItem("h" + it.id, it.abertoEm, if (it.tipo == "quarentena") "Quarentena" else "Saúde", it.titulo, if (it.fechadoEm != null) "encerrado" else "em aberto", Screen.CaseDetail(it.id), Luluco.AreaSaude)
        }
        out.sortedByDescending { it.at }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Jornal", onBack = if (inicial != null) ({ nav.back() }) else null)
        ChipRow(listOf("todos" to "Tudo", "teste" to "Testes", "tpa" to "TPAs", "dose" to "Doses", "aquario" to "Aquário", "saude" to "Saúde", "financas" to "Finanças", "evento" to "Notas"), filter, Modifier.padding(bottom = 10.dp)) { filter = it }
        if (rows.isEmpty()) Muted("Nada registrado ainda.")
        LazyColumn(Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).background(Luluco.Card)) {
            items(rows, key = { it.key }) { item ->
                Row(Modifier.fillMaxWidth().clickable { nav.go(item.screen) }.padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(item.color))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.width(128.dp)) {
                        Text(fmtDateTime(item.at), style = MaterialTheme.typography.bodySmall, color = Luluco.TextSoft)
                        Text(item.type.uppercase(), fontSize = 11.sp, letterSpacing = 0.8.sp, fontWeight = FontWeight.SemiBold, color = lift(item.color))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(item.title, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (item.detail.isNotEmpty()) Muted(item.detail, maxLines = 2)
                    }
                }
                HorizontalDivider(color = Luluco.Divider)
            }
        }
    }
}
