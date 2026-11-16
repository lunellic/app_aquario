package br.com.lunelli.lulucoreef.ui

import android.content.Intent
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lunelli.lulucoreef.data.HealthCase
import br.com.lunelli.lulucoreef.data.Livestock
import java.time.LocalDate

val LIVESTOCK_TYPES = listOf("coral" to "Corais", "anemona" to "Anêmonas", "peixe" to "Peixes", "invertebrado" to "Invertebrados", "alga" to "Algas")
private val TYPE_SINGULAR = mapOf("coral" to "Coral", "anemona" to "Anêmona", "peixe" to "Peixe", "invertebrado" to "Invertebrado", "alga" to "Alga")
private val CATEGORIES = listOf("LPS", "SPS", "mole", "zoa", "outro").map { it to it }
private val STATUSES = listOf("ativo" to "Ativos", "removido" to "Removidos", "morto" to "Mortos")

fun inventoryText(items: List<Livestock>, nome: String): String {
    val active = items.filter { it.status == "ativo" }
    val sb = StringBuilder("$nome - inventário (${fmtDay(System.currentTimeMillis())})\n")
    LIVESTOCK_TYPES.forEach { (tipo, label) ->
        val group = active.filter { it.tipo == tipo }
        if (group.isNotEmpty()) {
            sb.append("\n$label (${group.sumOf { it.quantidade }}):\n")
            group.forEach { l ->
                sb.append("- ")
                if (l.quantidade != 1) sb.append("${l.quantidade}x ")
                sb.append(l.nomePopular)
                l.nomeCientifico?.takeIf { it.isNotBlank() }?.let { sb.append(" ($it)") }
                sb.append('\n')
            }
        }
    }
    return sb.toString()
}

@Composable
fun AquarioScreen(vm: MainViewModel, nav: Nav) {
    val all by vm.livestock.collectAsStateWithLifecycle()
    val tank by vm.tank.collectAsStateWithLifecycle()
    var status by remember { mutableStateOf("ativo") }
    val ctx = LocalContext.current
    val shown = all.filter { it.status == status }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenHeader("Meu Aquário") {
            OutlinedButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, inventoryText(all, tank?.nome ?: "Luluco Reef"))
                ctx.startActivity(Intent.createChooser(send, "Compartilhar lista"))
            }) { Text("Compartilhar lista") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { nav.go(Screen.LivestockForm()) }) { Text("Adicionar") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChipRow(STATUSES.map { (v, l) -> v to "$l (${all.filter { it.status == v }.sumOf { x -> x.quantidade }})" }, status) { status = it }
        }
        val toReview = shown.count { it.revisar }
        if (toReview > 0) Muted("$toReview itens com selo \"confirmar\": toque para revisar nome, quantidade e local.")
        if (shown.isEmpty()) Muted("Nada aqui.")
        LIVESTOCK_TYPES.forEach { (tipo, label) ->
            val group = shown.filter { it.tipo == tipo }
            if (group.isNotEmpty()) {
                SectionTitle("$label (${group.sumOf { it.quantidade }})")
                group.forEach { l -> LivestockRow(l) { nav.go(Screen.LivestockDetail(l.id)) } }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun LivestockRow(l: Livestock, onClick: () -> Unit) {
    Panel(Modifier.fillMaxWidth().clickable(onClick = onClick), padding = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (l.fotoPath != null) Photo(l.fotoPath, Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)), maxPx = 160)
            Column(Modifier.weight(1f)) {
                Text((if (l.quantidade != 1) "${l.quantidade}x " else "") + l.nomePopular, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = listOfNotNull(l.nomeCientifico, l.categoria, l.localNoAquario).filter { it.isNotBlank() }.joinToString(" · ")
                if (sub.isNotEmpty()) Muted(sub, maxLines = 1)
            }
            if (l.revisar) Badge("confirmar", Luluco.Coral)
        }
    }
}

