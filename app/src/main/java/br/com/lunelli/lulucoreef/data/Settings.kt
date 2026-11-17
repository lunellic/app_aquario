package br.com.lunelli.lulucoreef.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Preferencias locais do aparelho (nao sincronizam). */
data class LocalSettings(
    val vitrineMinutes: Int = 2,
    val nightStartHour: Int = 0,
    val nightEndHour: Int = 7,
    val soundOn: Boolean = true,
    val soundVolume: Int = 70, // 0..100
    val inventoryNoticeSeen: Boolean = false,
    val waterFx: Boolean = true,
)

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<LocalSettings> = _state

    private fun read() = LocalSettings(
        vitrineMinutes = prefs.getInt("vitrine_min", 2),
        nightStartHour = prefs.getInt("night_start", 0),
        nightEndHour = prefs.getInt("night_end", 7),
        soundOn = prefs.getBoolean("sound_on", true),
        soundVolume = prefs.getInt("sound_vol", 70),
        inventoryNoticeSeen = prefs.getBoolean("inv_notice", false),
        waterFx = prefs.getBoolean("water_fx", true),
    )

    fun update(change: (LocalSettings) -> LocalSettings) {
        val s = change(_state.value)
        prefs.edit()
            .putInt("vitrine_min", s.vitrineMinutes)
            .putInt("night_start", s.nightStartHour)
            .putInt("night_end", s.nightEndHour)
            .putBoolean("sound_on", s.soundOn)
            .putInt("sound_vol", s.soundVolume)
            .putBoolean("inv_notice", s.inventoryNoticeSeen)
            .putBoolean("water_fx", s.waterFx)
            .apply()
        _state.value = s
    }

    companion object {
        /** Modo noite: intervalo [inicio, fim) em horas, podendo cruzar a meia-noite. */
        fun isNight(hour: Int, start: Int, end: Int): Boolean = when {
            start == end -> false
            start < end -> hour in start until end
            else -> hour >= start || hour < end
        }
    }
}
