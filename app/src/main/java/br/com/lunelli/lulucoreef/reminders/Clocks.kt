package br.com.lunelli.lulucoreef.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import br.com.lunelli.lulucoreef.App
import br.com.lunelli.lulucoreef.MainActivity
import br.com.lunelli.lulucoreef.R
import br.com.lunelli.lulucoreef.domain.nextRing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject

/** Um timer em contagem: nome opcional e quando acaba. */
data class Timer(val id: String, val label: String, val endAt: Long)

/** O que esta tocando agora. */
data class Ringing(val titulo: String, val alarmId: String?)

/**
 * Timers e alarmes do tablet.
 *
 * Separado dos lembretes de rotina (Reminders) de proposito: rotina e tarefa, com calendario,
 * historico e nota de cumprimento. Timer e alarme so tocam. Misturar os dois faria o timer de
 * "desligar a bomba 10 min" entrar no calendario e na nota de saude do aquario.
 *
 * Os dois usam o AlarmManager, nao uma contagem na tela: o uso real e desligar a bomba ou o skimmer e
 * deixar o tablet de lado, muitas vezes com a tela apagada. Uma contagem em Compose morreria ali.
 *
 * Os timers ficam em SharedPreferences (sao de passagem) e os alarmes no Room, so neste aparelho.
 */
object Clocks {
    private const val CHANNEL = "timers"
    private const val NOTIF_ID = 1002
    private const val PREFS = "clocks"
    private const val KEY_TIMERS = "timers"
    const val ACTION_STOP = "br.com.lunelli.lulucoreef.STOP_RINGING"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var player: MediaPlayer? = null

    private val _timers = MutableStateFlow<List<Timer>>(emptyList())
    val timers: StateFlow<List<Timer>> = _timers

    private val _ringing = MutableStateFlow<Ringing?>(null)
    val ringing: StateFlow<Ringing?> = _ringing

    /** Minutos dos botoes fixos do bloco de timer. */
    val PRESETS = listOf(5, 10, 15, 20, 30)

    // ---------------------------------------------------------------- timers

    fun load(context: Context) {
        _timers.value = readTimers(context).filter { it.endAt > System.currentTimeMillis() }
        reschedule(context)
    }

    fun startTimer(context: Context, minutes: Int, label: String = "") {
        val t = Timer(java.util.UUID.randomUUID().toString(), label, System.currentTimeMillis() + minutes * 60_000L)
        writeTimers(context, readTimers(context).filter { it.endAt > System.currentTimeMillis() } + t)
        load(context)
    }

    fun cancelTimer(context: Context, id: String) {
        writeTimers(context, readTimers(context).filter { it.id != id })
        load(context)
    }