@Composable
fun LivestockDetail(vm: MainViewModel, nav: Nav, id: String) {
    val all by vm.livestock.collectAsStateWithLifecycle()
    val cases by vm.cases.collectAsStateWithLifecycle()
    val l = all.firstOrNull { it.id == id }
    if (l == null) {
        Column { ScreenHeader("Habitante", onBack = { nav.back() }); Muted("Não encontrado.") }
        return
    }
    var confirm by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader(l.nomePopular, onBack = { nav.back() }) {
            Button(onClick = { nav.go(Screen.LivestockForm(l.id)) }) { Text("Editar") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (l.fotoPath != null) Photo(l.fotoPath, Modifier.size(220.dp).clip(RoundedCornerShape(16.dp)), maxPx = 640)
            Panel(Modifier.weight(1f)) {
                if (l.revisar) { Badge("confirmar dados", Luluco.Coral); Spacer(Modifier.height(6.dp)) }
                l.nomeCientifico?.takeIf { it.isNotBlank() }?.let { Text(it, fontStyle = FontStyle.Italic) }
                Field("Tipo", listOfNotNull(TYPE_SINGULAR[l.tipo], l.categoria).joinToString(" · "))
                Field("Quantidade", l.quantidade.toString())
                Field("Local no aquário", l.localNoAquario)
                Field("Adicionado em", fmtIsoDay(l.adicionadoEm))
                Field("Origem", l.origem)
                Field("Status", l.status + (l.removidoEm?.let { " em ${fmtIsoDay(it)}" } ?: ""))
                Field("Observação", l.observacao)
            }
        }
        SpeciesCard(vm, l)
        val mine = cases.filter { it.livestockId == l.id }
        if (mine.isNotEmpty()) {
            SectionTitle("Casos de saúde")
            mine.forEach { c -> Panel(Modifier.fillMaxWidth(), padding = 10.dp) { Text(c.titulo, fontWeight = FontWeight.Bold); Muted("${c.tipo} · aberto em ${fmtDay(c.abertoEm)}" + (if (c.fechadoEm != null) " · fechado" else " · em aberto")) } }
        }
        if (l.tipo == "coral" && l.status == "ativo") OutlinedButton(onClick = { nav.go(Screen.CoralProtocol(l.id)) }) { Text("Protocolo de entrada (banho e aclimatação)") }
        if (l.status == "ativo") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { confirm = "removido" }) { Text("Marcar como removido") }
                OutlinedButton(onClick = { confirm = "morto" }) { Text("Marcar como morto") }
            }
        } else {
            OutlinedButton(onClick = { vm.save(l.copy(status = "ativo", removidoEm = null)) }) { Text("Voltar para ativo") }
        }
    }
    confirm?.let { st ->
        ConfirmDialog("Marcar como $st", "${l.nomePopular} sai da lista de ativos e continua no histórico.", "Confirmar",
            onConfirm = { vm.save(l.copy(status = st, removidoEm = LocalDate.now().toString())); confirm = null; nav.back() }, onDismiss = { confirm = null })
    }
}

