package br.com.lunelli.lulucoreef.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import br.com.lunelli.lulucoreef.App
import br.com.lunelli.lulucoreef.MainActivity
import br.com.lunelli.lulucoreef.R
import br.com.lunelli.lulucoreef.data.Settings
import br.com.lunelli.lulucoreef.domain.Occurrence
import br.com.lunelli.lulucoreef.domain.nextDueAfter
import br.com.lunelli.lulucoreef.domain.occurrencesOn
import br.com.lunelli.lulucoreef.domain.reminderText
import br.com.lunelli.lulucoreef.domain.toLocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * Lembretes locais: um alarme exato para o proximo horario de rotina. Ao disparar, toca um som curto
 * (silenciado no modo noite), mostra a notificacao agrupada e agenda o proximo.
 */
object Reminders {
    private const val CHANNEL = "rotinas"
    private const val NOTIF_ID = 1001
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun schedule(context: Context) {
        val app = context.applicationContext
        scope.launch { scheduleNext(app, System.currentTimeMillis()) }
    }

    private fun pending(context: Context): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getBroadcast(context, 0, Intent(context, ReminderReceiver::class.java), flags)
    }

    private suspend fun load(context: Context, now: Long): Triple<List<Occurrence>, Long?, ZoneId> {
        val graph = (context as App).graph
        val zone = ZoneId.systemDefault()
        if (!graph.session.loggedIn.value) return Triple(emptyList(), null, zone)
        val routines = graph.db.routines().all().first()
        val logs = graph.db.routineLogs().all().first()
        val today = occurrencesOn(routines, logs, now.toLocalDate(zone), zone)
        val candidates = routines.mapNotNull { nextDueAfter(it, now, zone) } +
            today.filter { it.open && it.effectiveDueAt > now }.map { it.effectiveDueAt }
        return Triple(today, candidates.minOrNull(), zone)
    }

    private suspend fun scheduleNext(context: Context, now: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(context)
        val next = try { load(context, now).second } catch (_: Exception) { null }
        if (next == null) {
            am.cancel(pi)
            return
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

    /** Chamado pelo alarme. */
    suspend fun fire(context: Context) {
        val now = System.currentTimeMillis()
        val graph = (context as App).graph
        try {
            val (today, _, zone) = load(context, now)
            val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
            val notified = prefs.getStringSet("notified", emptySet())!!
            // vence agora (com 1 min de folga) e ainda nao foi avisado neste horario
            val due = today.filter { it.open && it.effectiveDueAt <= now + 60_000 }
            val fresh = due.filter { "${it.routine.id}|${it.effectiveDueAt}" !in notified }
            if (fresh.isNotEmpty()) {
                val products = graph.db.products().all().first().associateBy { it.id }
                val volume = graph.db.tankConfig().all().first().firstOrNull()?.volumeDosagemL ?: 100.0
                val text = fresh.groupBy { it.effectiveDueAt / 60_000 }.values.joinToString("\n") { reminderText(it, products, volume, zone) }
                val s = graph.settings.state.value
                val hour = Instant.ofEpochMilli(now).atZone(zone).hour
                val night = Settings.isNight(hour, s.nightStartHour, s.nightEndHour)
                notify(context, text)
                if (s.soundOn && !night) playSound(context, s.soundVolume)
                prefs.edit().putStringSet("notified", due.map { "${it.routine.id}|${it.effectiveDueAt}" }.toSet()).apply()
            }
        } finally {
            scheduleNext(context, now + 60_000)
        }
    }

    private fun notify(context: Context, text: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "Rotinas do aquário", NotificationManager.IMPORTANCE_DEFAULT)
            ch.setSound(null, null) // o som e tocado pelo app, com o volume das Configuracoes
            nm.createNotificationChannel(ch)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        val open = PendingIntent.getActivity(context, 1, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), flags)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_drop)
            .setContentTitle("Rotinas do aquário")
            .setContentText(text.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try { nm.notify(NOTIF_ID, n) } catch (_: SecurityException) { }
    }

    fun playSound(context: Context, volumePct: Int) {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: return
            val v = (volumePct.coerceIn(0, 100)) / 100f
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(context, uri)
                setVolume(v, v)
                setOnCompletionListener { it.release() }
                setOnErrorListener { mp, _, _ -> mp.release(); true }
                prepare()
                start()
            }
        } catch (_: Exception) {
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                Reminders.fire(context.applicationContext)
            } finally {
                result.finish()
            }
        }
    }
}

/** Reagenda o alarme depois de reiniciar o tablet ou atualizar o app. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.schedule(context)
    }
}
