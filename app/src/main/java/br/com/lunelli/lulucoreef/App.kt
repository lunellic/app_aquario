package br.com.lunelli.lulucoreef

import android.app.Application
import br.com.lunelli.lulucoreef.data.Api
import br.com.lunelli.lulucoreef.data.AppDb
import br.com.lunelli.lulucoreef.data.Photos
import br.com.lunelli.lulucoreef.data.SessionStore
import br.com.lunelli.lulucoreef.data.Settings
import br.com.lunelli.lulucoreef.data.SyncEngine
import br.com.lunelli.lulucoreef.data.SyncWorker

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
        SyncWorker.schedulePeriodic(this)
    }
}
