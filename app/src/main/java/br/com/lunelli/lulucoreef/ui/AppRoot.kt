package br.com.lunelli.lulucoreef.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.platform.LocalFocusManager
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
    data object Assistente : Screen { override val topLevel = true }
    data object Relatorios : Screen { override val topLevel = true }

    data class TestForm(val edit: WaterTest? = null, val only: String? = null) : Screen
    data class HistoricoDe(val filtro: String) : Screen
    data class TpaForm(val edit: WaterChange? = null, val occurrence: Occurrence? = null) : Screen
    data class DoseForm(val edit: Dosing? = null) : Screen
    data class EventForm(val edit: Event? = null) : Screen
    data class LivestockDetail(val id: String) : Screen
    data class LivestockForm(val id: String? = null, val tipo: String = "coral") : Screen
    data class CoralProtocol(val id: String) : Screen
    data class ProductForm(val id: String? = null) : Screen
    data class RoutineForm(val id: String? = null, val equipmentId: String? = null, val onceDate: String? = null) : Screen
    data class CaseForm(val id: String? = null) : Screen
    data class CaseDetail(val id: String) : Screen
    data class EquipmentForm(val id: String? = null) : Screen
    data class RoutineHistory(val id: String) : Screen
}

class Nav {
    val stack = mutableStateListOf<Screen>(Screen.Painel)
    val current: Screen get() = stack.last()

    /** Chamado antes de trocar de tela: solta o foco para o teclado fechar. */
    var onLeave: () -> Unit = {}
    fun go(s: Screen) { onLeave(); stack.add(s) }
    fun back() { if (stack.size > 1) { onLeave(); stack.removeAt(stack.lastIndex) } }
    fun replace(s: Screen) { onLeave(); stack[stack.lastIndex] = s }
    fun top(s: Screen) { onLeave(); stack.clear(); stack.add(s) }
}

private class Tab(val screen: Screen, val label: String, val icon: ImageVector, val color: androidx.compose.ui.graphics.Color = Luluco.Pool)

private val tabs = listOf(
    Tab(Screen.Painel, "Painel", Icons.Rounded.Home, Luluco.Pool),
    Tab(Screen.Registrar, "Registrar", Icons.Rounded.AddCircle, Luluco.Ok),
    Tab(Screen.Historico, "Histórico", Icons.Rounded.List, Luluco.Blue),
    Tab(Screen.Aquario, "Meu Aquário", Icons.Rounded.Star, Luluco.Cyan),
    Tab(Screen.Graficos, "Gráficos", Icons.Rounded.Info, Luluco.Violet),
    Tab(Screen.Relatorios, "Relatórios", Icons.Rounded.Email, Luluco.Blue),
    Tab(Screen.Saude, "Saúde", Icons.Rounded.Favorite, Luluco.Critical),
    Tab(Screen.Rotinas, "Rotinas", Icons.Rounded.DateRange, Luluco.Amber),
    Tab(Screen.Equipamentos, "Equipamentos", Icons.Rounded.Build, Luluco.Neutral),
    Tab(Screen.Assistente, "Assistente", Icons.Rounded.Face, Luluco.Pink),
    Tab(Screen.Config, "Ajustes", Icons.Rounded.Settings, Luluco.Neutral),
)

@Composable
fun AppRoot(vm: MainViewModel = viewModel()) {
    val logged by vm.loggedIn.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalWaterFx provides settings.waterFx) {
        Box(Modifier.fillMaxSize()) {
            if (logged) LoggedIn(vm) else {
                WaterBackground(Modifier.fillMaxSize())
                LoginScreen(vm)
            }
            Splash()
        }
    }
}

/** Abertura: a logo entra com uma mola sobre o fundo de agua e some. Nao bloqueia toques. */
@Composable
private fun Splash() {
    var visible by remember { mutableStateOf(true) }
    val scale = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        delay(500)
        visible = false
    }
    AnimatedVisibility(visible, enter = fadeIn(tween(0)), exit = fadeOut(tween(450))) {
        Box(Modifier.fillMaxSize().background(Luluco.DeepTop), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.logo_luluco), "Luluco Reef",
                Modifier.size(260.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value }.clip(RoundedCornerShape(48.dp)))
        }
    }
}

@Composable
private fun LoggedIn(vm: MainViewModel) {
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
    fun wake() {
        if (overlay) nav.top(Screen.Painel)
        lastTouch = System.currentTimeMillis()
        tick = lastTouch
    }

    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                wake()
            }
        },
    ) {
        when {
            night -> NightScreen(vm)
            vitrine -> VitrineScreen(vm)
            else -> {
                WaterBackground(Modifier.fillMaxSize())
                MainScaffold(vm, nav)
            }
        }
        // na vitrine e no modo noite, o botao voltar so acorda o Painel
        BackHandler(enabled = night || vitrine) { wake() }
    }
}

