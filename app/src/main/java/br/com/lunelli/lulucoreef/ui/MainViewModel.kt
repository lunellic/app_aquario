package br.com.lunelli.lulucoreef.ui

import br.com.lunelli.lulucoreef.data.FinanceEntry
import br.com.lunelli.lulucoreef.data.ShoppingItem

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
    val finance = db.finance().all().state()
    val shopping = db.shopping().all().state()
    val cases = db.healthCases().all().state()
    val entries = db.healthEntries().all().state()
    val speciesInfo = db.speciesInfo().all().state()
    val analyses = db.aiAnalyses().all().state()

    /** Chamadas de IA em andamento, por chave (ex.: "analise", "especie:<id>"). */
    val aiBusy = MutableStateFlow<Set<String>>(emptySet())

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
    fun save(x: FinanceEntry) = persist(db.finance(), x.copy(updatedAt = ts(), pending = true))
    fun save(x: ShoppingItem) = persist(db.shopping(), x.copy(updatedAt = ts(), pending = true))
    fun delete(x: FinanceEntry) = save(x.copy(deletedAt = ts()))
    fun delete(x: ShoppingItem) = save(x.copy(deletedAt = ts()))

    /** Compra parcelada no cartao: uma entrada por fatura, todas com o mesmo grupo. */
    fun saveInstallments(base: FinanceEntry, n: Int) {
        val grupo = br.com.lunelli.lulucoreef.data.newId()
        val parte = Math.round(base.valor / n * 100) / 100.0
        val start = java.time.Instant.ofEpochMilli(base.lancadoEm).atZone(zone)
        for (i in 0 until n) {
            val v = if (i == n - 1) Math.round((base.valor - parte * (n - 1)) * 100) / 100.0 else parte
            save(base.copy(id = br.com.lunelli.lulucoreef.data.newId(), valor = v, parcela = i + 1, parcelas = n, grupo = grupo, lancadoEm = start.plusMonths(i.toLong()).toInstant().toEpochMilli()))
        }
    }

    /** Marca o item como comprado e lanca a despesa; desmarcar apaga a despesa ligada a ele. */
    fun buy(item: ShoppingItem, valor: Double, quando: Long) {
        save(item.copy(compradoEm = quando, valorPago = valor))
        save(FinanceEntry(lancadoEm = quando, tipo = "despesa", categoria = item.categoria, descricao = item.nome, valor = valor, aquario = item.aquario, shoppingItemId = item.id))
    }
    fun unbuy(item: ShoppingItem) {
        save(item.copy(compradoEm = null, valorPago = null))
        finance.value.filter { it.shoppingItemId == item.id }.forEach { delete(it) }
    }
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

    /** Desfaz um "feito" ou "pulado": apaga o registro, a dose gerada por ele e devolve a data da rotina. */
    fun undo(o: Occurrence) {
        val l = o.log ?: return
        save(l.copy(deletedAt = ts()))
        if (l.status == "feito" && o.routine.tipo == "dosagem") {
            dosings.value.firstOrNull { it.productId == o.routine.productId && it.dosadoEm == l.feitoEm && it.observacao == "Rotina: ${o.routine.titulo}" }?.let { delete(it) }
        }
        if (o.routine.isInterval()) save(o.routine.copy(proximaEm = o.dueAt))
    }

    /** Corrige a hora real de um "feito" (e da dose gerada por ele). */
    fun changeDoneTime(o: Occurrence, at: Long) {
        val l = o.log ?: return
        if (l.status != "feito" || l.feitoEm == at) return
        if (o.routine.tipo == "dosagem") {
            dosings.value.firstOrNull { it.productId == o.routine.productId && it.dosadoEm == l.feitoEm && it.observacao == "Rotina: ${o.routine.titulo}" }?.let { save(it.copy(dosadoEm = at)) }
        }
        save(l.copy(feitoEm = at))
    }

    /** Quarentena encerrada: apaga os lembretes que ainda nao aconteceram. */
    fun dropQuarantineReminders(caseId: String) {
        val now = ts()
        routines.value.filter { it.instrucao?.contains("[q:$caseId]") == true && (it.proximaEm ?: 0) > now }.forEach { delete(it) }
    }

    fun snooze(o: Occurrence) = log(o, "adiado", null)
    fun skip(o: Occurrence) = log(o, "pulado", null)

    // ---- nuvem: IA e avisos (Edge Functions) ----

    /**
     * Sincroniza (a funcao le os dados do servidor), chama a Edge Function e puxa o que ela gravou.
     * onResult recebe (resposta, erro).
     */
    private fun callCloud(function: String, key: String, body: org.json.JSONObject, onResult: (org.json.JSONObject?, String?) -> Unit) = viewModelScope.launch {
        if (key in aiBusy.value) return@launch
        aiBusy.value = aiBusy.value + key
        var response: org.json.JSONObject? = null
        val error = try {
            g.sync.sync()
            response = withContext(Dispatchers.IO) { g.api.callFunction(function, body) }
            g.sync.sync()
            null
        } catch (e: ApiException) {
            when (e.code) {
                404 -> "A função ainda não foi publicada no Supabase (Bloco C: configure os secrets e rode o workflow de backend)."
                else -> e.message ?: "Erro ${e.code}"
            }
        } catch (e: IOException) {
            "Sem conexão: ${e.message}"
        } finally {
            aiBusy.value = aiBusy.value - key
        }
        onResult(response, error)
    }

    fun analyze(onResult: (String?, String?) -> Unit) =
        callCloud("assistant", "analise", org.json.JSONObject().put("action", "analise")) { r, e -> onResult(r?.optString("texto"), e) }

    fun ask(question: String, onResult: (String?, String?) -> Unit) =
        callCloud("assistant", "pergunta", org.json.JSONObject().put("action", "pergunta").put("pergunta", question)) { r, e -> onResult(r?.optString("texto"), e) }

    fun healthGuidance(caseId: String, onResult: (String?, String?) -> Unit) =
        callCloud("assistant", "saude:$caseId", org.json.JSONObject().put("action", "saude").put("case_id", caseId)) { r, e -> onResult(r?.optString("texto"), e) }

    fun generateSpecies(livestockId: String, onResult: (String?) -> Unit = {}) =
        callCloud("assistant", "especie:$livestockId", org.json.JSONObject().put("action", "especie").put("livestock_id", livestockId)) { _, e -> onResult(e) }

    fun sendTest(mode: String, onResult: (String) -> Unit) =
        callCloud("notifier", mode, org.json.JSONObject().put("mode", mode)) { r, e -> onResult(e ?: r?.optString("message").orEmpty().ifBlank { "Enviado." }) }

    /** Manda o relatorio (texto) para o e-mail da conta, pela Edge Function notifier (Resend). */
    fun emailReport(subject: String, text: String, onResult: (String) -> Unit) =
        callCloud("notifier", "report", org.json.JSONObject().put("mode", "report").put("subject", subject).put("text", text)) { r, e ->
            onResult(e ?: r?.optString("message").orEmpty().ifBlank { "Enviado." })
        }

    fun save(x: br.com.lunelli.lulucoreef.data.SpeciesInfo) = persist(db.speciesInfo(), x.copy(updatedAt = ts(), pending = true))

    // ---- fotos ----

    fun importPhoto(uri: Uri, onPath: (String?) -> Unit) = viewModelScope.launch {
        val path = withContext(Dispatchers.IO) { g.photos.importImage(uri) }
        onPath(path)
        if (path != null) afterWrite()
    }
}
