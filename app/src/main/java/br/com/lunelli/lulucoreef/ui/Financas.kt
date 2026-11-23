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

/** Ciclo da fatura: do dia [dia] de um mes ate a vespera do dia [dia] do mes seguinte. O ciclo leva o nome do mes em que termina. */
fun cycleOf(at: Long, dia: Int, zone: ZoneId): YearMonth {
    val d = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
    val ym = YearMonth.from(d)
    return if (d.dayOfMonth >= dia) ym.plusMonths(1) else ym
}
fun cycleRange(ym: YearMonth, dia: Int): String {
    val ini = ym.minusMonths(1).atDay(minOf(dia, ym.minusMonths(1).lengthOfMonth()))
    val fim = ym.atDay(minOf(dia, ym.lengthOfMonth())).minusDays(1)
    val f = DateTimeFormatter.ofPattern("dd/MM")
    return "${ini.format(f)} a ${fim.format(f)}"
}
private fun Long.ym(zone: ZoneId, dia: Int): YearMonth = cycleOf(this, dia, zone)

/** Saldo da caixinha: depositos e vendas entram; gastos no Pix e retiradas saem; cartao nao mexe. */
fun caixaDelta(e: FinanceEntry): Double = when (e.tipo) {
    "aporte", "receita" -> e.valor
    "retirada" -> -e.valor
    "despesa" -> if (e.forma == "pix") -e.valor else 0.0
    else -> 0.0
}
fun FinanceEntry.parcelaTexto(): String? = if (parcela != null && parcelas != null && parcelas > 1) "parcela $parcela/$parcelas" else null

/** Gasto no cartao que ainda nao foi quitado. Pix sai da caixinha na hora, entao nunca fica em aberto. */
fun FinanceEntry.emAberto(): Boolean = tipo == "despesa" && forma == "cartao" && !pago

/** Da para marcar como paga? So gasto no cartao; no Pix o dinheiro ja saiu. */
fun FinanceEntry.podeQuitar(): Boolean = tipo == "despesa" && forma == "cartao"
fun FinanceEntry.formaTexto(): String = when (tipo) { "aporte" -> "Depósito na caixinha"; "retirada" -> "Retirada da caixinha"; else -> if (forma == "cartao") "Cartão" else "Pix" }

