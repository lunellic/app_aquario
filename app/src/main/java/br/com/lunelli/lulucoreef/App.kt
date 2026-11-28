package br.com.lunelli.lulucoreef

import android.app.Application
import br.com.lunelli.lulucoreef.data.Api
import br.com.lunelli.lulucoreef.data.AppDb
import br.com.lunelli.lulucoreef.data.Photos
import br.com.lunelli.lulucoreef.data.SessionStore
import br.com.lunelli.lulucoreef.data.Settings
import br.com.lunelli.lulucoreef.data.SyncEngine
import br.com.lunelli.lulucoreef.data.SyncWorker
import kotlinx.coroutines.launch

/** Dependencias do app (um modulo, sem framework de injecao). */
class Graph(app: Application) {
    val session = SessionStore(app)
    val settings = Settings(app)
    val db = AppDb.build(app)
    val api = Api(app, session)
    val photos = Photos(app, api, session)
    val sync = SyncEngine(app, db, api, session, photos)
}

class App : Application() {
    val graph by lazy { Graph(this) }

    override fun onCreate() {
        super.onCreate()
        br.com.lunelli.lulucoreef.diag.Diario.iniciar(this)
        br.com.lunelli.lulucoreef.diag.Queda.instalar(this)
        br.com.lunelli.lulucoreef.update.VersaoVista.registrarAbertura(this)
        // So no APK de debug do CI: o teste de atualizacao de ponta a ponta cria este arquivo para
        // o app abrir no Painel sem login de verdade e sem tentar sincronizar. Nunca existe na
        // release, que e o que vai para o tablet.
        if (BuildConfig.DEBUG && java.io.File(filesDir, "e2e").exists()) {
            Api.offlineForTests = true
            if (graph.session.refreshToken == null) {
                graph.session.save(org.json.JSONObject("""{"access_token":"x","refresh_token":"y","expires_in":999999,"user":{"id":"00000000-0000-0000-0000-000000000001","email":"e2e@example.com"}}"""))
            }
        }
        SyncWorker.schedulePeriodic(this)
        // Alarmes e timers criados no iPhone: o tablet confere a cada 20 s enquanto o app esta vivo
        // (fica sempre aberto ao lado do aquario). A sincronizacao completa continua de 15 em 15 min.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
            while (true) {
                kotlinx.coroutines.delay(20_000)
                if (!Api.offlineForTests && graph.session.loggedIn.value) graph.sync.syncRapido()
            }
        }
        // Manda o diario guardado (queda da ultima vez, passos da ultima atualizacao) depois que o
        // app ja abriu, fora da linha principal.
        Thread {
            Thread.sleep(15_000)
            br.com.lunelli.lulucoreef.diag.Diario.enviar(graph.api)
        }.apply { isDaemon = true; name = "diario" }.start()
    }
}
