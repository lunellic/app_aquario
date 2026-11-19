package br.com.lunelli.lulucoreef.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.lunelli.lulucoreef.domain.Params
import kotlin.math.abs
import kotlin.math.roundToInt

/** Faixa e passo da roleta de cada parametro. */
fun wheelValues(p: String): List<Double> {
    val (lo, hi, step) = when (p) {
        "kh" -> Triple(0.0, 15.0, 0.05)
        "ca" -> Triple(0.0, 800.0, 5.0)
        "mg" -> Triple(0.0, 2000.0, 10.0)
        "salinidade" -> Triple(1.000, 1.035, 0.001)
        "no3" -> Triple(0.0, 50.0, 0.5)
        "po4" -> Triple(0.0, 1.0, 0.01)
        else -> Triple(0.0, 100.0, 1.0)
    }
    val n = ((hi - lo) / step).roundToInt()
    return (0..n).map { Math.round((lo + it * step) * 10000) / 10000.0 }
}

/** Roleta vertical (como a do iPhone): gira, encaixa no valor do meio. Sem teclado. */
@Composable
fun WheelPicker(items: List<String>, index: Int, onIndex: (Int) -> Unit, modifier: Modifier = Modifier, active: Boolean = true, onTouch: () -> Unit = {}) {
    val itemH = 40.dp
    val state = rememberLazyListState(initialFirstVisibleItemIndex = index.coerceIn(0, items.lastIndex))
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    val touch by rememberUpdatedState(onTouch)
    val pick by rememberUpdatedState(onIndex)
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collect { moving -> if (moving) touch() }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex to state.isScrollInProgress }.collect { (i, moving) -> if (!moving) pick(i.coerceIn(0, items.lastIndex)) }
    }
    LaunchedEffect(index) { if (!state.isScrollInProgress && state.firstVisibleItemIndex != index) state.scrollToItem(index.coerceIn(0, items.lastIndex)) }
    val shape = RoundedCornerShape(12.dp)
    Box(modifier.height(itemH * 3).clip(shape).background(Luluco.Solid)) {
        // faixa do meio
        Box(Modifier.align(Alignment.Center).fillMaxWidth().height(itemH).padding(horizontal = 6.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = if (active) 0.07f else 0.03f)))
        LazyColumn(state = state, flingBehavior = fling, modifier = Modifier.fillMaxSize()) {
            // um item vazio em cima e outro embaixo para o primeiro e o ultimo valor poderem ficar no meio
            item { Spacer(Modifier.height(itemH)) }
            items(items.size) { i ->
                val center = state.firstVisibleItemIndex
                val dist = abs(i - center)
                Box(Modifier.height(itemH).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(items[i], fontSize = if (dist == 0) 22.sp else 16.sp, fontWeight = if (dist == 0) FontWeight.SemiBold else FontWeight.Normal,
                        color = when { dist == 0 && active -> Luluco.Cream; dist == 0 -> Luluco.TextSoft; else -> Luluco.TextFaint })
                }
            }
            item { Spacer(Modifier.height(itemH)) }
        }
        // esmaecer em cima e embaixo
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(itemH).background(Brush.verticalGradient(listOf(Luluco.Solid, Luluco.Solid.copy(alpha = 0f)))))
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(itemH).background(Brush.verticalGradient(listOf(Luluco.Solid.copy(alpha = 0f), Luluco.Solid))))
    }
}

/** Parametro com roleta. Comeca no ultimo valor medido, apagado; girar marca como medido. */
@Composable
fun ParamWheel(p: String, value: String?, start: Double?, onChange: (String?) -> Unit, modifier: Modifier = Modifier) {
    val vals = remember(p) { wheelValues(p) }
    val labels = remember(p) { vals.map { Params.format(p, it) } }
    val startIdx = remember(p, start) { start?.let { s -> vals.indices.minByOrNull { abs(vals[it] - s) } } ?: (vals.size / 2) }
    var idx by remember(p) { mutableIntStateOf(value?.let { v -> labels.indexOf(v).takeIf { it >= 0 } } ?: startIdx) }
    val active = value != null
    Column(modifier.testTag("roleta_$p")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(Params.label(p) + " · " + Params.unit(p), Modifier.weight(1f), color = if (active) Luluco.PoolText else Luluco.TextSoft)
            if (active) Text("limpar", fontSize = 12.sp, color = Luluco.TextSoft, modifier = Modifier.clickable { onChange(null) }.padding(4.dp))
            else Text("gire para marcar", fontSize = 12.sp, color = Luluco.TextFaint)
        }
        Spacer(Modifier.height(6.dp))
        WheelPicker(labels, idx, { i -> idx = i; if (active) onChange(labels[i]) },
            Modifier.fillMaxWidth().then(if (active) Modifier.border(1.dp, Luluco.Pool.copy(alpha = 0.5f), RoundedCornerShape(12.dp)) else Modifier),
            active = active, onTouch = { if (!active) onChange(labels[idx]) })
    }
}