@Composable
fun FinancasScreen(vm: MainViewModel, nav: Nav) {
    var aba by remember { mutableStateOf("resumo") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader("Finanças") {
            Button(onClick = { nav.go(if (aba == "compras") Screen.ShoppingForm() else Screen.FinanceForm()) }) {
                Text(if (aba == "compras") "Novo item" else "Novo lançamento")
            }
        }
        ChipRow(listOf("resumo" to "Resumo do ciclo", "caixinha" to "Caixinha", "compras" to "Lista de compras"), aba) { aba = it }
        when (aba) { "resumo" -> Resumo(vm, nav); "caixinha" -> Caixinha(vm, nav); else -> Compras(vm, nav) }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Resumo(vm: MainViewModel, nav: Nav) {
    val all by vm.finance.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val zone = vm.zone
    val dia = settings.cicloDia
    var mes by remember { mutableStateOf(cycleOf(System.currentTimeMillis(), dia, zone)) }
    var filtro by remember { mutableStateOf("todos") }
    val entries = all.filter { (it.tipo == "despesa" || it.tipo == "receita") && (filtro == "todos" || it.aquario == filtro || it.aquario == "ambos") }
    val doMes = entries.filter { it.lancadoEm.ym(zone, dia) == mes }
    val cartao = doMes.filter { it.tipo == "despesa" && it.forma == "cartao" }.sumOf { it.valor }
    val gastos = doMes.filter { it.tipo == "despesa" }.sumOf { it.valor }
    val receitas = doMes.filter { it.tipo == "receita" }.sumOf { it.valor }
    // o que ainda nao foi quitado, neste ciclo e no total (parcelas futuras incluidas)
    val aPagarMes = doMes.filter { it.emAberto() }.sumOf { it.valor }
    val aPagarTudo = entries.filter { it.emAberto() }.sumOf { it.valor }

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { mes = mes.minusMonths(1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Mês anterior") }
        Column {
            Text("Fatura de " + mes.label().lowercase(), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Muted(cycleRange(mes, dia))
        }
        IconButton(onClick = { mes = mes.plusMonths(1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Próximo mês") }
        Spacer(Modifier.weight(1f))
        ChipRow(listOf("todos" to "Todos", "marinho" to "Marinho", "doce" to "Água doce"), filtro) { filtro = it }
    }

    // Caixinha primeiro, e nao mais escondida na aba propria: e o unico numero que responde
    // "posso comprar isso?", que e a pergunta que leva alguem a abrir Financas. Os outros contam o
    // que ja aconteceu. "No cartao" saiu da fileira e virou a linha fina embaixo de Gastos: era um
    // recorte de Gastos ocupando o mesmo tamanho do total, e com cinco numeros do mesmo peso
    // nenhum deles se le primeiro.
    val caixaHoje = all.sumOf { caixaDelta(it) }
    Panel(Modifier.fillMaxWidth(), padding = 0.dp) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Money(Modifier.weight(1f), "Caixinha hoje", caixaHoje, if (caixaHoje > 0) Luluco.ZoneIdeal else Luluco.TextSoft,
                nota = if (all.none { caixaDelta(it) != 0.0 }) "nenhum depósito ainda" else "saldo atual, não é do mês")
            VerticalDivider(color = Luluco.Divider)
            Money(Modifier.weight(1f), "Gastos do ciclo", gastos, null,
                nota = buildList {
                    if (cartao > 0) add("${money(cartao)} no cartão")
                    if (aPagarMes > 0) add("${money(aPagarMes)} a pagar")
                }.joinToString(" · ").ifBlank { null })
            VerticalDivider(color = Luluco.Divider)
            Money(Modifier.weight(1f), "Saldo do mês", receitas - gastos, if (receitas - gastos >= 0) Luluco.ZoneIdeal else Luluco.ZoneWarn)
            VerticalDivider(color = Luluco.Divider)
            Money(Modifier.weight(1f), "Vendas", receitas, null)
        }
        HorizontalDivider(color = Luluco.Divider)
        // ultimos 6 meses: gasto (cinza) e receita (verde) lado a lado
        val meses = (5 downTo 0).map { mes.minusMonths(it.toLong()) }
        val serie = meses.map { m -> entries.filter { it.lancadoEm.ym(zone, dia) == m }.let { l -> l.filter { it.tipo == "despesa" }.sumOf { it.valor } to l.filter { it.tipo == "receita" }.sumOf { it.valor } } }
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

    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionTitle("Lançamentos", Modifier.weight(1f))
        Muted("Ciclo começa no dia ")
        NumInput("", dia.toString(), Modifier.width(70.dp)) { v -> v.toIntOrNull()?.let { n -> if (n in 1..28) vm.updateSettings { it.copy(cicloDia = n) } } }
    }
    if (aPagarTudo > 0) Muted("Em aberto no cartão, somando as parcelas futuras: " + money(aPagarTudo) + ". Toque numa parcela já paga para quitá-la.")
    if (doMes.isEmpty()) Muted("Nada lançado neste mês. Use \"Novo lançamento\" ou marque um item da lista de compras como comprado.")
    else Panel(Modifier.fillMaxWidth(), padding = 0.dp) {
        doMes.forEachIndexed { i, e ->
            if (i > 0) HorizontalDivider(color = Luluco.Divider)
            Lancamento(vm, nav, e)
        }
    }
}

/**
 * Uma linha de lancamento. Gasto no cartao abre o dialogo de quitar; o resto vai direto para a ficha.
 * Marcar como paga nao muda o gasto do ciclo (o custo foi real), muda o "a pagar".
 */
@Composable
private fun Lancamento(vm: MainViewModel, nav: Nav, e: FinanceEntry) {
    var dialogo by remember { mutableStateOf(false) }
    if (dialogo) QuitarDialog(vm, nav, e) { dialogo = false }
    val quitado = e.podeQuitar() && e.pago
    Row(
        Modifier.fillMaxWidth().clickable { if (e.podeQuitar()) dialogo = true else nav.go(Screen.FinanceForm(e.id)) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(fmtDay(e.lancadoEm), Modifier.width(64.dp), color = Luluco.TextSoft, fontSize = 14.sp)
        Column(Modifier.weight(1f)) {
            Text(e.descricao.ifBlank { catLabel(e.categoria) }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (quitado) Luluco.TextSoft else Luluco.Cream)
            Muted(listOfNotNull(catLabel(e.categoria), aquarioLabel(e.aquario), e.formaTexto(), e.parcelaTexto()).joinToString(" · "), maxLines = 1)
        }
        if (quitado) Badge("paga", Luluco.Ok) else if (e.emAberto()) Badge("a pagar", Luluco.CoralText)
        Spacer(Modifier.width(10.dp))
        Text(
            (if (e.tipo == "receita") "+ " else "- ") + money(e.valor), fontWeight = FontWeight.Medium,
            color = if (e.tipo == "receita") Luluco.Ok else if (quitado) Luluco.TextFaint else Luluco.Cream,
        )
    }
}

/** Quitar a parcela, quitar a compra inteira ou abrir a ficha. */
@Composable
private fun QuitarDialog(vm: MainViewModel, nav: Nav, e: FinanceEntry, onDismiss: () -> Unit) {
    val all by vm.finance.collectAsStateWithLifecycle()
    val irmas = e.grupo?.let { g -> all.filter { it.grupo == g && it.deletedAt == null } }.orEmpty()
    val abertasNoGrupo = irmas.count { it.emAberto() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(e.descricao.ifBlank { catLabel(e.categoria) }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(listOfNotNull(money(e.valor), e.parcelaTexto(), "Cartão").joinToString(" · "))
                Muted(
                    if (e.pago) "Esta parcela está marcada como paga."
                    else "Marcar como paga só tira do \"a pagar\". O gasto continua contando no ciclo em que foi lançado.",
                )
                if (abertasNoGrupo > 1) Muted("Esta compra tem $abertasNoGrupo parcelas em aberto.")
            }
        },
        confirmButton = {
            if (e.pago) TextButton(onClick = { vm.setFinancePago(listOf(e), false); onDismiss() }) { Text("Desmarcar") }
            else TextButton(onClick = { vm.setFinancePago(listOf(e), true); onDismiss() }) { Text("Já paguei esta") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { nav.go(Screen.FinanceForm(e.id)); onDismiss() }) { Text("Editar") }
                if (!e.pago && abertasNoGrupo > 1) TextButton(onClick = { vm.setFinancePago(irmas.filter { it.emAberto() }, true); onDismiss() }) { Text("Quitar todas") }
            }
        },
    )
}

@Composable
private fun Caixinha(vm: MainViewModel, nav: Nav) {
    val all by vm.finance.collectAsStateWithLifecycle()
    val movs = all.filter { caixaDelta(it) != 0.0 }.sortedBy { it.lancadoEm }
    var acc = 0.0
    val linhas = movs.map { acc += caixaDelta(it); it to acc }.reversed()
    val saldo = acc
    Panel(Modifier.fillMaxWidth(), padding = 0.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Money(Modifier.weight(1f), "Saldo da caixinha", saldo, if (saldo >= 0) Luluco.Cream else Luluco.CoralText)
            Row(Modifier.padding(end = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { nav.go(Screen.FinanceForm(null, "aporte")) }) { Text("Depositar") }
                OutlinedButton(onClick = { nav.go(Screen.FinanceForm(null, "retirada")) }) { Text("Retirar") }
            }
        }
    }
    Muted("Entram depósitos e vendas. Saem gastos no Pix e retiradas. Compras no cartão não mexem na caixinha.")
    SectionTitle("Histórico da caixinha")
    if (linhas.isEmpty()) Muted("Nenhum movimento ainda.")
    else Panel(Modifier.fillMaxWidth(), padding = 0.dp) {
        linhas.take(60).forEachIndexed { i, (e, s) ->
            if (i > 0) HorizontalDivider(color = Luluco.Divider)
            val d = caixaDelta(e)
            Row(Modifier.fillMaxWidth().clickable { nav.go(Screen.FinanceForm(e.id)) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(fmtDay(e.lancadoEm), Modifier.width(64.dp), color = Luluco.TextSoft, fontSize = 14.sp)
                Column(Modifier.weight(1f)) {
                    Text(e.descricao.ifBlank { e.formaTexto() }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Muted(e.formaTexto(), maxLines = 1)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text((if (d > 0) "+ " else "- ") + money(kotlin.math.abs(d)), fontWeight = FontWeight.Medium, color = if (d > 0) Luluco.Ok else Luluco.Cream)
                    Muted("saldo " + money(s))
                }
            }
        }
    }
}

@Composable
private fun Money(modifier: Modifier, label: String, v: Double, color: Color?, nota: String? = null) {
    Column(modifier.padding(16.dp)) {
        Eyebrow(label)
        Spacer(Modifier.height(6.dp))
        Text(money(v), fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, letterSpacing = (-0.6).sp,
            color = color ?: Luluco.Cream, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (nota != null) {
            Spacer(Modifier.height(3.dp))
            Text(nota, fontSize = 11.sp, color = Luluco.TextFaint, maxLines = 2, lineHeight = 13.sp)
        }
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
        var forma by remember(item.id) { mutableStateOf("pix") }
        var parc by remember(item.id) { mutableIntStateOf(1) }
        AlertDialog(
            onDismissRequest = { comprando = null },
            title = { Text("Comprei: ${item.nome}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumInput("Valor pago", valor, Modifier.fillMaxWidth(), suffix = "R$") { valor = it }
                    DateTimeField("Quando", quando) { quando = it }
                    PagamentoPicker(forma, parc, parseMoney(valor), { forma = it }, { parc = it })
                    Muted("Vira uma despesa de ${catLabel(item.categoria)}.")
                }
            },
            confirmButton = { TextButton(enabled = parseMoney(valor) != null, onClick = { vm.buy(item, parseMoney(valor)!!, quando, forma, if (forma == "cartao") parc else 1); comprando = null }) { Text("Lançar") } },
            dismissButton = { TextButton(onClick = { comprando = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
fun FinanceForm(vm: MainViewModel, nav: Nav, id: String?, tipoInicial: String? = null) {
    val all by vm.finance.collectAsStateWithLifecycle()
    val edit = remember(id) { all.firstOrNull { it.id == id } }
    var tipo by remember { mutableStateOf(edit?.tipo ?: tipoInicial ?: "despesa") }
    var categoria by remember { mutableStateOf(edit?.categoria ?: "corais_peixes") }
    var descricao by remember { mutableStateOf(edit?.descricao.orEmpty()) }
    var valor by remember { mutableStateOf(edit?.valor?.let { numShort(it) } ?: "") }
    var aquario by remember { mutableStateOf(edit?.aquario ?: "marinho") }
    var quando by remember { mutableLongStateOf(edit?.lancadoEm ?: System.currentTimeMillis()) }
    var obs by remember { mutableStateOf(edit?.observacao.orEmpty()) }
    var forma by remember { mutableStateOf(edit?.forma ?: "pix") }
    var parcelas by remember { mutableStateOf("1") }
    var confirmDelete by remember { mutableStateOf(false) }
    val cats = if (tipo == "receita") RECEITA_CATS else DESPESA_CATS
    if (cats.none { it.first == categoria }) categoria = cats.first().first
    val caixa = tipo == "aporte" || tipo == "retirada"
    val nParc = if (forma == "cartao" && edit == null) (parcelas.toIntOrNull() ?: 1).coerceIn(1, 24) else 1

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader(if (edit != null) "Editar lançamento" else "Novo lançamento", onBack = { nav.back() })
        ChipRow(listOf("despesa" to "Gasto", "receita" to "Venda", "aporte" to "Depósito na caixinha", "retirada" to "Retirada"), tipo) { tipo = it }
        if (!caixa) ChipRow(cats, categoria) { categoria = it }
        TextInput(when (tipo) { "despesa" -> "O que foi (ex.: Torch Holy Grail)"; "receita" -> "O que foi vendido (ex.: 3 mudas de zoa)"; else -> "Descrição (opcional)" }, descricao) { descricao = it }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            NumInput(if (nParc > 1) "Valor total" else "Valor", valor, Modifier.width(220.dp), suffix = "R$") { valor = it }
            DateTimeField("Data", quando) { quando = it }
        }
        if (tipo == "despesa") {
            if (edit == null) PagamentoPicker(forma, parcelas.toIntOrNull() ?: 1, parseMoney(valor), { forma = it }, { parcelas = it.toString() })
            else ChipRow(listOf("pix" to "Pix (sai da caixinha)", "cartao" to "Cartão de crédito"), forma) { forma = it }
            if (edit?.parcelaTexto() != null) Muted("Esta é a ${edit.parcelaTexto()}. Salvar ou apagar mexe só nesta parcela.")
        }
        if (!caixa) {
            SectionTitle("Aquário")
            ChipRow(AQUARIOS, aquario) { aquario = it }
        }
        TextInput("Observação", obs, singleLine = false) { obs = it }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton("Salvar", Modifier.weight(1f), enabled = parseMoney(valor) != null) {
                val b = edit ?: FinanceEntry()
                val base = b.copy(tipo = tipo, categoria = if (caixa) "outros" else categoria, descricao = descricao.trim(), valor = parseMoney(valor)!!, aquario = aquario,
                    lancadoEm = quando, observacao = obs.trim().ifBlank { null }, forma = if (tipo == "despesa") forma else "pix")
                if (nParc > 1) vm.saveInstallments(base, nParc) else vm.save(base)
                nav.back()
            }
            if (edit != null) BigButton("Apagar", Modifier.width(140.dp), color = Luluco.Solid) { confirmDelete = true }
        }
    }
    if (confirmDelete && edit != null) {
        ConfirmDialog("Apagar lançamento", if (edit.parcelaTexto() != null) "Apaga só a ${edit.parcelaTexto()}." else "Ele sai do balanço e da caixinha.", "Apagar",
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

/** Pix ou credito; no credito aparece a roleta de parcelas (1 a 24). */
@Composable
fun PagamentoPicker(forma: String, parcelas: Int, total: Double?, onForma: (String) -> Unit, onParcelas: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Eyebrow("Pagou com")
        ChipRow(listOf("pix" to "Pix (sai da caixinha)", "cartao" to "Crédito"), forma) { onForma(it) }
        if (forma == "cartao") {
            val labels = remember { (1..24).map { if (it == 1) "à vista" else "${it}x" } }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WheelPicker(labels, parcelas.coerceIn(1, 24) - 1, { onParcelas(it + 1) }, Modifier.width(120.dp))
                total?.let { v -> Muted(if (parcelas > 1) "$parcelas parcelas de ${money(v / parcelas)}, uma por fatura." else "Uma parcela só, na fatura do ciclo.") }
            }
        }
    }
}
