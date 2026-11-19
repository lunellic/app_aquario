package br.com.lunelli.lulucoreef.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import br.com.lunelli.lulucoreef.R

/**
 * Tema v0.4: escuro neutro e sobrio (grafite azulado), cor so com funcao.
 * Os nomes antigos foram mantidos para nao mexer em todas as telas; os valores mudaram.
 */
object Luluco {
    // um unico destaque, contido (verde-agua frio); o resto e cinza
    val Pool = Color(0xFF2CC5A8)
    val PoolText = Color(0xFF5ED6BF)
    val Royal = Color(0xFF4C8DF6)
    // estado
    val Coral = Color(0xFFE8734A)
    val CoralText = Color(0xFFF0A267)
    val Ok = Color(0xFF34C77B)
    val Critical = Color(0xFFE5534B)
    val Neutral = Color(0xFF6B7684)
    // categorias (usadas com parcimonia)
    val Violet = Color(0xFF8B7CF6)
    val Blue = Color(0xFF4C8DF6)
    val Amber = Color(0xFFE5A23C)
    val Pink = Color(0xFFE06C9F)
    val Cyan = Color(0xFF38BDF8)
    // fundo e superficies: preto azulado e um degrau acima
    val DeepTop = Color(0xFF0B0E13)
    val DeepBottom = Color(0xFF0B0E13)
    val Navy = Color(0xFF0B0E13)
    val Card = Color(0xFF12161C)
    val CardBorder = Color(0xFF1E242C)
    val Solid = Color(0xFF161B22)
    val SolidHigh = Color(0xFF1C222A)
    val Divider = Color(0xFF1E242C)
    // cor de cada area (menu e jornal)
    val AreaPainel = Color(0xFF2CC5A8)
    val AreaTeste = Color(0xFF4C8DF6)
    val AreaTpa = Color(0xFF38BDF8)
    val AreaDose = Color(0xFFE5A23C)
    val AreaJornal = Color(0xFFE06C9F)
    val AreaAnimal = Color(0xFF34C77B)
    val AreaSaude = Color(0xFFE5534B)
    val AreaEquip = Color(0xFF94A3B8)
    val AreaGrafico = Color(0xFF8B7CF6)
    val AreaRelatorio = Color(0xFF6E7FF3)
    val AreaCalc = Color(0xFFEAB308)
    val AreaFinancas = Color(0xFF10B981)
    val AreaAjustes = Color(0xFF8B95A3)
    // faixas de parametro: ideal, fora do ideal (ok/atencao) e critico
    val ZoneIdeal = Color(0xFF2F8F5B)
    val ZoneWarn = Color(0xFFC0602A)
    val ZoneCrit = Color(0xFFB3261E)
    // texto
    val Cream = Color(0xFFECEFF3)
    val TextSoft = Color(0xFF8B95A3)
    val TextFaint = Color(0xFF5B6573)
    val Abyss = Navy
    val Deep = Solid
    val DeepHigh = SolidHigh
}

/** Texto com contraste garantido sobre um preenchimento. */
fun onColor(bg: Color): Color = if (bg.luminance() > 0.22f) Luluco.Navy else Luluco.Cream

/** Fundo em tom da cor (quadradinho de icone, selo, chip). */
fun tint(c: Color, a: Float = 0.16f): Color = c.copy(alpha = a)

/** SF Pro Display (arquivos enviados pelo Lucas): so os pesos do meio para cima. Light cai no Regular. */
val Jakarta = FontFamily(
    Font(R.font.sf_regular, FontWeight.Light),
    Font(R.font.sf_regular, FontWeight.Normal),
    Font(R.font.sf_medium, FontWeight.Medium),
    Font(R.font.sf_bold, FontWeight.SemiBold),
    Font(R.font.sf_bold, FontWeight.Bold),
)
val Display = Jakarta
val Manrope = Jakarta

private val OceanColors = darkColorScheme(
    primary = Luluco.PoolText,
    onPrimary = Luluco.Navy,
    primaryContainer = Luluco.Royal,
    onPrimaryContainer = Luluco.Cream,
    secondary = Luluco.Pool,
    onSecondary = Luluco.Navy,
    secondaryContainer = Luluco.Royal,
    onSecondaryContainer = Luluco.Cream,
    tertiary = Luluco.Pool,
    onTertiary = Luluco.Navy,
    background = Luluco.DeepTop,
    onBackground = Luluco.Cream,
    surface = Luluco.Solid,
    onSurface = Luluco.Cream,
    surfaceVariant = Luluco.SolidHigh,
    onSurfaceVariant = Luluco.TextSoft,
    surfaceContainer = Luluco.Solid,
    surfaceContainerLow = Luluco.Solid,
    surfaceContainerHigh = Luluco.Solid,
    surfaceContainerHighest = Luluco.SolidHigh,
    outline = Color(0xFF2C3846),
    outlineVariant = Color(0xFF1F2833),
    error = Luluco.Critical,
    onError = Luluco.Navy,
    scrim = Color(0xCC05080C),
)

private fun body(size: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(fontFamily = Jakarta, fontWeight = weight, fontSize = size.sp, lineHeight = (size * 1.4).sp, letterSpacing = (-0.1).sp, fontFeatureSettings = "tnum")
private fun title(size: Int) = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = size.sp, lineHeight = (size * 1.2).sp, letterSpacing = (-0.4).sp, fontFeatureSettings = "tnum")

private val LulucoType = Typography(
    displayLarge = title(56), displayMedium = title(44), displaySmall = title(36),
    headlineLarge = title(32), headlineMedium = title(28), headlineSmall = title(24),
    titleLarge = title(22), titleMedium = title(18), titleSmall = title(16),
    bodyLarge = body(16), bodyMedium = body(15), bodySmall = body(13),
    labelLarge = body(15, FontWeight.SemiBold), labelMedium = body(13, FontWeight.Bold), labelSmall = body(12, FontWeight.Bold),
)

@Composable
fun LulucoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = OceanColors, typography = LulucoType) {
        // Texto sem cor explicita sai sempre claro (creme) e em Manrope: nunca texto escuro sobre fundo escuro.
        CompositionLocalProvider(LocalContentColor provides Luluco.Cream) {
            ProvideTextStyle(LulucoType.bodyLarge, content)
        }
    }
}
