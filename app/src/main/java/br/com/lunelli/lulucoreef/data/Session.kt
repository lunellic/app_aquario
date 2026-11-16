package br.com.lunelli.lulucoreef.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/** Sessao do Supabase Auth guardada em SharedPreferences (privado do app). */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    private val _loggedIn = MutableStateFlow(prefs.getString("refresh", null) != null)
    val loggedIn: StateFlow<Boolean> = _loggedIn

    val accessToken: String? get() = prefs.getString("access", null)
    val refreshToken: String? get() = prefs.getString("refresh", null)
    val expiresAtMs: Long get() = prefs.getLong("expires_at", 0)
    val userId: String? get() = prefs.getString("user_id", null)
    val email: String? get() = prefs.getString("email", null)

    /** Recebe a resposta de /auth/v1/token. */
    fun save(tokenResponse: JSONObject) {
        val user = tokenResponse.optJSONObject("user")
        prefs.edit()
            .putString("access", tokenResponse.getString("access_token"))
            .putString("refresh", tokenResponse.getString("refresh_token"))
            .putLong("expires_at", System.currentTimeMillis() + tokenResponse.optLong("expires_in", 3600) * 1000)
            .apply {
                if (user != null) {
                    putString("user_id", user.optString("id"))
                    putString("email", user.optString("email"))
                }
            }
            .commit()
        _loggedIn.value = true
    }

    fun clear() {
        prefs.edit().clear().commit()
        _loggedIn.value = false
    }
}