@Composable
private fun Field(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Text(
        androidx.compose.ui.text.buildAnnotatedString {
            pushStyle(androidx.compose.ui.text.SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant))
            append("$label: ")
            pop()
            append(value)
        },
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

@Composable
fun LivestockForm(vm: MainViewModel, nav: Nav, id: String?, tipoInicial: String) {
    val all by vm.livestock.collectAsStateWithLifecycle()
    val edit = remember(id) { all.firstOrNull { it.id == id } }
    var tipo by remember { mutableStateOf(edit?.tipo ?: tipoInicial) }
    var nome by remember { mutableStateOf(edit?.nomePopular.orEmpty()) }
    var cientifico by remember { mutableStateOf(edit?.nomeCientifico.orEmpty()) }
    var categoria by remember { mutableStateOf(edit?.categoria) }
    var qtd by remember { mutableStateOf((edit?.quantidade ?: 1).toString()) }
    var local by remember { mutableStateOf(edit?.localNoAquario.orEmpty()) }
    var adicionado by remember { mutableStateOf(edit?.adicionadoEm ?: if (edit == null) LocalDate.now().toString() else null) }
    var origem by remember { mutableStateOf(edit?.origem.orEmpty()) }
    var obs by remember { mutableStateOf(edit?.observacao.orEmpty()) }
    var foto by remember { mutableStateOf(edit?.fotoPath) }
    var confirmDelete by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.importPhoto(uri) { path -> if (path != null) foto = path }
    }
    val q = qtd.toIntOrNull()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader(if (edit != null) "Editar habitante" else "Adicionar habitante", onBack = { nav.back() })
        ChipRow(TYPE_SINGULAR.toList(), tipo) { tipo = it }
        if (tipo == "coral") ChipRow(CATEGORIES, categoria) { categoria = it }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextInput("Nome popular", nome, Modifier.weight(2f)) { nome = it }
            NumInput("Quantidade", qtd, Modifier.weight(1f)) { v -> qtd = v.filter { it.isDigit() } }
        }
        TextInput("Nome científico (opcional)", cientifico) { cientifico = it }
        TextInput("Local no aquário (opcional)", local) { local = it }
        TextInput("Origem (loja, amigo...)", origem) { origem = it }
        DateField("Adicionado em", adicionado) { adicionado = it }
        TextInput("Observação", obs, singleLine = false) { obs = it }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (foto != null) Photo(foto, Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)), maxPx = 256)
            OutlinedButton(onClick = { picker.launch("image/*") }) { Text(if (foto != null) "Trocar foto" else "Escolher foto") }
            if (foto != null) TextButton(onClick = { foto = null }) { Text("Remover foto") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton(if (edit?.revisar == true) "Salvar e confirmar" else "Salvar", Modifier.weight(1f), enabled = nome.isNotBlank() && q != null) {
                val base = edit ?: Livestock(nomePopular = nome.trim())
                val saved = base.copy(
                    tipo = tipo, nomePopular = nome.trim(), nomeCientifico = cientifico.trim().ifBlank { null },
                    categoria = if (tipo == "coral") categoria else null, quantidade = q!!, localNoAquario = local.trim().ifBlank { null },
                    adicionadoEm = adicionado, origem = origem.trim().ifBlank { null }, observacao = obs.trim().ifBlank { null },
                    fotoPath = foto, revisar = false, // editar pelo Lucas confirma o item
                )
                vm.save(saved)
                // habitante novo: pede a ficha da especie em segundo plano (falha em silencio se offline ou sem Bloco C)
                if (edit == null) vm.generateSpecies(saved.id)
                // coral novo: abre o protocolo de entrada
                if (edit == null && tipo == "coral") nav.replace(Screen.CoralProtocol(saved.id)) else nav.back()
            }
            if (edit != null) BigButton("Apagar", Modifier.width(140.dp), color = Luluco.Critical) { confirmDelete = true }
        }
    }
    if (confirmDelete && edit != null) {
        ConfirmDialog("Apagar habitante", "Use só para cadastro errado. Para saída ou morte, marque o status na ficha.", "Apagar",
            onConfirm = { vm.delete(edit); confirmDelete = false; nav.top(Screen.Aquario) }, onDismiss = { confirmDelete = false })
    }
}

/** Protocolo de entrada de coral: banho no Supreme Dip, aclimatacao e oferta de caso de quarentena/observacao. */
@Composable
fun CoralProtocol(vm: MainViewModel, nav: Nav, id: String) {
    val all by vm.livestock.collectAsStateWithLifecycle()
    val l = all.firstOrNull { it.id == id }
    val steps = listOf(
        "Separar um pote limpo com água do próprio aquário (meça quantos litros).",
        "Supreme Dip (Shask): 10 gotas por litro de água no pote.",
        "Banho de 3 a 5 minutos, agitando a água de leve o tempo todo.",
        "Olhar o fundo do pote: pragas soltas (planárias, nudibrânquios, caramujos)? Anote se houver.",
        "Enxaguar o coral em água limpa do aquário e descartar a água do banho. Nunca devolver ao aquário.",
        "Aclimatação: começar em local de luz e fluxo mais baixos e subir aos poucos ao longo dos dias.",
        "Manter distância dos vizinhos, pensando no alcance dos tentáculos à noite.",
    )
    val checked = remember { mutableStateListOf<Int>() }
    var opened by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenHeader("Protocolo de entrada" + (l?.let { ": ${it.nomePopular}" } ?: ""), onBack = { nav.back() })
        steps.forEachIndexed { i, text ->
            Panel(Modifier.fillMaxWidth().clickable { if (i in checked) checked.remove(i) else checked.add(i) }, padding = 8.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = i in checked, onCheckedChange = { on -> if (on) checked.add(i) else checked.remove(i) })
                    Text(text, Modifier.weight(1f))
                }
            }
        }
        SectionTitle("Acompanhar nos primeiros dias?")
        if (opened != null) {
            Muted("Caso de $opened aberto. Ele aparece na ficha do coral e na tela Saúde.")
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("observacao" to "Abrir caso de observação", "quarentena" to "Abrir caso de quarentena").forEach { (tipo, label) ->
                    OutlinedButton(onClick = {
                        vm.save(HealthCase(titulo = "Entrada: ${l?.nomePopular ?: "coral"}", abertoEm = System.currentTimeMillis(), livestockId = id, tipo = tipo,
                            observacao = "Aberto no protocolo de entrada. Etapas marcadas: ${checked.size} de ${steps.size}."))
                        opened = if (tipo == "observacao") "observação" else tipo
                    }) { Text(label) }
                }
            }
        }
        BigButton("Concluir", Modifier.fillMaxWidth()) { nav.back() }
    }
}

