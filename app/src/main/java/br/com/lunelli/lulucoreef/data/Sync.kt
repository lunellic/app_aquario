package br.com.lunelli.lulucoreef.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import br.com.lunelli.lulucoreef.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.TimeUnit

data class SyncStatus(
    val running: Boolean = false,
    val lastOkAt: Long? = null,
    val lastError: String? = null,
    val pending: Int = 0,
)

/**
 * Offline primeiro: o Room e a fonte de verdade. Aqui: puxa o que mudou no servidor desde o cursor
 * (synced_at) e envia as linhas pendentes. Conflito: vence o updated_at mais recente.
 */
class SyncEngine(context: Context, private val db: AppDb, private val api: Api, private val session: SessionStore, private val photos: Photos) {
    private val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    val status = MutableStateFlow(SyncStatus(lastOkAt = prefs.getLong("last_ok", 0).takeIf { it > 0 }))

    /** @return true se sincronizou tudo. */
    suspend fun sync(): Boolean = withContext(Dispatchers.IO) {
        if (!session.loggedIn.value) return@withContext false
        mutex.withLock {
            status.value = status.value.copy(running = true)
            try {
                // Uma segunda chance numa conexao nova. O erro de tempo esgotado quase sempre e
                // conexao que morreu enquanto o Wi-Fi do Fire dormia; jogar o pool fora e repetir
                // resolve sem esperar o proximo ciclo de 15 minutos.
                try {
                    rodada()
                } catch (e: java.io.IOException) {
                    // So erro de rede ganha segunda chance. Resposta do servidor (400, 401,
                    // sessao expirada) repetida da o mesmo resultado, so que mais devagar.
                    if (e is ApiException || e is SessionExpiredException) throw e
                    api.descartarConexoes()
                    rodada()
                }
                val now = System.currentTimeMillis()
                prefs.edit().putLong("last_ok", now).apply()
                status.value = SyncStatus(lastOkAt = now, pending = countPending())
                true
            } catch (e: SessionExpiredException) {
                status.value = status.value.copy(running = false, lastError = "Sessão expirada")
                false
            } catch (e: Exception) {
                val msg = e.message ?: e.javaClass.simpleName
                val friendly = when {
                    msg.contains("schema cache") || msg.contains("does not exist") -> "O banco no Supabase está desatualizado: falta rodar o SQL mais recente ($msg)"
                    e is java.net.SocketTimeoutException || msg.contains("timeout", ignoreCase = true) ->
                        "O servidor não respondeu a tempo, duas vezes seguidas. Confira o Wi-Fi do tablet; tento de novo sozinho em alguns minutos."
                    e is java.net.UnknownHostException -> "Sem internet no tablet. Tento de novo quando a rede voltar."
                    else -> msg
                }
                status.value = status.value.copy(running = false, lastError = friendly, pending = countPending())
                false
            }
        }
    }

    private suspend fun rodada() {
        val tables = db.syncTables()
        // Banco local de versao nova (coluna nova numa tabela): o que foi puxado pela versao antiga do
        // app veio sem a coluna e o cursor ja tinha passado dessas linhas. Zera os cursores uma vez
        // para puxar tudo de novo; linha sem alteracao local e sobrescrita pela do servidor.
        val esquema = db.openHelper.readableDatabase.version
        val refazer = prefs.getInt("esquema", 0) != esquema
        if (refazer) prefs.edit().apply { tables.forEach { remove("cursor_${it.name}") } }.apply()
        tables.forEach { pull(it) }
        if (refazer) prefs.edit().putInt("esquema", esquema).apply()
        tables.forEach { push(it) }
        photos.uploadPending()
    }

    suspend fun countPending(): Int = db.syncTables().sumOf { it.dao.pendingCount() } + photos.pendingCount()

    suspend fun refreshPendingCount() {
        status.value = status.value.copy(pending = countPending())
    }

    private suspend fun <T : Syncable> pull(table: SyncTable<T>) {
        val key = "cursor_${table.name}"
        var cursor = prefs.getString(key, null)
        while (true) {
            val rows = ApiJson.parseToJsonElement(api.pull(table.name, cursor, PAGE)) as JsonArray
            for (row in rows) {
                val remote = ApiJson.decodeFromJsonElement(table.serializer, row) // pending = false
                val local = table.dao.get(remote.id)
                if (local == null || !local.pending || remote.updatedAt > local.updatedAt) table.dao.upsert(remote)
                cursor = row.jsonObject["synced_at"]?.jsonPrimitive?.content ?: cursor
            }
            if (rows.isNotEmpty()) prefs.edit().putString(key, cursor).apply()
            if (rows.size < PAGE) break
        }
    }

    private suspend fun <T : Syncable> push(table: SyncTable<T>) {
        val pending = table.dao.pending()
        for (chunk in pending.chunked(200)) {
            api.push(table.name, ApiJson.encodeToString(ListSerializer(table.serializer), chunk))
            chunk.forEach { table.dao.markSynced(it.id, it.updatedAt) }
        }
    }

    /** Usado no logout: apaga cursores para um novo login puxar tudo de novo. */
    fun reset() {
        prefs.edit().clear().apply()
        status.value = SyncStatus()
    }

    companion object {
        private const val PAGE = 500
    }
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = (applicationContext as App).graph
        if (!graph.session.loggedIn.value) return Result.success()
        return if (graph.sync.sync()) Result.success() else Result.retry()
    }

    companion object {
        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Envia pendentes assim que houver rede, com retentativa exponencial. */
        fun kick(context: Context) {
            val req = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(online)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("sync_now", ExistingWorkPolicy.REPLACE, req)
        }

        /** Puxa alteracoes a cada 15 minutos. */
        fun schedulePeriodic(context: Context) {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(online).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("sync_periodic", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
