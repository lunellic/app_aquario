package br.com.lunelli.lulucoreef.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.lunelli.lulucoreef.App
import br.com.lunelli.lulucoreef.data.ApiException
import br.com.lunelli.lulucoreef.data.Dosing
import br.com.lunelli.lulucoreef.data.Equipment
import br.com.lunelli.lulucoreef.data.Event
import br.com.lunelli.lulucoreef.data.HealthCase
import br.com.lunelli.lulucoreef.data.HealthEntry
import br.com.lunelli.lulucoreef.data.Livestock
import br.com.lunelli.lulucoreef.data.ParamRange
import br.com.lunelli.lulucoreef.data.Product
import br.com.lunelli.lulucoreef.data.Routine
import br.com.lunelli.lulucoreef.data.RoutineLog
import br.com.lunelli.lulucoreef.data.SyncDao
import br.com.lunelli.lulucoreef.data.SyncWorker
import br.com.lunelli.lulucoreef.data.Syncable
import br.com.lunelli.lulucoreef.data.TankConfig
import br.com.lunelli.lulucoreef.data.WaterChange
import br.com.lunelli.lulucoreef.data.WaterTest
import br.com.lunelli.lulucoreef.domain.Occurrence
import br.com.lunelli.lulucoreef.domain.doseFor
import br.com.lunelli.lulucoreef.domain.isInterval
import br.com.lunelli.lulucoreef.domain.nextAnchor
import br.com.lunelli.lulucoreef.reminders.Reminders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.ZoneId

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val g = (app as App).graph
    private val db = g.db
    val zone: ZoneId get() = ZoneId.systemDefault()

    val loggedIn = g.session.loggedIn
    val email: String? get() = g.session.email
    val settings = g.settings.state
    val syncStatus = g.sync.status

    private fun <T> Flow<List<T>>.state(): StateFlow<List<T>> = stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val tank: StateFlow<TankConfig?> = db.tankConfig().all().map { it.firstOrNull() }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val ranges: StateFlow<Map<String, ParamRange>> = db.paramRanges().all().map { l -> l.associateBy { it.parametro } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val tests = db.waterTests().all().state()
    val changes = db.waterChanges().all().state()
    val products = db.products().all().state()
    val dosings = db.dosings().all().state()
    val routines = db.routines().all().state()
    val logs = db.routineLogs().all().state()
    val livestock = db.livestock().all().state()
    val events = db.events().all().state()
    val equipment = db.equipment().all().state()
    val cases = db.healthCases().all().state()
    val entries = db.healthEntries().all().state()

    /** Relogio da UI (status, atrasos, modo noite). */
    val now = MutableStateFlow(System.currentTimeMillis())

    init {
        viewModelScope.launch {
            while (true) {
                now.value = System.currentTimeMillis()
                delay(15_000)
            }
        }
        if (loggedIn.value) syncNow()
    }

    val volume: Double get() = tank.value?.volumeDosagemL ?: 100.0

    // ---- conta ----

    fun login(email: String, password: String, onResult: (String?) -> Unit) = viewModelScope.launch {
        val error = withContext(Dispatchers.IO) {
            try {
                g.api.login(email, password); null
            } catch (e: ApiException) {
                if (e.code == 400) "E-mail ou senha incorretos." else "Erro ${e.code}: ${e.message}"
            } catch (e: IOException) {
                "Sem conexão com o servidor: ${e.message}"
            }
        }
        if (error == null) {
            g.sync.sync()
            Reminders.schedule(getApplication())
        }
        onResult(error)
    }

    fun logout() = viewModelScope.launch {
        withContext(Dispatchers.IO) { db.clearAllTables() }
        g.sync.reset()
        g.session.clear()
        Reminders.schedule(getApplication())
    }

    fun syncNow() = viewModelScope.launch {
        g.sync.sync()
        Reminders.schedule(getApplication())
    }

    fun updateSettings(change: (br.com.lunelli.lulucoreef.data.LocalSettings) -> br.com.lunelli.lulucoreef.data.LocalSettings) = g.settings.update(change)

    // ---- gravacao: salva no Room como pendente e pede sincronizacao ----

    private fun <T : Syncable> persist(dao: SyncDao<T>, item: T) = viewModelScope.launch {
        dao.upsert(item)
        afterWrite()
    }

    private suspend fun afterWrite() {
        g.sync.refreshPendingCount()
        SyncWorker.kick(getApplication())
        Reminders.schedule(getApplication())
    }

    private fun ts() = System.currentTimeMillis()

    fun save(x: TankConfig) = persist(db.tankConfig(), x.copy(updatedAt = ts(), pending = true))
    fun save(x: ParamRange) = persist(db.paramRanges(), x.copy(updatedAt = ts(), pending = true))
    fun save(x: WaterTest) = persist(db.waterTests(), x.copy(updatedAt = ts(), pending = true))
    fun save(x: WaterChange) = persist(db.waterChanges(), x.copy(updatedAt = ts(), pending = true))
    fun save(x: Product) = persist(db.products(), x.copy(updatedAt = ts(), pending = true))
    fun save(x: Dosing) = persist(db.dosings(), x.copy(updatedAt = ts(), pending = true))
    fun save(x: Routine) = persist(db.routines(), x.copy(updatedAt = ts(), pending = true))
    fun save(x: RoutineLog) = persist(db.routineLogs(), x.copy(updatedAt = ts(), pending = true))
    fun save(x: Livestock) = persist(db.livestock(), x.copy(updatedAt = ts(), pending = true))
    fun save(x: Event) = persist(db.events(), x.copy(updatedAt = ts(), pending = true))
    fun save(x: Equipment) = persist(db.equipment(), x.copy(updatedAt = ts(), pending = true))
    fun save(x: HealthCase) = persist(db.healthCases(), x.copy(updatedAt = ts(), pending = true))
    fun save(x: HealthEntry) = persist(db.healthEntries(), x.copy(updatedAt = ts(), pending = true))

    // exclusao logica
    fun delete(x: WaterTest) = save(x.copy(deletedAt = ts()))
    fun delete(x: WaterChange) = save(x.copy(deletedAt = ts()))
    fun delete(x: Dosing) = save(x.copy(deletedAt = ts()))
    fun delete(x: Event) = save(x.copy(deletedAt = ts()))
    fun delete(x: Product) = save(x.copy(deletedAt = ts()))
    fun delete(x: Routine) = save(x.copy(deletedAt = ts()))
    fun delete(x: Livestock) = save(x.copy(deletedAt = ts()))
    fun delete(x: Equipment) = save(x.copy(deletedAt = ts()))
    fun delete(x: HealthCase) = save(x.copy(deletedAt = ts()))
    fun delete(x: HealthEntry) = save(x.copy(deletedAt = ts()))

    // ---- rotinas ----

    private fun log(o: Occurrence, status: String, feitoEm: Long?) {
        val base = o.log ?: RoutineLog(routineId = o.routine.id, previstoPara = o.dueAt, status = status)
        save(base.copy(status = status, feitoEm = feitoEm))
        if (o.routine.isInterval() && status != "adiado") {
            save(o.routine.copy(proximaEm = nextAnchor(o.routine, done = status == "feito", now = ts(), zone = zone)))
        }
    }

    /** Feito. Rotina de dosagem com produto tambem gera o registro em dosings, com a dose para o volume atual. */
    fun markDone(o: Occurrence, createDosing: Boolean = true) {
        val at = ts()
        log(o, "feito", at)
        val p = o.routine.productId?.let { id -> products.value.firstOrNull { it.id == id } }
        if (createDosing && o.routine.tipo == "dosagem" && p != null) {
            save(Dosing(dosadoEm = at, productId = p.id, quantidade = doseFor(p, volume), unidade = p.doseUnidade, observacao = "Rotina: ${o.routine.titulo}"))
        }
    }

    fun snooze(o: Occurrence) = log(o, "adiado", null)
    fun skip(o: Occurrence) = log(o, "pulado", null)

    // ---- fotos ----

    fun importPhoto(uri: Uri, onPath: (String?) -> Unit) = viewModelScope.launch {
        val path = withContext(Dispatchers.IO) { g.photos.importImage(uri) }
        onPath(path)
        if (path != null) afterWrite()
    }
}
