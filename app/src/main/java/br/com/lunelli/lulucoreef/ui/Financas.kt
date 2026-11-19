package br.com.lunelli.lulucoreef.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lunelli.lulucoreef.data.FinanceEntry
import br.com.lunelli.lulucoreef.data.ShoppingItem
import br.com.lunelli.lulucoreef.domain.numShort
import java.text.NumberFormat
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val DESPESA_CATS = listOf(
    "corais_peixes" to "Corais e peixes", "sal_suplementos" to "Sal e suplementos", "equipamentos" to "Equipamentos",
    "manutencao" to "Manutenção", "alimentacao" to "Alimentação", "outros" to "Outros",
)
val RECEITA_CATS = listOf("venda_corais" to "Venda de corais e mudas", "venda_outros" to "Venda de outros itens", "outros" to "Outros")
val AQUARIOS = listOf("marinho" to "Marinho", "doce" to "Água doce", "ambos" to "Ambos")
val PRIORIDADES = listOf("alta" to "Alta", "media" to "Média", "baixa" to "Baixa")

fun catLabel(c: String) = (DESPESA_CATS + RECEITA_CATS).firstOrNull { it.first == c }?.second ?: c
fun aquarioLabel(a: String) = AQUARIOS.firstOrNull { it.first == a }?.second ?: a
private val brl = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
fun money(v: Double): String = brl.format(v).replace(' ', ' ')
fun parseMoney(s: String): Double? = s.replace(".", "").replace(',', '.').trim().toDoubleOrNull()
    ?: s.replace(',', '.').trim().toDoubleOrNull()
private fun prioColor(p: String) = when (p) { "alta" -> Luluco.Critical; "media" -> Luluco.CoralText; else -> Luluco.Neutral }
private fun YearMonth.label(): String = format(DateTimeFormatter.ofPattern("MMMM 'de' yyyy", PtBr)).replaceFirstChar { it.uppercase() }
private fun Long.ym(zone: ZoneId): YearMonth = YearMonth.from(Instant.ofEpochMilli(this).atZone(zone))

