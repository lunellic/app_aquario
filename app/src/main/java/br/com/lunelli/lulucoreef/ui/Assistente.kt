package br.com.lunelli.lulucoreef.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val TRIGGERS = mapOf("manual" to "Análise", "semanal" to "Análise semanal", "especie" to "Ficha de espécie", "saude" to "Saúde", "chat" to "Pergunta")

/** Assistente: "Analisar meu aquario", ultima analise salva e pergunta livre com o contexto do aquario. */
@Composable
fun AssistenteScreen(vm: MainViewModel) {
    val analyses by vm.analyses.collectAsStateWithLifecycle()
    val busy by vm.aiBusy.collectAsStateWithLifecycle()
    var question by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val analyzing = "analise" in busy
    val asking = "pergunta" in busy
    val lastAnalysis = analyses.firstOrNull { it.gatilho == "manual" || it.gatilho == "semanal" }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader("Assistente")
        Muted("A IA lê só o que está registrado no app (últimos 90 dias) e respeita o seu regime: TPAs + All-For-Reef + Shask. Precisa de internet. Limite de 60 chamadas por dia.")
        BigButton(if (analyzing) "Analisando..." else "Analisar meu aquário", Modifier.fillMaxWidth(), enabled = !analyzing) {
            error = null
            vm.analyze { _, e -> error = e }
        }
        error?.let { Panel(Modifier.fillMaxWidth(), borderColor = Luluco.Coral) { Text(it) } }

        SectionTitle("Última análise")
        Panel(Modifier.fillMaxWidth()) {
            if (lastAnalysis == null) Muted("Nenhuma análise ainda.")
            else {
                Muted("${TRIGGERS[lastAnalysis.gatilho]} · ${fmtDateTime(lastAnalysis.geradaEm)} · gerado por IA, confira")
                Spacer(Modifier.height(6.dp))
                Text(lastAnalysis.texto)
            }
        }

        SectionTitle("Pergunta livre")
        TextInput("Pergunte algo sobre o seu aquário", question, singleLine = false) { question = it }
        Button(enabled = !asking && question.isNotBlank(), onClick = {
            error = null
            vm.ask(question.trim()) { _, e -> error = e; if (e == null) question = "" }
        }) { Text(if (asking) "Perguntando..." else "Perguntar") }

        val others = analyses.filter { it.id != lastAnalysis?.id }.take(30)
        if (others.isNotEmpty()) {
            SectionTitle("Histórico")
            others.forEach { a ->
                Panel(Modifier.fillMaxWidth(), padding = 12.dp) {
                    Muted("${TRIGGERS[a.gatilho] ?: a.gatilho} · ${fmtDateTime(a.geradaEm)}")
                    a.pergunta?.let { Text(it, fontWeight = FontWeight.Bold) }
                    Text(a.texto)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
