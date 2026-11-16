package br.com.lunelli.lulucoreef.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Luluco {
    val Royal = Color(0xFF0A47B5)
    val Pool = Color(0xFF12A5C0)
    val Coral = Color(0xFFF05A35)
    val Cream = Color(0xFFFBF1E4)
    val Abyss = Color(0xFF061426)
    val Deep = Color(0xFF0B1F3A)
    val DeepHigh = Color(0xFF12305A)
    val Ok = Color(0xFF3DDC97)
    val Critical = Color(0xFFE5484D)
}

private val OceanColors = darkColorScheme(
    primary = Luluco.Pool,
    onPrimary = Luluco.Abyss,
    primaryContainer = Luluco.Royal,
    onPrimaryContainer = Luluco.Cream,
    secondary = Luluco.Royal,
    onSecondary = Luluco.Cream,
    tertiary = Luluco.Coral, // laranja reservado para atencao/alerta
    onTertiary = Luluco.Abyss,
    background = Luluco.Abyss,
    onBackground = Luluco.Cream,
    surface = Luluco.Deep,
    onSurface = Luluco.Cream,
    surfaceVariant = Luluco.DeepHigh,
    onSurfaceVariant = Color(0xFFB9C7DD),
    outline = Color(0xFF3A5580),
    error = Luluco.Critical,
    onError = Color.White,
)

@Composable
fun LulucoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = OceanColors, content = content)
}
