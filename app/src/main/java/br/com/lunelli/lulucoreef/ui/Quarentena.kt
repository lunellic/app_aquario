package br.com.lunelli.lulucoreef.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lunelli.lulucoreef.data.HealthCase
import br.com.lunelli.lulucoreef.data.Routine
import br.com.lunelli.lulucoreef.domain.numShort
import br.com.lunelli.lulucoreef.domain.toLocalDate
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Uma etapa do protocolo: no dia [dia] (0 = dia em que o peixe chega). [lembrete] vira atividade no calendario. */
class QStep(val dia: Int, val titulo: String, val texto: String, val lembrete: Boolean = true)

/** Protocolo de quarentena. Os textos sao montados com a litragem do aquario de quarentena. */
class QProtocol(
    val id: String, val nome: String, val contra: String, val duracao: String, val equipamento: List<String>,
    val cuidados: List<String>, val passos: (litros: Double) -> List<QStep>,
)

private fun gotas(l: Double, porLitros: Double, gotas: Double) = (l / porLitros * gotas).roundToInt()

val PROTOCOLS = listOf(
    QProtocol(
        "cobre", "Cobre (Cupramine) + Prazipro", "Íctio e oodinium; Prazipro para vermes", "cerca de 6 semanas",
        listOf("Caixa ou aquário simples, filtro HOB e aquecedor", "Mídia biológica já colonizada (não volta para o aquário principal depois do cobre)",
            "Tubos de PVC como esconderijo, sem substrato", "Ammonia Alert (o cobre atrapalha os testes comuns de amônia)", "Teste de cobre: Sera ou Hanna Copper HR"),
        listOf("Mantenha o cobre entre 0,30 e 0,60 ppm. Meça todo dia.", "Depois de TPA, reponha o cobre na proporção da água nova.",
            "Retire sobras de ração logo depois de alimentar.", "Nunca use cobre com invertebrados ou corais."),
    ) { l ->
        listOf(
            QStep(0, "Chegada", "Aclimate igualando temperatura e salinidade. Evite gotejamento longo (a amônia do saco fica mais tóxica com pH alto). Observe e alimente pouco.", false),
            QStep(3, "Cupramine: 1ª dose", "Depois de 2 a 4 dias de observação, comece o cobre: ${gotas(l, 40.0, 20.0)} gotas para ${numShort(l)} L (20 gotas a cada 40 L)."),
            QStep(5, "Cupramine: reforço", "48 h depois, repita: ${gotas(l, 40.0, 20.0)} gotas. Meça: alvo 0,30 a 0,60 ppm."),
        ) + (6..30).map { d -> QStep(d, "Medir cobre", "Meça o cobre (alvo 0,30 a 0,60 ppm) e ajuste. Dia ${d - 2} de cobre: o mínimo é 14, o ideal é 21 a 28.") } +
            listOf(
                QStep(31, "Fim do cobre", "28 dias de cobre completos. Tire o cobre com TPAs e carvão ativado."),
                QStep(32, "Prazipro: início", "1 colher de chá (5 mL) a cada 80 L: ${numShort(Math.round(l / 80.0 * 5 * 10) / 10.0)} mL para ${numShort(l)} L. Mantenha por 5 a 7 dias."),
                QStep(38, "Fim do Prazipro", "Fim do tratamento. Observe comportamento e alimentação antes de levar ao aquário principal."),
            )
    },
    QProtocol(
        "cloroquina", "Cloroquina (hidroxicloroquina)", "Íctio, oodinium, uronema e brooklynella", "14 dias",
        listOf("Aquário sem mídia, rocha ou substrato colonizado (o biofilme degrada o remédio)", "Sulfato de hidroxicloroquina (ex.: Requinol, 400 mg por comprimido)", "Recipiente com um pouco de água RODI para dissolver"),
        listOf("Dose de 10 a 20 mg por litro. Não existe teste acessível para medir na água.", "Nas TPAs, reponha a dose na proporção da água nova.",
            "Alimente com moderação, de preferência no dia anterior às TPAs.", "Cuidado com peixe-leão e alguns bodiões (wrasses), que são sensíveis.", "Sem biologia no aquário: fique de olho na amônia."),
    ) { l ->
        val mg15 = (l * 15).roundToInt(); val cp = l * 15 / 400.0
        listOf(
            QStep(0, "Dose inicial", "Dissolva em água RODI à parte: ${(l * 10).roundToInt()} a ${(l * 20).roundToInt()} mg para ${numShort(l)} L. Com 15 mg/L dá $mg15 mg (${numShort(Math.round(cp * 10) / 10.0)} comprimido de 400 mg)."),
            QStep(5, "TPA + repor dose", "Faça a TPA e reponha 15 mg por litro de água nova."),
            QStep(10, "TPA + repor dose", "Faça a TPA e reponha 15 mg por litro de água nova."),
            QStep(14, "Fim do tratamento", "14 dias completos. Observe antes de levar ao aquário principal."),
        )
    },
    QProtocol(
        "hipo", "Hiposalinidade", "Só íctio (não pega oodinium)", "cerca de 27 dias (+7 de Prazipro, opcional)",
        listOf("Refratômetro", "Água de osmose (RO/DI) para baixar a salinidade", "Sal para subir no final", "Reposição automática sempre cheia"),
        listOf("Não serve para invertebrados: camarões, corais e equipe de limpeza não sobrevivem.", "Reposição cheia: a evaporação sobe a salinidade.", "Retire sobras de ração."),
    ) { l ->
        val troca1 = l * (1 - 0.016 / 0.025); val troca2 = l * (1 - 0.009 / 0.016)
        listOf(
            QStep(0, "Baixar para 1,016", "Troque ${numShort(Math.round(troca1 * 10) / 10.0)} L por água de osmose (de 1,025 para 1,016 em ${numShort(l)} L)."),
            QStep(1, "Baixar para 1,009", "Troque ${numShort(Math.round(troca2 * 10) / 10.0)} L por água de osmose (de 1,016 para 1,009). Mantenha 1,009 por 20 dias."),
        ) + (2..20).map { d -> QStep(d, "Conferir 1,009", "Meça a salinidade e mantenha 1,009. Dia ${d - 1} de 20.") } +
            listOf(
                QStep(21, "Subir para 1,012", "Comece a subir uns 3 pontos por dia."),
                QStep(22, "Subir para 1,015", "Mais 3 pontos."),
                QStep(23, "Subir para 1,018", "Mais 3 pontos."),
                QStep(24, "Subir para 1,021", "Mais 3 pontos."),
                QStep(25, "Subir para 1,025", "Salinidade normal."),
                QStep(26, "Prazipro (opcional)", "Uma semana de Prazipro: 1 colher de chá (5 mL) a cada 80 L."),
                QStep(33, "Fim", "Observe antes de levar ao aquário principal."),
            )
    },
    QProtocol(
        "ttm", "Troca de tanques (TTM)", "Íctio e oodinium (corta o ciclo do parasita)", "9 dias (+5 de Prazipro, opcional)",
        listOf("2 ou mais caixas, baldes ou aquários com aquecedor e oxigenação", "Água sanitária para desinfetar entre as trocas", "Teste de amônia"),
        listOf("A cada troca, deixe o tanque anterior e os equipamentos 24 h em 1 parte de água sanitária para 5 a 10 de água, lave, seque e deixe no sol.",
            "Sem biologia: teste amônia e alimente com cuidado.", "Use água nova nas mesmas salinidade e temperatura."),
    ) { _ ->
        listOf(
            QStep(0, "Tanque 1", "Peixe entra no primeiro tanque limpo.", false),
            QStep(3, "Troca 1", "Passe o peixe para um tanque limpo e desinfete o anterior."),
            QStep(6, "Troca 2", "Passe o peixe para um tanque limpo e desinfete o anterior."),
            QStep(9, "Troca 3", "Última troca. Daqui em diante, observação."),
            QStep(10, "Prazipro (opcional)", "5 dias de Prazipro: 1 colher de chá (5 mL) a cada 80 L."),
            QStep(15, "Fim", "Observe antes de levar ao aquário principal."),
        )
    },
)

