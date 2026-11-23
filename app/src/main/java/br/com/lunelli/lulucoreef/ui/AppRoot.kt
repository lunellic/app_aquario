package br.com.lunelli.lulucoreef.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Create
import androidx.compose.material.icons.rounded.ShoppingCart
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
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
    data object Financas : Screen { override val topLevel = true }
    data class Calendario(val dia: String? = null) : Screen { override val topLevel get() = dia == null }
    data object Calculadoras : Screen { override val topLevel = true }
    data object Timers : Screen { override val topLevel = true }
    data class AlarmForm(val id: String? = null) : Screen
    data class QuarantineStart(val protocolo: String) : Screen
    data class FinanceForm(val id: String? = null, val tipo: String? = null) : Screen
    data class ShoppingForm(val id: String? = null) : Screen
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
    data class RoutineForm(val id: String? = null, val equipmentId: String? = null, val onceDate: String? = null, val productId: String? = null) : Screen
    data class CaseForm(val id: String? = null) : Screen
    data class CaseDetail(val id: String) : Screen
    data class EquipmentForm(val id: String? = null) : Screen
    data class RoutineHistory(val id: String) : Screen
}

/** O que a tela esta mostrando: o app, o descanso ou o modo noite. */
private enum class Modo { APP, DESCANSO, NOITE }

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

private class TabGroup(val title: String?, val tabs: List<Tab>)

/** Menu em grupos curtos para caber sem rolar. */
private val groups = listOf(
    TabGroup(null, listOf(
        Tab(Screen.Painel, "Painel", Icons.Outlined.Home, Luluco.AreaPainel),
        Tab(Screen.Registrar, "Registrar", Icons.Outlined.AddCircle, Luluco.AreaTeste),
        Tab(Screen.Calendario(), "Calendário", Icons.Outlined.DateRange, Luluco.AreaTpa),
        Tab(Screen.Rotinas, "Rotinas", Icons.Outlined.Notifications, Luluco.AreaDose),
        Tab(Screen.Historico, "Jornal", Icons.Outlined.List, Luluco.AreaJornal),
        Tab(Screen.Timers, "Timer e alarmes", Icons.Outlined.Refresh, Luluco.AreaCalc),
    )),
    TabGroup("Aquário", listOf(
        Tab(Screen.Aquario, "Meu Aquário", Icons.Outlined.Star, Luluco.AreaAnimal),
        Tab(Screen.Saude, "Saúde e quarentena", Icons.Outlined.FavoriteBorder, Luluco.AreaSaude),
        Tab(Screen.Equipamentos, "Equipamentos e produtos", Icons.Outlined.Build, Luluco.AreaEquip),
    )),
    TabGroup("Análise e contas", listOf(
        Tab(Screen.Graficos, "Gráficos", Icons.Outlined.Info, Luluco.AreaGrafico),
        Tab(Screen.Relatorios, "Relatórios", Icons.Outlined.Email, Luluco.AreaRelatorio),
        Tab(Screen.Calculadoras, "Calculadoras", Icons.Outlined.Create, Luluco.AreaCalc),
        Tab(Screen.Financas, "Finanças", Icons.Outlined.ShoppingCart, Luluco.AreaFinancas),
    )),
)
private val tabs = groups.flatMap { it.tabs } + Tab(Screen.Config, "Ajustes", Icons.Outlined.Settings, Luluco.AreaAjustes)