@Composable
fun FinancasScreen(vm: MainViewModel, nav: Nav) {
    var aba by remember { mutableStateOf("resumo") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader("Finanças") {
            Button(onClick = { nav.go(if (aba == "compras") Screen.ShoppingForm() else Screen.FinanceForm()) }) {
                Text(if (aba == "compras") "Novo item" else "Novo lançamento")
            }
        }
        ChipRow(listOf("resumo" to "Resumo do mês", "compras" to "Lista de compras"), aba) { aba = it }
        if (aba == "resumo") Resumo(vm, nav) else Compras(vm, nav)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Resumo(vm: MainViewModel, nav: Nav) {
    val all by vm.finance.collectAsStateWithLifecycle()
    val zone = vm.zone
    var mes by remember { mutableStateOf(YearMonth.now(zone)) }
    var filtro by remember { mutableStateOf("todos") }
    val entries = all.filter { filtro == "todos" || it.aquario == filtro || it.aquario == "ambos" }
    val doMes = entries.filter { it.lancadoEm.ym(zone) == mes }
    val gastos = doMes.filter { it.tipo == "despesa" }.sumOf { it.valor }
    val receitas = doMes.filter { it.tipo == "receita" }.sumOf { it.valor }

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { mes = mes.minusMonths(1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Mês anterior") }
        Text(mes.label(), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = { mes = mes.plusMonths(1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Próximo mês") }
        Spacer(Modifier.weight(1f))
        ChipRow(listOf("todos" to "Todos", "marinho" to "Marinho", "doce" to "Água doce"), filtro) { filtro = it }
    }

    // saldo, gastos e vendas em um so card com divisorias
    Panel(Modifier.fillMaxWidth(), padding = 0.dp) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Money(Modifier.weight(1f), "Saldo do mês", receitas - gastos, if (receitas - gastos >= 0) Luluco.Ok else Luluco.CoralText)
            VerticalDivider(color = Luluco.Divider)
            Money(Modifier.weight(1f), "Gastos", gastos, null)
            VerticalDivider(color = Luluco.Divider)
            Money(Modifier.weight(1f), "Vendas e receitas", receitas, null)
        }
        HorizontalDivider(color = Luluco.Divider)
        // ultimos 6 meses: gasto (cinza) e receita (verde) lado a lado
        val meses = (5 downTo 0).map { mes.minusMonths(it.toLong()) }
        val serie = meses.map { m -> entries.filter { it.lancadoEm.ym(zone) == m }.let { l -> l.filter { it.tipo == "despesa" }.sumOf { it.valor } to l.filter { it.tipo == "receita" }.sumOf { it.valor } } }
        val maxV = serie.maxOf { maxOf(it.first, it.second) }.coerceAtLeast(1.0)
        Column(Modifier.padding(16.dp)) {
            Eyebrow("Últimos 6 meses")
            Spacer(Modifier.height(10.dp))
            Canvas(Modifier.fillMaxWidth().height(70.dp)) {
                val slot = size.width / serie.size
                val bw = slot * 0.18f
                serie.forEachIndexed { i, (g, r) ->
                    val x = slot * i + slot / 2
                    val hg = (g / maxV).toFloat() * size.height
                    val hr = (r / maxV).toFloat() * size.height
                    drawRoundRect(Color(0xFF8B95A3), Offset(x - bw - 2f, size.height - hg), Size(bw, hg), CornerRadius(4f, 4f))
                    drawRoundRect(Luluco.Ok, Offset(x + 2f, size.height - hr), Size(bw, hr), CornerRadius(4f, 4f))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                meses.forEach { m -> Text(m.format(DateTimeFormatter.ofPattern("MMM", PtBr)), Modifier.weight(1f), fontSize = 12.sp, color = Luluco.TextSoft, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
        }
    }

    // gasto por categoria
    val porCat = doMes.filter { it.tipo == "despesa" }.groupBy { it.categoria }.mapValues { e -> e.value.sumOf { it.valor } }.toList().sortedByDescending { it.second }
    if (porCat.isNotEmpty()) {
        SectionTitle("Para onde foi o dinheiro")
        Panel(Modifier.fillMaxWidth(), padding = 0.dp) {
            porCat.forEachIndexed { i, (c, v) ->
                if (i > 0) HorizontalDivider(color = Luluco.Divider)
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row { Text(catLabel(c), Modifier.weight(1f)); Text(money(v), fontWeight = FontWeight.Medium) }
                    Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.06f))) {
                        Box(Modifier.fillMaxWidth((v / gastos).toFloat().coerceIn(0.02f, 1f)).fillMaxHeight().background(Color(0xFFB9C0CA)))
                    }
                }
            }
        }
    }

    SectionTitle("Lançamentos")
    if (doMes.isEmpty()) Muted("Nada lançado neste mês. Use \"Novo lançamento\" ou marque um item da lista de compras como comprado.")
    else Panel(Modifier.fillMaxWidth(), padding = 0.dp) {
        doMes.forEachIndexed { i, e ->
            if (i > 0) HorizontalDivider(color = Luluco.Divider)
            Row(Modifier.fillMaxWidth().clickable { nav.go(Screen.FinanceForm(e.id)) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(fmtDay(e.lancadoEm), Modifier.width(64.dp), color = Luluco.TextSoft, fontSize = 14.sp)
                Column(Modifier.weight(1f)) {
                    Text(e.descricao.ifBlank { catLabel(e.categoria) }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Muted("${catLabel(e.categoria)} · ${aquarioLabel(e.aquario)}", maxLines = 1)
                }
                Text((if (e.tipo == "receita") "+ " else "- ") + money(e.valor), fontWeight = FontWeight.Medium, color = if (e.tipo == "receita") Luluco.Ok else Luluco.Cream)
            }
        }
    }
}

@Composable
private fun Money(modifier: Modifier, label: String, v: Double, color: Color?) {
    Column(modifier.padding(16.dp)) {
        Eyebrow(label)
        Spacer(Modifier.height(6.dp))
        Text(money(v), fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 26.sp, letterSpacing = (-0.6).sp, color = color ?: Luluco.Cream, maxLines = 1)
    }
}

@Composable
private fun Compras(vm: MainViewModel, nav: Nav) {
    val items by vm.shopping.collectAsStateWithLifecycle()
    var comprando by remember { mutableStateOf<ShoppingItem?>(null) }
    val ordem = mapOf("alta" to 0, "media" to 1, "baixa" to 2)
    val abertos = items.filter { it.compradoEm == null }.sortedWith(compareBy({ ordem[it.prioridade] ?: 3 }, { it.nome }))
    val comprados = items.filter { it.compradoEm != null }.sortedByDescending { it.compradoEm }

    val previsto = abertos.sumOf { it.valorMedio ?: 0.0 }
    Panel(Modifier.fillMaxWidth(), padding = 0.dp) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Money(Modifier.weight(1f), "Previsto na lista", previsto, null)
            VerticalDivider(color = Luluco.Divider)
            Money(Modifier.weight(1f), "Só prioridade alta", abertos.filter { it.prioridade == "alta" }.sumOf { it.valorMedio ?: 0.0 }, null)
        }
    }
    if (abertos.isEmpty()) Muted("Lista vazia. Toque em \"Novo item\" para anotar o que precisa comprar.")
    else Panel(Modifier.fillMaxWidth(), padding = 0.dp) {
        abertos.forEachIndexed { i, it ->
            if (i > 0) HorizontalDivider(color = Luluco.Divider)
            Row(Modifier.fillMaxWidth().clickable { nav.go(Screen.ShoppingForm(it.id)) }.padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(prioColor(it.prioridade)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(it.nome, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Muted("${catLabel(it.categoria)} · ${aquarioLabel(it.aquario)}", maxLines = 1)
                }
                Text(it.valorMedio?.let { v -> "~" + money(v) } ?: "", color = Luluco.TextSoft)
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { comprando = it }) { Text("Comprei") }
            }
        }
    }
    if (comprados.isNotEmpty()) {
        SectionTitle("Comprados")
        Panel(Modifier.fillMaxWidth(), padding = 0.dp) {
            comprados.take(15).forEachIndexed { i, it ->
                if (i > 0) HorizontalDivider(color = Luluco.Divider)
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(it.nome, color = Luluco.TextSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Muted("${fmtDay(it.compradoEm!!)} · ${money(it.valorPago ?: 0.0)}", maxLines = 1)
                    }
                    TextButton(onClick = { vm.unbuy(it) }) { Text("Desfazer") }
                }
            }
        }
    }
    comprando?.let { item ->
        var valor by remember(item.id) { mutableStateOf(item.valorMedio?.let { numShort(it) } ?: "") }
        var quando by remember(item.id) { mutableLongStateOf(System.currentTimeMillis()) }
        AlertDialog(
            onDismissRequest = { comprando = null },
            title = { Text("Comprei: ${item.nome}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumInput("Valor pago", valor, Modifier.fillMaxWidth(), suffix = "R$") { valor = it }
                    DateTimeField("Quando", quando) { quando = it }
                    Muted("Vira uma despesa de ${catLabel(item.categoria)} no mês.")
                }
            },
            confirmButton = { TextButton(enabled = parseMoney(valor) != null, onClick = { vm.buy(item, parseMoney(valor)!!, quando); comprando = null }) { Text("Lançar") } },
            dismissButton = { TextButton(onClick = { comprando = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
fun FinanceForm(vm: MainViewModel, nav: Nav, id: String?) {
    val all by vm.finance.collectAsStateWithLifecycle()
    val edit = remember(id) { all.firstOrNull { it.id == id } }
    var tipo by remember { mutableStateOf(edit?.tipo ?: "despesa") }
    var categoria by remember { mutableStateOf(edit?.categoria ?: "corais_peixes") }
    var descricao by remember { mutableStateOf(edit?.descricao.orEmpty()) }
    var valor by remember { mutableStateOf(edit?.valor?.let { numShort(it) } ?: "") }
    var aquario by remember { mutableStateOf(edit?.aquario ?: "marinho") }
    var quando by remember { mutableLongStateOf(edit?.lancadoEm ?: System.currentTimeMillis()) }
    var obs by remember { mutableStateOf(edit?.observacao.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val cats = if (tipo == "despesa") DESPESA_CATS else RECEITA_CATS
    if (cats.none { it.first == categoria }) categoria = cats.first().first

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader(if (edit != null) "Editar lançamento" else "Novo lançamento", onBack = { nav.back() })
        ChipRow(listOf("despesa" to "Gasto", "receita" to "Venda ou receita"), tipo) { tipo = it }
        ChipRow(cats, categoria) { categoria = it }
        TextInput(if (tipo == "despesa") "O que foi (ex.: Torch Holy Grail)" else "O que foi vendido (ex.: 3 mudas de zoa)", descricao) { descricao = it }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            NumInput("Valor", valor, Modifier.width(220.dp), suffix = "R$") { valor = it }
            DateTimeField("Data", quando) { quando = it }
        }
        SectionTitle("Aquário")
        ChipRow(AQUARIOS, aquario) { aquario = it }
        TextInput("Observação", obs, singleLine = false) { obs = it }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton("Salvar", Modifier.weight(1f), enabled = parseMoney(valor) != null) {
                val b = edit ?: FinanceEntry()
                vm.save(b.copy(tipo = tipo, categoria = categoria, descricao = descricao.trim(), valor = parseMoney(valor)!!, aquario = aquario, lancadoEm = quando, observacao = obs.trim().ifBlank { null }))
                nav.back()
            }
            if (edit != null) BigButton("Apagar", Modifier.width(140.dp), color = Luluco.Solid) { confirmDelete = true }
        }
    }
    if (confirmDelete && edit != null) {
        ConfirmDialog("Apagar lançamento", "Ele sai do balanço do mês.", "Apagar",
            onConfirm = { vm.delete(edit); confirmDelete = false; nav.back() }, onDismiss = { confirmDelete = false })
    }
}

@Composable
fun ShoppingForm(vm: MainViewModel, nav: Nav, id: String?) {
    val all by vm.shopping.collectAsStateWithLifecycle()
    val edit = remember(id) { all.firstOrNull { it.id == id } }
    var nome by remember { mutableStateOf(edit?.nome.orEmpty()) }
    var categoria by remember { mutableStateOf(edit?.categoria ?: "sal_suplementos") }
    var valor by remember { mutableStateOf(edit?.valorMedio?.let { numShort(it) } ?: "") }
    var prioridade by remember { mutableStateOf(edit?.prioridade ?: "media") }
    var aquario by remember { mutableStateOf(edit?.aquario ?: "marinho") }
    var obs by remember { mutableStateOf(edit?.observacao.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader(if (edit != null) "Editar item" else "Novo item da lista", onBack = { nav.back() })
        TextInput("O que comprar (ex.: Magnésio Buffer)", nome) { nome = it }
        NumInput("Valor médio", valor, Modifier.width(220.dp), suffix = "R$") { valor = it }
        SectionTitle("Prioridade")
        ChipRow(PRIORIDADES, prioridade) { prioridade = it }
        SectionTitle("Categoria")
        ChipRow(DESPESA_CATS, categoria) { categoria = it }
        SectionTitle("Aquário")
        ChipRow(AQUARIOS, aquario) { aquario = it }
        TextInput("Observação (loja, link, tamanho)", obs, singleLine = false) { obs = it }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton("Salvar", Modifier.weight(1f), enabled = nome.isNotBlank()) {
                val b = edit ?: ShoppingItem()
                vm.save(b.copy(nome = nome.trim(), categoria = categoria, valorMedio = parseMoney(valor), prioridade = prioridade, aquario = aquario, observacao = obs.trim().ifBlank { null }))
                nav.back()
            }
            if (edit != null) BigButton("Apagar", Modifier.width(140.dp), color = Luluco.Solid) { confirmDelete = true }
        }
    }
    if (confirmDelete && edit != null) {
        ConfirmDialog("Apagar item", "Sai da lista de compras.", "Apagar",
            onConfirm = { vm.delete(edit); confirmDelete = false; nav.back() }, onDismiss = { confirmDelete = false })
    }
}
