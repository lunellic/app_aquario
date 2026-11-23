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
    /** Onda do descanso. */
    val waterFx: Boolean = true,
    /**
     * Microanimacoes de dentro do app (cascata dos blocos, numero contando, anel enchendo, mola do
     * botao, transicao entre telas). Desligadas por padrao: no Fire HD 8 de 2017 elas custam quadros
     * e nao mostram nada. A onda do descanso tem a chave propria acima.
     */
    val uiAnim: Boolean = false,
    /** Forca o brilho da tela enquanto o app esta na frente (0..100). 0 = deixa o brilho do sistema. */
    val brilho: Int = 100,
    val salGramasPorLitro: Float = 37f,
    val cicloDia: Int = 16,
    /** Parametros que aparecem no Painel e no standby (NO3 e PO4 sao opcionais). */
    val shownParams: Set<String> = setOf("kh", "ca", "mg", "salinidade"),
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
        salGramasPorLitro = prefs.getFloat("sal_gpl", 37f),
        cicloDia = prefs.getInt("ciclo_dia", 16),
        inventoryNoticeSeen = prefs.getBoolean("inv_notice", false),
        waterFx = prefs.getBoolean("water_fx", true),
        uiAnim = prefs.getBoolean("ui_anim", false),
        brilho = prefs.getInt("brilho", 100),
        shownParams = prefs.getStringSet("shown_params", null)?.toSet() ?: setOf("kh", "ca", "mg", "salinidade"),
    )

    fun update(change: (LocalSettings) -> LocalSettings) {
        val s = change(_state.value)
        prefs.edit()
            .putInt("vitrine_min", s.vitrineMinutes)
            .putInt("night_start", s.nightStartHour)
            .putInt("night_end", s.nightEndHour)
            .putBoolean("sound_on", s.soundOn)
            .putInt("sound_vol", s.soundVolume)
            .putFloat("sal_gpl", s.salGramasPorLitro)
            .putInt("ciclo_dia", s.cicloDia)
            .putBoolean("inv_notice", s.inventoryNoticeSeen)
            .putBoolean("water_fx", s.waterFx)
            .putBoolean("ui_anim", s.uiAnim)
            .putInt("brilho", s.brilho)
            .putStringSet("shown_params", s.shownParams)
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