@Composable
private fun MainScaffold(vm: MainViewModel, nav: Nav) {
    var menuOpen by remember { mutableStateOf(false) }
    var lastBack by remember { mutableLongStateOf(0L) }
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    DisposableEffect(nav, focus) {
        nav.onLeave = { focus.clearFocus(force = true) }
        onDispose { nav.onLeave = {} }
    }
    // Voltar: fecha o menu, volta a tela, vai ao Painel; no Painel so sai com dois toques em ate 2 s (modo quiosque).
    BackHandler {
        when {
            menuOpen -> menuOpen = false
            nav.stack.size > 1 -> nav.back()
            nav.current != Screen.Painel -> nav.top(Screen.Painel)
            System.currentTimeMillis() - lastBack < 2_000 -> (ctx as? android.app.Activity)?.finish()
            else -> {
                lastBack = System.currentTimeMillis()
                Toast.makeText(ctx, "Toque em voltar de novo para sair", Toast.LENGTH_SHORT).show()
            }
        }
    }
    CompositionLocalProvider(LocalOpenMenu provides { focus.clearFocus(force = true); menuOpen = true }) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp)) { ScreenContent(vm, nav) }
            Drawer(vm, menuOpen, nav.stack.first(), onPick = { menuOpen = false; nav.top(it) }, onClose = { menuOpen = false })
        }
    }
}

/** Menu lateral: fechado por padrao, abre pelo icone de menu e fecha ao escolher. */
@Composable
private fun Drawer(vm: MainViewModel, open: Boolean, current: Screen, onPick: (Screen) -> Unit, onClose: () -> Unit) {
    AnimatedVisibility(open, enter = fadeIn(tween(180)), exit = fadeOut(tween(180))) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose))
    }
    AnimatedVisibility(open, enter = slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { -it }, exit = slideOutHorizontally(tween(200)) { -it }) {
        Column(
            Modifier.width(280.dp).fillMaxHeight().clip(RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)).background(Luluco.Solid)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(14.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Image(painterResource(R.drawable.logo_luluco), "Luluco Reef", Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Luluco Reef", style = MaterialTheme.typography.titleLarge)
                    SyncDot(vm)
                }
            }
            tabs.forEach { t ->
                val selected = current == t.screen
                Row(
                    Modifier.fillMaxWidth().testTag("menu_${t.label}").clip(RoundedCornerShape(18.dp))
                        .background(if (selected) Luluco.Royal else Color.Transparent).clickable { onPick(t.screen) }.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconTile(t.icon, t.color, 34.dp)
                    Spacer(Modifier.width(14.dp))
                    Text(t.label, fontSize = 17.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = Luluco.Cream)
                }
            }
        }
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
    Text(text, fontSize = 12.sp, color = if (s.pending > 0 || s.lastError != null) Luluco.CoralText else Luluco.TextSoft)
}

@Composable
private fun ScreenContent(vm: MainViewModel, nav: Nav) {
    // transicao fluida entre telas: fade + leve deslize para cima
    AnimatedContent(
        targetState = nav.current,
        transitionSpec = { (fadeIn(tween(240)) + slideInVertically(tween(300)) { it / 28 }) togetherWith fadeOut(tween(110)) },
        label = "tela",
    ) { s -> ScreenFor(vm, nav, s) }
}

@Composable
private fun ScreenFor(vm: MainViewModel, nav: Nav, s: Screen) {
    when (s) {
        Screen.Painel -> PainelScreen(vm, nav)
        Screen.Registrar -> RegistrarScreen(nav)
        Screen.Historico -> HistoricoScreen(vm, nav)
        Screen.Aquario -> AquarioScreen(vm, nav)
        Screen.Rotinas -> RotinasScreen(vm, nav)
        Screen.Config -> ConfigScreen(vm)
        Screen.Graficos -> GraficosScreen(vm)
        Screen.Saude -> SaudeScreen(vm, nav)
        Screen.Equipamentos -> EquipamentosScreen(vm, nav)
        Screen.Assistente -> AssistenteScreen(vm)
        Screen.Relatorios -> RelatoriosScreen(vm)
        is Screen.TestForm -> TestForm(vm, nav, s.edit, s.only)
        is Screen.HistoricoDe -> HistoricoScreen(vm, nav, s.filtro)
        is Screen.TpaForm -> TpaForm(vm, nav, s.edit, s.occurrence)
        is Screen.DoseForm -> DoseForm(vm, nav, s.edit)
        is Screen.EventForm -> EventForm(vm, nav, s.edit)
        is Screen.LivestockDetail -> LivestockDetail(vm, nav, s.id)
        is Screen.LivestockForm -> LivestockForm(vm, nav, s.id, s.tipo)
        is Screen.CoralProtocol -> CoralProtocol(vm, nav, s.id)
        is Screen.ProductForm -> ProductForm(vm, nav, s.id)
        is Screen.RoutineForm -> RoutineForm(vm, nav, s.id, s.equipmentId, s.onceDate)
        is Screen.CaseForm -> CaseForm(vm, nav, s.id)
        is Screen.CaseDetail -> CaseDetail(vm, nav, s.id)
        is Screen.EquipmentForm -> EquipmentForm(vm, nav, s.id)
        is Screen.RoutineHistory -> RoutineHistory(vm, nav, s.id)
    }
}

@Composable
fun LoginScreen(vm: MainViewModel) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 420.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(R.drawable.logo_luluco), "Luluco Reef", Modifier.size(150.dp).clip(RoundedCornerShape(24.dp)))
            OutlinedTextField(email, { email = it }, label = { Text("E-mail") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(password, { password = it }, label = { Text("Senha") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = Luluco.CoralText) }
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
