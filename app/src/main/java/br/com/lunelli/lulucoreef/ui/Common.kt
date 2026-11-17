package br.com.lunelli.lulucoreef.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.lunelli.lulucoreef.App
import br.com.lunelli.lulucoreef.domain.DAY_MS
import br.com.lunelli.lulucoreef.domain.HOUR_MS
import br.com.lunelli.lulucoreef.domain.Status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val PtBr = Locale("pt", "BR")
private val dateTimeFmt = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm", PtBr)
private val dayFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy", PtBr)
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", PtBr)

fun fmtDateTime(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(dateTimeFmt)
fun fmtTime(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(timeFmt)
fun fmtDay(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(dayFmt)
fun fmtIsoDay(iso: String?): String = try { LocalDate.parse(iso).format(dayFmt) } catch (_: Exception) { "" }

fun fmtAgo(ms: Long, now: Long): String {
    val d = now - ms
    return when {
        d < 0 -> fmtDateTime(ms)
        d < HOUR_MS -> "há ${maxOf(1, d / 60_000)} min"
        d < DAY_MS -> "há ${d / HOUR_MS} h"
        d < 60 * DAY_MS -> "há ${d / DAY_MS} d"
        else -> fmtDay(ms)
    }
}

fun statusColor(s: Status): Color = when (s) {
    Status.IDEAL -> Luluco.Ok
    Status.ATENCAO -> Luluco.CoralText
    Status.CRITICO -> Luluco.Critical
    Status.SEM_MEDICAO -> Luluco.Neutral
}

fun statusLabel(s: Status) = when (s) {
    Status.IDEAL -> "Ideal"; Status.ATENCAO -> "Atenção"; Status.CRITICO -> "Crítico"; Status.SEM_MEDICAO -> "Sem medição"
}

/** Card de agua: azul translucido, cantos bem arredondados e borda sutil. */
@Composable
fun Panel(modifier: Modifier = Modifier, borderColor: Color? = null, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier.clip(shape).background(Luluco.Card).border(if (borderColor != null) 2.dp else 1.dp, borderColor ?: Luluco.CardBorder, shape).padding(padding),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

/** Botao grande para mao molhada. Laranja por padrao; texto escolhido pelo contraste. */
@Composable
fun BigButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, color: Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 60.dp).pressSpring(source),
        shape = RoundedCornerShape(20.dp), interactionSource = source,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = onColor(color),
            disabledContainerColor = Luluco.SolidHigh.copy(alpha = 0.6f), disabledContentColor = Luluco.TextSoft),
    ) { Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2) }
}

@Composable
fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bg = if (selected) Luluco.Pool else Luluco.Card
    val fg = if (selected) Luluco.Navy else Luluco.Cream
    Box(
        modifier.clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = fg, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipRow(options: List<Pair<String, String>>, selected: String?, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) -> Chip(label, value == selected) { onSelect(value) } }
    }
}

@Composable
fun Badge(text: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(color).padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(text, color = onColor(color), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun TextInput(label: String, value: String, modifier: Modifier = Modifier, singleLine: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = singleLine, modifier = modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )
}

@Composable
fun NumInput(label: String, value: String, modifier: Modifier = Modifier, suffix: String? = null, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, { v -> onChange(v.filter { it.isDigit() || it == ',' || it == '.' }) },
        label = { Text(label) }, singleLine = true, modifier = modifier,
        suffix = if (suffix != null) ({ Text(suffix) }) else null,
        textStyle = MaterialTheme.typography.headlineSmall,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

/** Data e hora editaveis com os dialogos nativos (leves no Fire). */
@Composable
fun DateTimeField(label: String, ms: Long, modifier: Modifier = Modifier, onChange: (Long) -> Unit) {
    val ctx = LocalContext.current
    val zone = ZoneId.systemDefault()
    val zdt = Instant.ofEpochMilli(ms).atZone(zone)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = {
            DatePickerDialog(ctx, { _, y, m, d -> onChange(zdt.withYear(y).withMonth(m + 1).withDayOfMonth(d).toInstant().toEpochMilli()) },
                zdt.year, zdt.monthValue - 1, zdt.dayOfMonth).show()
        }) { Text(fmtDay(ms)) }
        OutlinedButton(onClick = {
            TimePickerDialog(ctx, { _, h, min -> onChange(zdt.withHour(h).withMinute(min).withSecond(0).toInstant().toEpochMilli()) },
                zdt.hour, zdt.minute, true).show()
        }) { Text(fmtTime(ms)) }
    }
}

/** Data opcional (colunas date, yyyy-MM-dd). */
@Composable
fun DateField(label: String, iso: String?, modifier: Modifier = Modifier, onChange: (String?) -> Unit) {
    val ctx = LocalContext.current
    val current = try { LocalDate.parse(iso) } catch (_: Exception) { null }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = {
            val base = current ?: LocalDate.now()
            DatePickerDialog(ctx, { _, y, m, d -> onChange(LocalDate.of(y, m + 1, d).toString()) }, base.year, base.monthValue - 1, base.dayOfMonth).show()
        }) { Text(if (current != null) fmtIsoDay(iso) else "definir") }
        if (current != null) TextButton(onClick = { onChange(null) }) { Text("limpar") }
    }
}

@Composable
fun PickerField(label: String, options: List<Pair<String, String>>, selected: String?, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { open = true }) { Text(options.firstOrNull { it.first == selected }?.second ?: "escolher") }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Fechar") } },
            title = { Text(label) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    options.forEach { (value, text) ->
                        Text(
                            text, fontWeight = if (value == selected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.fillMaxWidth().clickable { onSelect(value); open = false }.padding(vertical = 14.dp),
                        )
                    }
                }
            },
        )
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Abre o menu lateral (fornecido pelo AppRoot). */
val LocalOpenMenu = compositionLocalOf<() -> Unit> { {} }

@Composable
fun MenuButton() {
    val open = LocalOpenMenu.current
    IconButton(onClick = open, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(Luluco.Card)) {
        Icon(Icons.Rounded.Menu, contentDescription = "Menu", tint = Luluco.Cream)
    }
}

/** Cabecalho de tela: voltar nas telas internas, menu nas principais. */
@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            OutlinedButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Voltar")
            }
        } else {
            MenuButton()
        }
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

/** Foto do bucket privado (arquivo local ou baixada sob demanda). */
@Composable
fun Photo(path: String?, modifier: Modifier = Modifier, maxPx: Int = 640, contentScale: ContentScale = ContentScale.Crop) {
    val photos = (LocalContext.current.applicationContext as App).graph.photos
    val bmp by produceState<ImageBitmap?>(null, path, maxPx) {
        value = if (path == null) null else withContext(Dispatchers.IO) {
            try { photos.load(path, maxPx)?.asImageBitmap() } catch (_: Exception) { null }
        }
    }
    val b = bmp
    if (b != null) Image(b, contentDescription = null, modifier = modifier, contentScale = contentScale)
    else Box(modifier.background(Luluco.Card))
}
