package br.com.lunelli.lulucoreef.net

import android.content.Context
import android.os.Build
import br.com.lunelli.lulucoreef.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.cert.X509Certificate

/** Prova de conexao: TLS, login no Supabase Auth e uma consulta REST autenticada. */
class ConnectionProbe(private val context: Context) {

    data class Step(val ok: Boolean, val title: String, val detail: String)

    fun run(email: String, password: String, emit: (Step) -> Unit) {
        val base = BuildConfig.SUPABASE_URL
        val key = BuildConfig.SUPABASE_KEY
        val json = "application/json; charset=utf-8".toMediaType()

        emit(Step(true, "Dispositivo", "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), app ${BuildConfig.VERSION_NAME}"))

        val client = try {
            TrustedHttp.client(context).also {
                emit(Step(true, "Certificados", "${TrustedHttp.systemRootCount} raízes do sistema + ${TrustedHttp.embeddedSubjects.size} embutidas"))
            }
        } catch (e: Exception) {
            emit(Step(false, "Certificados", describe(e))); return
        }

        // 1) TLS + saude do Auth (sem login)
        try {
            client.newCall(Request.Builder().url("$base/auth/v1/health").header("apikey", key).build()).execute().use { r ->
                val h = r.handshake
                val chain = h?.peerCertificates.orEmpty().filterIsInstance<X509Certificate>()
                val issuer = chain.lastOrNull()?.issuerX500Principal?.name ?: "?"
                emit(Step(r.isSuccessful, "TLS e servidor", "HTTP ${r.code}, ${h?.tlsVersion?.javaName}, ${h?.cipherSuite?.javaName}\nCadeia: ${chain.size} certificados, emissor do topo: $issuer"))
                if (!r.isSuccessful) return
            }
        } catch (e: Exception) {
            emit(Step(false, "TLS e servidor", describe(e))); return
        }

        // 2) Login por e-mail e senha
        val token: String
        try {
            val body = JSONObject().put("email", email.trim()).put("password", password).toString().toRequestBody(json)
            val req = Request.Builder().url("$base/auth/v1/token?grant_type=password").header("apikey", key).post(body).build()
            client.newCall(req).execute().use { r ->
                val text = r.body?.string().orEmpty()
                if (!r.isSuccessful) {
                    emit(Step(false, "Login", "HTTP ${r.code}: ${errorMessage(text)}")); return
                }
                val o = JSONObject(text)
                token = o.getString("access_token")
                val hasRefresh = o.optString("refresh_token").isNotEmpty()
                emit(Step(true, "Login", "Usuário ${o.optJSONObject("user")?.optString("email")}, refresh token: ${if (hasRefresh) "sim" else "não"}, expira em ${o.optInt("expires_in")} s"))
            }
        } catch (e: Exception) {
            emit(Step(false, "Login", describe(e))); return
        }

        // 3) Consulta REST autenticada (tabela das migracoes)
        try {
            val req = Request.Builder().url("$base/rest/v1/param_ranges?select=parametro,ideal_min,ideal_max,unidade&order=parametro")
                .header("apikey", key).header("Authorization", "Bearer $token").build()
            client.newCall(req).execute().use { r ->
                val text = r.body?.string().orEmpty()
                when {
                    r.isSuccessful -> {
                        val arr = JSONArray(text)
                        val sample = (0 until arr.length()).joinToString(", ") { i ->
                            val o = arr.getJSONObject(i); "${o.optString("parametro")} ${o.opt("ideal_min")}-${o.opt("ideal_max")}"
                        }
                        emit(Step(true, "Consulta ao banco", if (arr.length() == 0) "Conectou, mas param_ranges está vazia (rode o seed)." else "${arr.length()} faixas: $sample"))
                    }
                    r.code == 404 -> emit(Step(true, "Consulta ao banco", "Conexão e autenticação OK. A tabela param_ranges ainda não existe (migrações não aplicadas)."))
                    else -> emit(Step(false, "Consulta ao banco", "HTTP ${r.code}: ${errorMessage(text)}"))
                }
            }
        } catch (e: Exception) {
            emit(Step(false, "Consulta ao banco", describe(e)))
        }
    }

    companion object {
        fun errorMessage(body: String): String = try {
            val o = JSONObject(body)
            listOf("msg", "error_description", "message", "error").map { o.optString(it) }.firstOrNull { it.isNotEmpty() } ?: body.take(300)
        } catch (_: Exception) { body.take(300) }

        fun describe(e: Throwable): String {
            val parts = ArrayList<String>()
            var t: Throwable? = e
            while (t != null && parts.size < 4) { parts += "${t.javaClass.simpleName}: ${t.message}"; t = t.cause }
            return parts.joinToString("\n  causa: ")
        }
    }
}