@Composable
fun AppRoot(vm: MainViewModel = viewModel()) {
    val logged by vm.loggedIn.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalWaterFx provides settings.waterFx, LocalAnim provides settings.uiAnim) {
        Box(Modifier.fillMaxSize()) {
            if (logged) LoggedIn(vm) else {
                WaterBackground(Modifier.fillMaxSize())
                LoginScreen(vm)
            }
            Splash()
            // timer ou alarme tocando: cobre a tela com o botao de parar, onde ele estiver
            if (logged) RingingOverlay()
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
        // testTag para o teste de fumaca esperar a abertura sair antes de tirar o print:
        // a tela de baixo ja esta montada enquanto a logo ainda cobre tudo.
        Box(Modifier.fillMaxSize().testTag("splash").background(Luluco.DeepTop), contentAlignment = Alignment.Center) {
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

    // Brilho num unico lugar: o Fire no automatico deixa a tela apagada e o tema e grafite, entao o
    // painel ao lado do aquario parecia escuro. O modo noite continua baixando para quase nada.
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    DisposableEffect(activity, settings.brilho, night) {
        setBrightness(
            activity,
            when {
                night -> 0.12f
                settings.brilho <= 0 -> android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                else -> (settings.brilho / 100f).coerceIn(0.05f, 1f)
            },
        )
        onDispose { }
    }

    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                wake()
            }
        },
    ) {
        // Entrada e saida do descanso em transicao suave, nao um corte seco.
        //
        // Esta fica sempre ligada, mesmo com "Animacoes da interface" desligada em Ajustes: a troca
        // entre telas do menu acontece a cada toque e por isso custa caro, mas esta acontece uma vez
        // a cada tantos minutos, e sem ela o tablet ao lado do aquario pisca de uma tela para a outra.
        Crossfade(
            targetState = when {
                night -> Modo.NOITE
                vitrine -> Modo.DESCANSO
                else -> Modo.APP
            },
            animationSpec = tween(700),
            label = "descanso",
        ) { modo ->
            when (modo) {
                Modo.NOITE -> NightScreen(vm)
                Modo.DESCANSO -> VitrineScreen(vm)
                Modo.APP -> {
                    WaterBackground(Modifier.fillMaxSize())
                    MainScaffold(vm, nav)
                }
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
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val sink = remember { androidx.compose.ui.focus.FocusRequester() }
    // tira o foco dos campos sem deixar o sistema escolher outro campo (no Fire isso abria o teclado sozinho)
    val parkFocus = { keyboard?.hide(); runCatching { sink.requestFocus() }; Unit }
    DisposableEffect(nav, focus) {
        nav.onLeave = { parkFocus() }
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
    CompositionLocalProvider(LocalOpenMenu provides { parkFocus(); menuOpen = true }) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().focusRequester(sink).focusable().padding(horizontal = 18.dp, vertical = 14.dp)) { ScreenContent(vm, nav) }
            Drawer(vm, menuOpen, nav.stack.first(), onPick = { parkFocus(); menuOpen = false; nav.top(it) }, onClose = { parkFocus(); menuOpen = false })
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
            Modifier.width(290.dp).fillMaxHeight().clip(RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)).background(Color(0xFF07090C))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(14.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                Image(painterResource(R.drawable.logo_mark), "Luluco Reef", Modifier.height(38.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Luluco Reef", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Luluco.Cream)
                    SyncDot(vm)
                }
                // Ajustes vira um icone no topo para o menu caber sem rolar
                Box(Modifier.testTag("menu_Ajustes").clip(RoundedCornerShape(12.dp)).clickable { onPick(Screen.Config) }) {
                    GlowIcon(Icons.Outlined.Settings, Luluco.AreaAjustes, 26.dp, strong = current == Screen.Config)
                }
            }
            groups.forEach { g ->
                if (g.title != null) Eyebrow(g.title, Modifier.padding(start = 12.dp, top = 6.dp), color = Luluco.TextFaint)
                g.tabs.forEach { t ->
                    val selected = current == t.screen
                    Row(
                        Modifier.fillMaxWidth().testTag("menu_${t.label}").clip(RoundedCornerShape(12.dp))
                            .background(if (selected) Luluco.SolidHigh else Color.Transparent).clickable { onPick(t.screen) }.padding(horizontal = 8.dp, vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GlowIcon(t.icon, t.color, 26.dp, strong = selected)
                        Spacer(Modifier.width(12.dp))
                        Text(t.label, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = if (selected) Luluco.Cream else Luluco.TextSoft, maxLines = 1)
                    }
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
    // Com as animacoes da interface desligadas a troca e seca: nao compoe as duas telas ao mesmo
    // tempo, que era o que fazia o menu parecer travado no Fire.
    if (!LocalAnim.current) {
        ScreenFor(vm, nav, nav.current)
        return
    }
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
        Screen.Registrar -> RegistrarScreen(vm, nav)
        Screen.Historico -> HistoricoScreen(vm, nav)
        Screen.Aquario -> AquarioScreen(vm, nav)
        Screen.Rotinas -> RotinasScreen(vm, nav)
        Screen.Config -> ConfigScreen(vm)
        Screen.Graficos -> GraficosScreen(vm)
        Screen.Saude -> SaudeScreen(vm, nav)
        Screen.Equipamentos -> EquipamentosScreen(vm, nav)
        Screen.Financas -> FinancasScreen(vm, nav)
        is Screen.Calendario -> CalendarioScreen(vm, nav, s.dia)
        Screen.Calculadoras -> CalculadorasScreen(vm)
        Screen.Timers -> TimersScreen(vm, nav)
        is Screen.AlarmForm -> AlarmForm(vm, nav, s.id)
        is Screen.QuarantineStart -> QuarantineStart(vm, nav, s.protocolo)
        is Screen.FinanceForm -> FinanceForm(vm, nav, s.id, s.tipo)
        is Screen.ShoppingForm -> ShoppingForm(vm, nav, s.id)
        Screen.Assistente -> PainelScreen(vm, nav)
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
        is Screen.RoutineForm -> RoutineForm(vm, nav, s.id, s.equipmentId, s.onceDate, s.productId)
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
