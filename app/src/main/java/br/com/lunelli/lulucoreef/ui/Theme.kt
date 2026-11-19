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
    // destaque principal (acoes, selecao, progresso)
    val Pool = Color(0xFF19C3A3)      // verde-agua
    val PoolText = Color(0xFF34D8B8)
    val Royal = Color(0xFF2F6FDB)     // azul (acao secundaria e inicio do degrade)
    // atencao e alerta
    val Coral = Color(0xFFF2643D)     // coral para borda/selo de "atrasado"
    val CoralText = Color(0xFFFBBF24) // ambar legivel para texto de atencao
    val Ok = Color(0xFF22C55E)
    val Critical = Color(0xFFEF4444)
    val Neutral = Color(0xFF64748B)

    // categorias (icones em quadradinhos coloridos)
    val Violet = Color(0xFF8B5CF6)
    val Blue = Color(0xFF3B82F6)
    val Amber = Color(0xFFF59E0B)
    val Pink = Color(0xFFEC4899)
    val Cyan = Color(0xFF06B6D4)

    // fundo e superficies
    val DeepTop = Color(0xFF0A0E13)
    val DeepBottom = Color(0xFF0C1118)
    val Navy = Color(0xFF0A0E13)
    val Card = Color(0xFF11171F)
    val CardBorder = Color(0xFF1F2833)
    val Solid = Color(0xFF151C26)
    val SolidHigh = Color(0xFF1C2531)

    // texto
    val Cream = Color(0xFFEEF2F6)
    val TextSoft = Color(0xFF8C99AA)

    val Abyss = Navy
    val Deep = Solid
    val DeepHigh = SolidHigh
}

/** Texto com contraste garantido sobre um preenchimento. */
fun onColor(bg: Color): Color = if (bg.luminance() > 0.22f) Luluco.Navy else Luluco.Cream

/** Fundo em tom da cor (quadradinho de icone, selo, chip). */
fun tint(c: Color, a: Float = 0.16f): Color = c.copy(alpha = a)

/** Plus Jakarta Sans (licenca OFL, embutida): geometrica e moderna. Display = pesos fortes para numeros e titulos. */
val Jakarta = FontFamily(
    Font(R.font.jakarta_light, FontWeight.Light),
    Font(R.font.jakarta_regular, FontWeight.Normal),
    Font(R.font.jakarta_medium, FontWeight.Medium),
    Font(R.font.jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.jakarta_bold, FontWeight.Bold),
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

private fun body(size: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(fontFamily = Jakarta, fontWeight = weight, fontSize = size.sp, lineHeight = (size * 1.4).sp)
private fun title(size: Int) = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = size.sp, lineHeight = (size * 1.2).sp, letterSpacing = (-0.2).sp)

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