/** Ficha da especie gerada pela IA, com compatibilidade com o inventario (spec 11.2). */
@Composable
fun SpeciesCard(vm: MainViewModel, l: Livestock) {
    val infos by vm.speciesInfo.collectAsStateWithLifecycle()
    val busy by vm.aiBusy.collectAsStateWithLifecycle()
    val info = infos.firstOrNull { it.livestockId == l.id }
    val running = "especie:${l.id}" in busy
    var error by remember { mutableStateOf<String?>(null) }
    SectionTitle("Ficha da espécie")
    Panel(Modifier.fillMaxWidth()) {
        val o = remember(info?.conteudoJson) { try { info?.let { org.json.JSONObject(it.conteudoJson) } } catch (_: Exception) { null } }
        if (o == null) {
            Muted(if (running) "Gerando a ficha..." else "Ainda sem ficha para este habitante.")
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Badge(if (info!!.confirmadaPeloUsuario) "conferida por você" else "gerado por IA, confira", if (info.confirmadaPeloUsuario) Luluco.Ok else Luluco.Coral)
                Muted("gerada em ${fmtDay(info.geradaEm)}")
            }
            Spacer(Modifier.height(6.dp))
            Field("Nome científico provável", o.optString("nome_cientifico"))
            Field("Tipo", o.optString("tipo"))
            Field("Dificuldade", o.optString("dificuldade"))
            Field("Agressividade", o.optString("agressividade"))
            Field("Alcance de tentáculos/pólipos", o.optString("alcance_tentaculos"))
            Field("Distância recomendada", o.optString("distancia_recomendada"))
            if (!o.isNull("volume_minimo_l")) Field("Volume mínimo", "${o.optDouble("volume_minimo_l").toInt()} L")
            Field("Parâmetros", o.optString("parametros"))
            Field("Iluminação", o.optString("iluminacao"))
            Field("Fluxo", o.optString("fluxo"))
            Field("Compatibilidade", o.optString("compatibilidade"))
            val conflicts = o.optJSONArray("conflitos")
            if (conflicts != null && conflicts.length() > 0) {
                Text("Conflitos com o inventário:", fontWeight = FontWeight.Bold, color = Luluco.Coral)
                for (i in 0 until conflicts.length()) {
                    val c = conflicts.optJSONObject(i) ?: continue
                    Text("- ${c.optString("com")}: ${c.optString("motivo")}")
                }
            }
            Field("Observações", o.optString("observacoes"))
        }
        error?.let { Text(it, color = Luluco.Coral, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(enabled = !running, onClick = { error = null; vm.generateSpecies(l.id) { error = it } }) {
                Text(if (running) "Gerando..." else if (info == null) "Gerar ficha (IA)" else "Gerar de novo")
            }
            if (info != null && !info.confirmadaPeloUsuario) OutlinedButton(onClick = { vm.save(info.copy(confirmadaPeloUsuario = true)) }) { Text("Conferi, está certa") }
        }
    }
}
