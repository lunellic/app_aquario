package br.com.lunelli.lulucoreef.domain

import br.com.lunelli.lulucoreef.data.ParamRange
import br.com.lunelli.lulucoreef.data.WaterTest
import br.com.lunelli.lulucoreef.data.reliable

/** Quantas leituras cairam em cada zona, no periodo. */
data class Faixas(val ideal: Int, val atencao: Int, val critico: Int) {
    val total: Int get() = ideal + atencao + critico
    fun pct(n: Int): Int = if (total == 0) 0 else Math.round(n * 100f / total)
}

/**
 * Leituras por faixa, no espirito do "Time in ranges" dos monitores continuos de glicose.
 *
 * Com uma diferenca que importa e que muda o nome da coisa: um monitor continuo le de 5 em 5
 * minutos e pode dizer "19h41 por dia no alvo". Aqui a medicao e de dois em dois dias. Dizer
 * "tempo em faixa" obrigaria a inventar o que aconteceu entre uma medicao e a seguinte, e o app
 * passaria a afirmar coisa que nao mediu.
 *
 * Entao a conta e de LEITURAS, e a tela diz isso: "8 de 12 leituras". So leitura confiavel entra,
 * pelo mesmo motivo de sempre.
 */
fun faixasDe(tests: List<WaterTest>, param: String, range: ParamRange?, from: Long, to: Long): Faixas {
    if (range == null) return Faixas(0, 0, 0)
    var ideal = 0
    var atencao = 0
    var critico = 0
    tests.reliable().filter { it.parametro == param && it.medidoEm in from..to }.forEach {
        when (statusOf(it.valor, range)) {
            Status.IDEAL -> ideal++
            Status.ATENCAO -> atencao++
            Status.CRITICO -> critico++
            Status.SEM_MEDICAO -> {}
        }
    }
    return Faixas(ideal, atencao, critico)
}
