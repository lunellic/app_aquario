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

/** Paleta da logo. Sem preto: tudo parte do azul-marinho da marca. */
object Luluco {
    val Royal = Color(0xFF0A47B5)
    val Pool = Color(0xFF12A5C0)
    val Coral = Color(0xFFF05A35)
    val Cream = Color(0xFFFBF1E4)

    // fundo em gradiente
    val DeepTop = Color(0xFF06225E)
    val DeepBottom = Color(0xFF0A2E73)
    val Navy = Color(0xFF06225E)

    // superficies
    val Card = Color(0x610A47B5) // azul royal translucido
    val CardBorder = Color(0x33FFFFFF)
    val Solid = Color(0xFF0C3A8E) // dialogos e campos (precisa ser opaco)
    val SolidHigh = Color(0xFF1250B8)

    // texto
    val TextSoft = Color(0xFFB9CBEA) // secundario, contraste > 7:1 sobre o fundo
    val CoralText = Color(0xFFFF9470) // laranja claro para texto (contraste > 4.5:1)
    val PoolText = Color(0xFF5AD0E6)

    val Ok = Color(0xFF3DDC97)
    val Critical = Color(0xFFFF6B6B)
    val Neutral = Color(0xFF9FB4D8)

    // nomes antigos usados em algumas telas
    val Abyss = Navy
    val Deep = Solid
    val DeepHigh = SolidHigh
}

/** Cor de texto com contraste garantido sobre um preenchimento: marinho em cores claras, creme nas escuras. */
fun onColor(bg: Color): Color = if (bg.luminance() > 0.22f) Luluco.Navy else Luluco.Cream

/** Fredoka (titulos e numeros grandes, combina com a logo) e Manrope (textos). Ambas OFL, embutidas. */
val Fredoka = FontFamily(Font(R.font.fredoka_semibold, FontWeight.SemiBold), Font(R.font.fredoka_semibold, FontWeight.Bold))
val Manrope = FontFamily(Font(R.font.manrope_regular, FontWeight.Normal), Font(R.font.manrope_bold, FontWeight.Bold))

private val OceanColors = darkColorScheme(
    // laranja claro: mesmo tom da marca, mas legivel como texto/contorno sobre o azul (contraste > 4.5:1)
    primary = Luluco.CoralText,
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
    outline = Color(0xFF7FA2E0),
    outlineVariant = Color(0xFF3F68B8),
    error = Luluco.Critical,
    onError = Luluco.Navy,
    scrim = Color(0xCC041A49),
)

private fun body(size: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(fontFamily = Manrope, fontWeight = weight, fontSize = size.sp, lineHeight = (size * 1.4).sp)
private fun title(size: Int) = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = size.sp, lineHeight = (size * 1.2).sp)

private val LulucoType = Typography(
    displayLarge = title(56), displayMedium = title(44), displaySmall = title(36),
    headlineLarge = title(32), headlineMedium = title(28), headlineSmall = title(24),
    titleLarge = title(22), titleMedium = title(18), titleSmall = title(16),
    bodyLarge = body(16), bodyMedium = body(15), bodySmall = body(13),
    labelLarge = body(15, FontWeight.Bold), labelMedium = body(13, FontWeight.Bold), labelSmall = body(12, FontWeight.Bold),
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