private val TAG = Regex("#(\\w+):([^\\s#]+)")
fun HealthCase.qMeta(): Map<String, String> = TAG.findAll(observacao.orEmpty()).associate { it.groupValues[1] to it.groupValues[2] }
fun HealthCase.protocol(): QProtocol? = qMeta()["protocolo"]?.let { id -> PROTOCOLS.firstOrNull { it.id == id } }

/** Cartoes dos 4 metodos, no topo de Saude. */
@Composable
fun QuarentenaSection(nav: Nav) {
    SectionTitle("Quarentena")
    Panel(Modifier.fillMaxWidth(), padding = 0.dp) {
        PROTOCOLS.forEachIndexed { i, p ->
            if (i > 0) HorizontalDivider(color = Luluco.Divider)
            Row(Modifier.fillMaxWidth().clickable { nav.go(Screen.QuarantineStart(p.id)) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Luluco.AreaSaude))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.nome, fontWeight = FontWeight.Medium)
                    Muted("${p.contra} · ${p.duracao}")
                }
                Text("Começar", color = Luluco.PoolText)
            }
        }
    }
}

/** Escolher o metodo, ver o tutorial dia a dia e lancar tudo no calendario. */
@Composable
fun QuarantineStart(vm: MainViewModel, nav: Nav, protocolo: String) {
    val livestock by vm.livestock.collectAsStateWithLifecycle()
    var pid by remember { mutableStateOf(protocolo) }
    val p = PROTOCOLS.first { it.id == pid }
    var quem by remember { mutableStateOf("") }
    var litros by remember { mutableStateOf("40") }
    var inicio by remember { mutableStateOf(LocalDate.now(vm.zone)) }
    var hora by remember { mutableStateOf("20:00") }
    val l = litros.replace(',', '.').toDoubleOrNull()
    val passos = if (l != null && l > 0) p.passos(l) else emptyList()
    val f = DateTimeFormatter.ofPattern("EEE dd/MM", PtBr)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader("Nova quarentena", onBack = { nav.back() })
        ChipRow(PROTOCOLS.map { it.id to it.nome }, pid) { pid = it }
        Muted("${p.contra} · ${p.duracao}")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextInput("Quem (ex.: Palhaço novo)", quem, Modifier.weight(1f)) { quem = it }
            NumInput("Aquário de quarentena", litros, Modifier.width(220.dp), suffix = "L") { litros = it }
        }
        if (livestock.any { it.tipo == "peixe" }) ChipRow(livestock.filter { it.tipo == "peixe" }.map { it.nomePopular to it.nomePopular }, quem) { quem = it }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            DateTimeField("Começa em", inicio.atTime(LocalTime.parse(hora)).atZone(vm.zone).toInstant().toEpochMilli()) { ms ->
                val z = java.time.Instant.ofEpochMilli(ms).atZone(vm.zone); inicio = z.toLocalDate(); hora = "%02d:%02d".format(z.hour, z.minute)
            }
            Muted("Os lembretes ficam nesse horário.")
        }
        Panel(Modifier.fillMaxWidth()) {
            Eyebrow("Você vai precisar")
            p.equipamento.forEach { Text("· $it", fontSize = 14.sp) }
            Spacer(Modifier.height(8.dp))
            Eyebrow("Cuidados")
            p.cuidados.forEach { Text("· $it", fontSize = 14.sp) }
        }
        SectionTitle("Dia a dia")
        Panel(Modifier.fillMaxWidth(), padding = 0.dp) {
            compact(passos).forEachIndexed { i, (s, ate) ->
                if (i > 0) HorizontalDivider(color = Luluco.Divider)
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Column(Modifier.width(118.dp)) {
                        Text(if (ate != null) "Dias ${s.dia} a $ate" else "Dia ${s.dia}", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Muted(inicio.plusDays(s.dia.toLong()).format(f))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(s.titulo, fontWeight = FontWeight.Medium)
                        Muted(s.texto)
                    }
                }
            }
        }
        BigButton("Começar e lançar no calendário", Modifier.fillMaxWidth(), enabled = l != null && l > 0, color = Luluco.Pool) {
            val titulo = "Quarentena (${p.nome.substringBefore(" (")})" + (quem.trim().takeIf { it.isNotEmpty() }?.let { " · $it" } ?: "")
            val case = HealthCase(titulo = titulo, abertoEm = inicio.atTime(LocalTime.parse(hora)).atZone(vm.zone).toInstant().toEpochMilli(), tipo = "quarentena",
                observacao = "#protocolo:${p.id} #litros:${numShort(l!!)} #inicio:$inicio")
            vm.save(case)
            passos.filter { it.lembrete }.forEach { s ->
                val at = inicio.plusDays(s.dia.toLong()).atTime(LocalTime.parse(hora)).atZone(vm.zone).toInstant().toEpochMilli()
                vm.save(Routine(titulo = "Quarentena: ${s.titulo}", tipo = "outro", recorrencia = "unica", horario = hora, proximaEm = at, instrucao = s.texto + "\n[q:${case.id}]"))
            }
            nav.back(); nav.go(Screen.CaseDetail(case.id))
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Junta etapas repetidas em sequencia (ex.: "Medir cobre" dias 6 a 30) para o tutorial ficar curto. */
private fun compact(passos: List<QStep>): List<Pair<QStep, Int?>> {
    val out = ArrayList<Pair<QStep, Int?>>()
    var i = 0
    while (i < passos.size) {
        var j = i
        while (j + 1 < passos.size && passos[j + 1].titulo == passos[i].titulo && passos[j + 1].dia == passos[j].dia + 1) j++
        out += passos[i] to (if (j > i) passos[j].dia else null)
        i = j + 1
    }
    return out
}

/** No detalhe do caso de quarentena: o tutorial com as datas e o dia de hoje marcado. */
@Composable
fun QuarantinePlan(vm: MainViewModel, c: HealthCase) {
    val p = c.protocol() ?: return
    val meta = c.qMeta()
    val l = meta["litros"]?.replace(',', '.')?.toDoubleOrNull() ?: 40.0
    val inicio = meta["inicio"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: c.abertoEm.toLocalDate(vm.zone)
    val hoje = LocalDate.now(vm.zone)
    val diaAtual = java.time.temporal.ChronoUnit.DAYS.between(inicio, hoje).toInt()
    val f = DateTimeFormatter.ofPattern("EEE dd/MM", PtBr)
    Panel(Modifier.fillMaxWidth()) {
        Text("${p.nome} · ${numShort(l)} L", fontWeight = FontWeight.SemiBold)
        Muted(if (diaAtual >= 0) "Hoje é o dia $diaAtual." else "Começa em ${inicio.format(f)}.")
        Spacer(Modifier.height(6.dp))
        p.cuidados.forEach { Text("· $it", fontSize = 14.sp, color = Luluco.TextSoft) }
    }
    Panel(Modifier.fillMaxWidth(), padding = 0.dp) {
        compact(p.passos(l)).forEachIndexed { i, (s, ate) ->
            if (i > 0) HorizontalDivider(color = Luluco.Divider)
            val agora = diaAtual >= s.dia && diaAtual <= (ate ?: s.dia)
            val passou = diaAtual > (ate ?: s.dia)
            Row(Modifier.background(if (agora) Luluco.AreaSaude.copy(alpha = 0.08f) else androidx.compose.ui.graphics.Color.Transparent).padding(horizontal = 16.dp, vertical = 10.dp)) {
                Column(Modifier.width(118.dp)) {
                    Text(if (ate != null) "Dias ${s.dia} a $ate" else "Dia ${s.dia}", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = if (passou) Luluco.TextFaint else Luluco.Cream)
                    Muted(inicio.plusDays(s.dia.toLong()).format(f))
                }
                Column(Modifier.weight(1f)) {
                    Text(s.titulo + if (agora) "  ·  agora" else "", fontWeight = FontWeight.Medium, color = if (passou) Luluco.TextFaint else Luluco.Cream)
                    Muted(s.texto)
                }
            }
        }
    }
}
