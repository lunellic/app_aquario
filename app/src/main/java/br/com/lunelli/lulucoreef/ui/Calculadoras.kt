package br.com.lunelli.lulucoreef.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lunelli.lulucoreef.domain.BUFFERS
import br.com.lunelli.lulucoreef.domain.Params
import br.com.lunelli.lulucoreef.domain.normalizeSalinity
import br.com.lunelli.lulucoreef.domain.numShort
import kotlin.math.roundToInt

private fun num(s: String): Double? = s.replace(',', '.').trim().toDoubleOrNull()
private fun sal(s: String): Double? = num(s)?.let { normalizeSalinity(it) }

/** Mistura de sal: preparar agua nova e corrigir um balde ja pronto. Linear em (densidade - 1). */
@Composable
fun CalculadorasScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var gpl by remember { mutableStateOf(numShort(settings.salGramasPorLitro.toDouble())) }
    var litros by remember { mutableStateOf("15") }
    var alvo by remember { mutableStateOf("1,025") }
    var balde by remember { mutableStateOf("15") }
    var medido by remember { mutableStateOf("") }
    var alvo2 by remember { mutableStateOf("1,025") }
    val g = num(gpl)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader("Calculadoras")
        SectionTitle("Mistura de sal")
        Panel(Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumInput("Seu sal para 1,025", gpl, Modifier.weight(1f), suffix = "g/L") { v ->
                    gpl = v; num(v)?.let { n -> if (n in 20.0..50.0) vm.updateSettings { it.copy(salGramasPorLitro = n.toFloat()) } }
                }
                Spacer(Modifier.weight(1f))
            }
            Muted("Fica salvo. Para o seu sal, 35 a 39 g por litro de água de osmose.")
        }

        Panel(Modifier.fillMaxWidth()) {
            Eyebrow("Preparar água nova")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumInput("Água de osmose", litros, Modifier.weight(1f), suffix = "L") { litros = it }
                NumInput("Salinidade alvo", alvo, Modifier.weight(1f)) { alvo = it }
            }
            val l = num(litros); val t = sal(alvo)
            if (g != null && l != null && t != null && t > 1.0) {
                val gramas = l * g * (t - 1) / 0.025
                Spacer(Modifier.height(10.dp))
                Result("${gramas.roundToInt()} g de sal", "${numShort(l)} L a ${numShort(g)} g/L para ${"%.3f".format(t).replace('.', ',')}")
            }
        }

        Panel(Modifier.fillMaxWidth()) {
            Eyebrow("Corrigir balde pronto")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumInput("Volume no balde", balde, Modifier.weight(1f), suffix = "L") { balde = it }
                NumInput("Mediu", medido, Modifier.weight(1f)) { medido = it }
                NumInput("Quer", alvo2, Modifier.weight(1f)) { alvo2 = it }
            }
            Muted("Pode digitar só o final: 22 vira 1,022.")
            val l = num(balde); val m = sal(medido); val t = sal(alvo2)
            if (g != null && l != null && m != null && t != null && m > 1.0 && t > 1.0) {
                Spacer(Modifier.height(10.dp))
                when {
                    kotlin.math.abs(m - t) < 0.0005 -> Result("Já está no ponto", "Diferença menor que 0,0005.")
                    m < t -> Result("Adicione ${(l * g * (t - m) / 0.025).roundToInt()} g de sal", "Misture bem e meça de novo depois de uns 30 min.")
                    else -> Result("Adicione ${numShort(l * ((m - 1) / (t - 1) - 1))} L de água de osmose", "O balde vai ficar com ${numShort(l * (m - 1) / (t - 1))} L.")
                }
            }
        }

        SectionTitle("Correção de KH, Ca e Mg")
        Correcao(vm)
        Spacer(Modifier.height(12.dp))
    }
}

/** Correcao de KH, Ca e Mg: produto, valor atual, alvo e volume real; mostra a dose total e como dividir pelos dias. */
@Composable
private fun Correcao(vm: MainViewModel) {
    val tests by vm.tests.collectAsStateWithLifecycle()
    var param by remember { mutableStateOf("mg") }
    val opts = BUFFERS.filter { it.param == param }
    var prodId by remember(param) { mutableStateOf(opts.firstOrNull { it.marca == "Aquaforest" }?.id ?: opts.first().id) }
    val prod = opts.firstOrNull { it.id == prodId } ?: opts.first()
    val ultimo = remember(tests, param) { tests.filter { it.parametro == param && !it.baixaConfianca }.maxByOrNull { it.medidoEm }?.valor }
    var atual by remember(param, ultimo) { mutableStateOf(ultimo?.let { Params.format(param, it) } ?: "") }
    var alvo by remember(param) { mutableStateOf(when (param) { "kh" -> "8"; "ca" -> "430"; else -> "1350" }) }
    var litros by remember { mutableStateOf(numShort(vm.volume)) }

    Panel(Modifier.fillMaxWidth()) {
        ChipRow(listOf("mg" to "Magnésio", "ca" to "Cálcio", "kh" to "KH"), param) { param = it }
        Spacer(Modifier.height(8.dp))
        ChipRow(opts.map { it.id to "${it.marca} · ${it.nome}" }, prodId) { prodId = it }
        Spacer(Modifier.height(8.dp))
        Muted("Rótulo: ${numShort(prod.quantidade)} ${prod.unidade} em ${numShort(prod.litros)} L sobe ${Params.format(param, prod.sobe)} ${Params.unit(param)}" +
            (prod.maxDia?.let { " · máximo ${Params.format(param, it)} ${Params.unit(param)} por dia" } ?: ""))
        prod.nota?.let { Muted(it) }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumInput("Valor atual", atual, Modifier.weight(1f), suffix = Params.unit(param)) { atual = it }
            NumInput("Alvo", alvo, Modifier.weight(1f), suffix = Params.unit(param)) { alvo = it }
            NumInput("Litragem real", litros, Modifier.weight(1f), suffix = "L") { litros = it }
        }
        Muted("Use a litragem real: desconte rochas, areia e equipamentos.")
        val a = num(atual); val t = num(alvo); val v = num(litros)
        if (a != null && t != null && v != null && v > 0) {
            Spacer(Modifier.height(10.dp))
            val delta = t - a
            if (delta <= 0) Result("Já está no alvo ou acima", "Não precisa corrigir. Para baixar, só com TPA ou esperando o consumo.")
            else {
                val total = prod.doseFor(delta, v)
                val dias = prod.maxDia?.let { kotlin.math.ceil(delta / it).toInt().coerceAtLeast(1) } ?: 1
                Result("${fmtQty(total)} ${prod.unidade} no total", "Para subir ${Params.format(param, delta)} ${Params.unit(param)} em ${numShort(v)} L.")
                if (dias > 1) Muted("Divida em $dias dias: ${fmtQty(total / dias)} ${prod.unidade} por dia (sobe ~${Params.format(param, delta / dias)} ${Params.unit(param)} por dia).")
                Muted("Ordem: magnésio primeiro, depois cálcio e por último KH. Nunca misture no mesmo copo e espere 15 a 30 min entre um e outro, num ponto de boa circulação.")
            }
        }
    }
}

private fun fmtQty(x: Double): String = if (x >= 10) x.roundToInt().toString() else numShort(Math.round(x * 10) / 10.0)

@Composable
private fun Result(title: String, sub: String) {
    Text(title, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, letterSpacing = (-0.6).sp, color = Luluco.PoolText)
    Muted(sub)
}
