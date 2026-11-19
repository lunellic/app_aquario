package br.com.lunelli.lulucoreef.domain

/**
 * Buffers e suplementos de KH, Ca e Mg (dados do relatorio enviado pelo Lucas, a partir dos rotulos dos fabricantes).
 * [quantidade] [unidade] em [litros] L sobe [sobe] (dKH para KH, ppm para Ca e Mg). [maxDia] e o teto diario de subida.
 */
data class Buffer(
    val marca: String, val nome: String, val param: String, val quantidade: Double, val unidade: String,
    val litros: Double, val sobe: Double, val maxDia: Double?, val nota: String? = null,
) {
    val id get() = "$marca $nome"
    /** Quanto do produto para subir [delta] num volume de [volume] litros. */
    fun doseFor(delta: Double, volume: Double): Double = delta / sobe * quantidade * (volume / litros)
}

val BUFFERS = listOf(
    // KH (meq/L convertido para dKH: x 2,8)
    Buffer("Seachem", "Reef Carbonate", "kh", 5.0, "ml", 80.0, 0.7, 2.8),
    Buffer("Seachem", "Reef Builder", "kh", 3.0, "g", 150.0, 0.7, 2.8),
    Buffer("Seachem", "Marine Buffer", "kh", 5.0, "g", 80.0, 2.8, null, "Também leva o pH a 8,3. Não passe de pH 8,3."),
    Buffer("Red Sea", "Foundation B (líquido)", "kh", 1.0, "ml", 100.0, 0.1, 1.4),
    Buffer("Red Sea", "Foundation B (pó)", "kh", 1.0, "g", 100.0, 0.33, 1.4),
    Buffer("ReeFlowers", "kH Blend", "kh", 10.0, "ml", 100.0, 1.0, 1.0),
    Buffer("ReeFlowers", "Pure kH (A)", "kh", 2.5, "g", 100.0, 1.0, 1.0),
    Buffer("Aquaforest", "KH Plus", "kh", 10.0, "ml", 100.0, 0.5, 0.5),
    Buffer("Aquaforest", "KH Buffer", "kh", 10.0, "g", 100.0, 3.3, 0.5),
    // Calcio (ppm)
    Buffer("Seachem", "Reef Complete", "ca", 5.0, "ml", 80.0, 10.0, 25.0),
    Buffer("Seachem", "Reef Advantage Calcium", "ca", 5.0, "g", 150.0, 12.0, 24.0),
    Buffer("Red Sea", "Foundation A (líquido)", "ca", 1.0, "ml", 100.0, 2.0, 20.0),
    Buffer("Red Sea", "Foundation A (pó)", "ca", 1.0, "g", 100.0, 1.82, 20.0),
    Buffer("ReeFlowers", "Pure Calcium (B)", "ca", 1.0, "g", 100.0, 3.0, 30.0),
    Buffer("Aquaforest", "Calcium", "ca", 10.0, "g", 100.0, 35.5, 20.0),
    // Magnesio (ppm)
    Buffer("Seachem", "Reef Advantage Magnesium", "mg", 5.0, "g", 80.0, 5.0, 25.0, "Dissolva em pelo menos 250 mL de água doce."),
    Buffer("Red Sea", "Foundation C (líquido)", "mg", 1.0, "ml", 100.0, 1.0, 10.0),
    Buffer("Red Sea", "Foundation C (pó)", "mg", 1.0, "g", 100.0, 1.67, 10.0),
    Buffer("ReeFlowers", "Pure Magnesium (C), solução 400 g/L", "mg", 10.0, "ml", 100.0, 8.5, 17.0),
    Buffer("Aquaforest", "Magnesium", "mg", 10.0, "g", 100.0, 12.0, 50.0),
)
