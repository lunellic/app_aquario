package br.com.lunelli.lulucoreef.domain

import br.com.lunelli.lulucoreef.data.ParamRange
import br.com.lunelli.lulucoreef.data.WaterTest
import java.util.Locale
import kotlin.math.abs

enum class Status { IDEAL, ATENCAO, CRITICO, SEM_MEDICAO }

object Params {
    val MAIN = listOf("kh", "ca", "mg", "salinidade")
    val OPTIONAL = listOf("no3", "po4")
    val ALL = MAIN + OPTIONAL

    fun label(p: String) = when (p) {
        "kh" -> "KH"; "ca" -> "Cálcio"; "mg" -> "Magnésio"; "salinidade" -> "Salinidade"
        "temperatura" -> "Temperatura"; "no3" -> "NO3"; "po4" -> "PO4"; else -> p
    }

    fun short(p: String) = when (p) {
        "ca" -> "Ca"; "mg" -> "Mg"; "salinidade" -> "Sal."; else -> label(p)
    }

    fun unit(p: String) = when (p) {
        "kh" -> "dKH"; "salinidade" -> "SG"; "temperatura" -> "°C"; else -> "ppm"
    }

    fun decimals(p: String) = when (p) {
        "kh", "temperatura" -> 1; "salinidade" -> 3; "po4" -> 2; "no3" -> 1; else -> 0
    }

    fun kit(p: String) = when (p) {
        "kh", "ca", "mg" -> "Aquaforest"; else -> null
    }

    /** Valor fora do plausivel: o formulario pede confirmacao antes de salvar. */
    fun isAbsurd(p: String, v: Double) = when (p) {
        "kh" -> v < 3 || v > 16
        "ca" -> v < 250 || v > 600
        "mg" -> v < 900 || v > 1800
        "salinidade" -> v < 1.015 || v > 1.035
        "temperatura" -> v < 18 || v > 34
        "no3" -> v < 0 || v > 100
        "po4" -> v < 0 || v > 2
        else -> false
    }

    /** Casas padrao do parametro; uma casa a mais quando o valor registrado precisa dela (ex.: 1,0225 ou 7,85). */
    fun format(p: String, v: Double): String {
        val d = decimals(p)
        val scaled = v * Math.pow(10.0, d.toDouble())
        return num(v, if (abs(scaled - Math.rint(scaled)) > 1e-6) d + 1 else d)
    }
}

/** Numero em pt-BR com casas fixas. */
fun num(v: Double, decimals: Int): String = String.format(Locale("pt", "BR"), "%.${decimals}f", v)

/** Numero em pt-BR sem zeros a direita (ate 3 casas). */
fun numShort(v: Double): String {
    val s = String.format(Locale("pt", "BR"), "%.3f", v).trimEnd('0').trimEnd(',')
    return if (s.isEmpty() || s == "-") "0" else s
}

/** Aceita virgula ou ponto. */
fun parseNum(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

fun statusOf(v: Double, r: ParamRange): Status = when {
    v < r.alertaMin || v > r.alertaMax -> Status.CRITICO
    v < r.idealMin || v > r.idealMax -> Status.ATENCAO
    else -> Status.IDEAL
}

fun List<WaterTest>.latestOf(param: String): WaterTest? = filter { it.parametro == param }.maxByOrNull { it.medidoEm }

const val DAY_MS = 86_400_000L
const val HOUR_MS = 3_600_000L

/** Reta ajustada: valor(t) = intercept + slopePerDay * dias a partir de `now`. */
data class Trend(val slopePerDay: Double, val intercept: Double, val daysToExit: Double?)

/**
 * Projecao (spec 8): pelo menos 3 medicoes nos ultimos 30 dias, regressao linear por tempo real.
 * daysToExit so vem preenchido se a reta esta dentro da faixa ideal agora e sai dela em ate 14 dias.
 */
fun trendOf(tests: List<WaterTest>, param: String, range: ParamRange?, now: Long): Trend? {
    val pts = tests.filter { !it.baixaConfianca && it.parametro == param && it.medidoEm <= now && now - it.medidoEm <= 30 * DAY_MS }
    if (pts.size < 3) return null
    val xs = pts.map { (it.medidoEm - now).toDouble() / DAY_MS }
    val ys = pts.map { it.valor }
    val mx = xs.average()
    val my = ys.average()
    val sxx = xs.sumOf { (it - mx) * (it - mx) }
    if (sxx < 1e-6) return null // tudo no mesmo instante
    val slope = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / sxx
    val intercept = my - slope * mx
    var exit: Double? = null
    if (range != null && intercept >= range.idealMin && intercept <= range.idealMax && abs(slope) > 1e-9) {
        val d = if (slope > 0) (range.idealMax - intercept) / slope else (range.idealMin - intercept) / slope
        if (d > 0 && d <= 14) exit = d
    }
    return Trend(slope, intercept, exit)
}

/**
 * Salinidade digitada de forma abreviada: 25 -> 1,025; 1025 -> 1,025; 225 -> 1,0225; 10225 -> 1,0225.
 * Valores ja no formato 1,0xx ficam como estao.
 */
fun normalizeSalinity(v: Double): Double = when {
    v >= 10000 && v < 20000 -> v / 10000
    v >= 1000 && v < 2000 -> v / 1000
    v >= 100 && v < 1000 -> 1 + v / 10000
    v >= 10 && v < 100 -> 1 + v / 1000
    else -> v
}

/** Numero digitado para um parametro (a salinidade aceita a forma abreviada). */
fun parseParam(p: String, text: String): Double? = parseNum(text)?.let { if (p == "salinidade") normalizeSalinity(it) else it }