    private fun readTimers(context: Context): List<Timer> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TIMERS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Timer(o.getString("id"), o.optString("label", ""), o.getLong("endAt"))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeTimers(context: Context, list: List<Timer>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("label", it.label).put("endAt", it.endAt)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_TIMERS, arr.toString()).apply()
    }

    // ---------------------------------------------------------------- agenda

    private fun pending(context: Context): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getBroadcast(context, 2, Intent(context, ClockReceiver::class.java), flags)
    }

    /** Reagenda um unico alarme do sistema para o toque mais proximo (timer ou alarme). */
    fun reschedule(context: Context) {
        val app = context.applicationContext
        scope.launch {
            val now = System.currentTimeMillis()
            val zone = ZoneId.systemDefault()
            val doTimer = readTimers(app).filter { it.endAt > now }.minOfOrNull { it.endAt }
            val doAlarme = try {
                (app as App).graph.db.alarms().list().mapNotNull { nextRing(it, now, zone) }.minOrNull()
            } catch (_: Exception) {
                null
            }
            val next = listOfNotNull(doTimer, doAlarme).minOrNull()
            val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = pending(app)
            if (next == null) {
                am.cancel(pi)
                return@launch
            }
            try {
                when {
                    Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms() -> am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
                    Build.VERSION.SDK_INT >= 23 -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
                    else -> am.setExact(AlarmManager.RTC_WAKEUP, next, pi)
                }
            } catch (_: SecurityException) {
                am.set(AlarmManager.RTC_WAKEUP, next, pi)
            }
        }
    }

    /** Chamado pelo alarme do sistema: ve o que venceu, toca e reagenda. */
    suspend fun fire(context: Context) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        try {
            // timers vencidos (1 min de folga para atraso do sistema)
            val vencidos = readTimers(app).filter { it.endAt <= now + 60_000 }
            if (vencidos.isNotEmpty()) {
                writeTimers(app, readTimers(app).filter { it.endAt > now + 60_000 })
                _timers.value = readTimers(app)
                val nome = vencidos.firstOrNull { it.label.isNotBlank() }?.label
                ring(app, if (nome != null) "Timer: $nome" else "Timer acabou", null, alto = true)
            }
            // alarmes que venceram neste minuto
            val alarmes = try { (app as App).graph.db.alarms().list() } catch (_: Exception) { emptyList() }
            val tocando = alarmes.filter { a ->
                val anterior = nextRing(a, now - 90_000, zone)
                anterior != null && anterior <= now + 60_000
            }
            tocando.firstOrNull()?.let { a ->
                ring(app, a.titulo.ifBlank { "Alarme" }, a.id, a.alto)
                // alarme de uma vez so nao volta: desliga depois de tocar
                if (a.recorrencia == "unica") {
                    try { (app as App).graph.db.alarms().put(a.copy(ativo = false)) } catch (_: Exception) { }
                }
            }
        } finally {
            reschedule(app)
        }
    }

    // ---------------------------------------------------------------- toque

    /** Toca o som de alarme do tablet em laco, mostra a notificacao e traz o app para a frente. */
    fun ring(context: Context, titulo: String, alarmId: String?, alto: Boolean) {
        _ringing.value = Ringing(titulo, alarmId)
        notify(context, titulo)
        try {
            stopPlayer()
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: return
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(context, uri)
                isLooping = true
                val v = if (alto) 1f else 0.45f
                setVolume(v, v)
                setOnErrorListener { mp, _, _ -> mp.release(); player = null; true }
                prepare()
                start()
            }
            // no "alto" sobe tambem o volume de alarme do tablet, senao nao se ouve do outro lado da sala
            if (alto) try {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
            } catch (_: Exception) { }
        } catch (_: Exception) {
        }
        Reminders.bringToFront(context)
    }

    /** Para o som e tira a notificacao. O alarme em si continua ativo para o proximo dia. */
    fun stop(context: Context) {
        stopPlayer()
        _ringing.value = null
        try {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIF_ID)
        } catch (_: Exception) {
        }
    }

    private fun stopPlayer() {
        try {
            player?.stop()
            player?.release()
        } catch (_: Exception) {
        }
        player = null
    }

    private fun notify(context: Context, titulo: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "Timers e alarmes", NotificationManager.IMPORTANCE_HIGH)
            ch.setSound(null, null) // o som e tocado pelo app, em laco, ate apertar Parar
            nm.createNotificationChannel(ch)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        val abrir = PendingIntent.getActivity(context, 3, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), flags)
        val parar = PendingIntent.getBroadcast(context, 4, Intent(context, ClockReceiver::class.java).setAction(ACTION_STOP), flags)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_drop)
            .setContentTitle(titulo)
            .setContentText("Toque em Parar para silenciar.")
            .setContentIntent(abrir)
            .addAction(0, "Parar", parar)
            .setAutoCancel(false)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try { nm.notify(NOTIF_ID, n) } catch (_: SecurityException) { }
    }
}

class ClockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Clocks.ACTION_STOP) {
            Clocks.stop(context.applicationContext)
            return
        }
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                Clocks.fire(context.applicationContext)
            } finally {
                result.finish()
            }
        }
    }
}
