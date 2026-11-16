package br.com.lunelli.lulucoreef.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.lunelli.lulucoreef.R
import br.com.lunelli.lulucoreef.data.Dosing
import br.com.lunelli.lulucoreef.data.Event
import br.com.lunelli.lulucoreef.data.Settings
import br.com.lunelli.lulucoreef.data.WaterChange
import br.com.lunelli.lulucoreef.data.WaterTest
import br.com.lunelli.lulucoreef.domain.Occurrence
import kotlinx.coroutines.delay
import java.time.Instant

sealed interface Screen {
    val topLevel: Boolean get() = false

    data object Painel : Screen { override val topLevel = true }
    data object Registrar : Screen { override val topLevel = true }
    data object Historico : Screen { override val topLevel = true }
    data object Aquario : Screen { override val topLevel = true }
    data object Rotinas : Screen { override val topLevel = true }
    data object Config : Screen { override val topLevel = true }
    data object Graficos : Screen { override val topLevel = true }
    data object Saude : Screen { override val topLevel = true }
    data object Equipamentos : Screen { override val topLevel = true }
    data object Mais : Screen { override val topLevel = true }

    data class TestForm(val edit: WaterTest? = null) : Screen
    data class TpaForm(val edit: WaterChange? = null, val occurrence: Occurrence? = null) : Screen
    data class DoseForm(val edit: Dosing? = null) : Screen
    data class EventForm(val edit: Event? = null) : Screen
    data class LivestockDetail(val id: String) : Screen
    data class LivestockForm(val id: String? = null, val tipo: String = "coral") : Screen
    data class CoralProtocol(val id: String) : Screen
    data class ProductForm(val id: String? = null) : Screen
    data class RoutineForm(val id: String? = null, val equipmentId: String? = null) : Screen
    data class CaseForm(val id: String? = null) : Screen
    data class CaseDetail(val id: String) : Screen
    data class EquipmentForm(val id: String? = null) : Screen
    data class RoutineHistory(val id: String) : Screen
}

class Nav {
    val stack = mutableStateListOf<Screen>(Screen.Painel)
    val current: Screen get() = stack.last()
    fun go(s: Screen) { stack.add(s) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun replace(s: Screen) { stack[stack.lastIndex] = s }
    fun top(s: Screen) { stack.clear(); stack.add(s) }
}

private val tabs = listOf(
    Screen.Painel to "Painel",
    Screen.Registrar to "Registrar",
    Screen.Historico to "Histórico",
    Screen.Aquario to "Meu Aquário",
    Screen.Graficos to "Gráficos",
    Screen.Saude to "Saúde",
    Screen.Rotinas to "Rotinas",
    Screen.Equipamentos to "Equipamentos",
    Screen.Config to "Ajustes",
)

/** No retrato (celular) a barra de baixo mostra so as 4 primeiras + "Mais". */
private val phoneTabs = tabs.take(4) + (Screen.Mais to "Mais")

@Composable
fun AppRoot(vm: MainViewModel = viewModel()) {
    val logged by vm.loggedIn.collectAsStateWithLifecycle()
    if (!logged) {
        LoginScreen(vm)
        return
    }
    val nav = remember { Nav() }
    val settings by vm.settings.collectAsStateWithLifecycle()
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            tick = System.currentTimeMillis()
        }
    }
    val idle = tick - lastTouch
    val hour = Instant.ofEpochMilli(tick).atZone(vm.zone).hour
    val topLevel = nav.current.topLevel
    // Modo noite: tela quase preta; um toque acorda o Painel por 1 minuto.
    val night = topLevel && Settings.isNight(hour, settings.nightStartHour, settings.nightEndHour) && idle > 60_000
    val vitrine = topLevel && !night && settings.vitrineMinutes > 0 && idle > settings.vitrineMinutes * 60_000L
    val overlay by rememberUpdatedState(night || vitrine)

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (overlay) nav.top(Screen.Painel)
                lastTouch = System.currentTimeMillis()
                tick = lastTouch
            }
        },
    ) {
        when {
            night -> NightScreen(vm)
            vitrine -> VitrineScreen(vm)
            else -> MainScaffold(vm, nav)
        }
    }
}

@Composable
private fun MainScaffold(vm: MainViewModel, nav: Nav) {
    BackHandler(enabled = nav.stack.size > 1) { nav.back() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier.width(132.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface).padding(8.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(painterResource(R.drawable.logo_luluco), "Luluco Reef", Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
                    tabs.forEach { (screen, label) -> TabItem(label, nav.stack.first() == screen, Modifier.fillMaxWidth()) { nav.top(screen) } }
                    SyncDot(vm)
                }
                Box(Modifier.weight(1f).fillMaxHeight().padding(16.dp)) { ScreenContent(vm, nav) }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp)) { ScreenContent(vm, nav) }
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    val root = nav.stack.first()
                    phoneTabs.forEach { (screen, label) ->
                        val selected = root == screen || (screen == Screen.Mais && phoneTabs.none { it.first == root })
                        TabItem(label.substringAfter("Meu "), selected, Modifier.weight(1f), small = true) { nav.top(screen) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabItem(label: String, selected: Boolean, modifier: Modifier, small: Boolean = false, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    Box(
        modifier.clip(RoundedCornerShape(12.dp)).background(bg).clickable(onClick = onClick).padding(vertical = if (small) 12.dp else 11.dp, horizontal = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = if (small) 12.sp else 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center, maxLines = 1,
            color = if (selected) Luluco.Cream else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SyncDot(vm: MainViewModel) {
    val s by vm.syncStatus.collectAsStateWithLifecycle()
    val text = when {
        s.running -> "sincronizando"
        s.pending > 0 -> "${s.pending} pendente${if (s.pending > 1) "s" else ""}"
        s.lastError != null -> "offline"
        else -> "sincronizado"
    }
    Text(text, fontSize = 11.sp, color = if (s.pending > 0 || s.lastError != null) Luluco.Coral else MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
}

@Composable
private fun ScreenContent(vm: MainViewModel, nav: Nav) {
    when (val s = nav.current) {
        Screen.Painel -> PainelScreen(vm, nav)
        Screen.Registrar -> RegistrarScreen(nav)
        Screen.Historico -> HistoricoScreen(vm, nav)
        Screen.Aquario -> AquarioScreen(vm, nav)
        Screen.Rotinas -> RotinasScreen(vm, nav)
        Screen.Config -> ConfigScreen(vm)
        Screen.Graficos -> GraficosScreen(vm)
        Screen.Saude -> SaudeScreen(vm, nav)
        Screen.Equipamentos -> EquipamentosScreen(vm, nav)
        Screen.Mais -> MaisScreen(nav)
        is Screen.TestForm -> TestForm(vm, nav, s.edit)
        is Screen.TpaForm -> TpaForm(vm, nav, s.edit, s.occurrence)
        is Screen.DoseForm -> DoseForm(vm, nav, s.edit)
        is Screen.EventForm -> EventForm(vm, nav, s.edit)
        is Screen.LivestockDetail -> LivestockDetail(vm, nav, s.id)
        is Screen.LivestockForm -> LivestockForm(vm, nav, s.id, s.tipo)
        is Screen.CoralProtocol -> CoralProtocol(vm, nav, s.id)
        is Screen.ProductForm -> ProductForm(vm, nav, s.id)
        is Screen.RoutineForm -> RoutineForm(vm, nav, s.id, s.equipmentId)
        is Screen.CaseForm -> CaseForm(vm, nav, s.id)
        is Screen.CaseDetail -> CaseDetail(vm, nav, s.id)
        is Screen.EquipmentForm -> EquipmentForm(vm, nav, s.id)
        is Screen.RoutineHistory -> RoutineHistory(vm, nav, s.id)
    }
}

@Composable
private fun MaisScreen(nav: Nav) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenHeader("Mais")
        tabs.drop(4).forEach { (screen, label) -> BigButton(label, Modifier.fillMaxWidth(), color = Luluco.Royal) { nav.top(screen) } }
    }
}

@Composable
fun LoginScreen(vm: MainViewModel) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 420.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(R.drawable.logo_luluco), "Luluco Reef", Modifier.size(150.dp).clip(RoundedCornerShape(24.dp)))
            OutlinedTextField(email, { email = it }, label = { Text("E-mail") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(password, { password = it }, label = { Text("Senha") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = Luluco.Coral) }
            BigButton(if (busy) "Entrando..." else "Entrar", Modifier.fillMaxWidth(), enabled = !busy && email.isNotBlank() && password.isNotEmpty()) {
                busy = true
                error = null
                vm.login(email, password) { err ->
                    busy = false
                    error = err
                }
            }
        }
    }
}
