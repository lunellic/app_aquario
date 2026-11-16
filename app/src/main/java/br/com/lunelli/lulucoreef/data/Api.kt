package br.com.lunelli.lulucoreef.data

import android.content.Context
import br.com.lunelli.lulucoreef.BuildConfig
import br.com.lunelli.lulucoreef.net.TrustedHttp
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

class ApiException(val code: Int, message: String) : IOException(message)

/** Sessao invalida no servidor (refresh token recusado). Unico caso em que o app volta ao login. */
class SessionExpiredException : IOException("Sessão expirada")

/** REST, Auth e Storage do Supabase via OkHttp. Chamadas bloqueantes: usar em Dispatchers.IO. */
class Api(context: Context, private val session: SessionStore) {
    private val real by lazy { TrustedHttp.client(context) }
    private val http get() = if (offlineForTests) throw IOException("offline (teste)") else real
    private val base = BuildConfig.SUPABASE_URL
    private val key = BuildConfig.SUPABASE_KEY
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    fun login(email: String, password: String) {
        val body = JSONObject().put("email", email.trim()).put("password", password).toString().toRequestBody(jsonType)
        val req = Request.Builder().url("$base/auth/v1/token?grant_type=password").header("apikey", key).post(body).build()
        http.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw ApiException(r.code, errorMessage(text))
            session.save(JSONObject(text))
        }
    }

    /** Token valido, renovando se faltar menos de 2 min. Falha de rede lanca IOException e mantem a sessao. */
    @Synchronized
    private fun freshToken(force: Boolean = false): String {
        val refresh = session.refreshToken ?: throw SessionExpiredException()
        val current = session.accessToken
        if (!force && current != null && session.expiresAtMs - System.currentTimeMillis() > 120_000) return current
        val body = JSONObject().put("refresh_token", refresh).toString().toRequestBody(jsonType)
        val req = Request.Builder().url("$base/auth/v1/token?grant_type=refresh_token").header("apikey", key).post(body).build()
        http.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (r.isSuccessful) {
                session.save(JSONObject(text))
                return session.accessToken!!
            }
            if (r.code == 400 || r.code == 401 || r.code == 403) {
                session.clear()
                throw SessionExpiredException()
            }
            throw ApiException(r.code, errorMessage(text))
        }
    }

    /** Executa com Authorization; em 401 renova o token uma vez e repete. */
    private fun <T> authed(build: () -> Request.Builder, handle: (Response) -> T): T {
        var token = freshToken()
        repeat(2) { attempt ->
            val req = build().header("apikey", key).header("Authorization", "Bearer $token").build()
            http.newCall(req).execute().use { r ->
                if (r.code == 401 && attempt == 0) {
                    token = freshToken(force = true)
                } else {
                    return handle(r)
                }
            }
        }
        throw ApiException(401, "Não autorizado")
    }

    /** Linhas com synced_at > cursor, em ordem. Retorna o JSON bruto (array). */
    fun pull(table: String, cursor: String?, limit: Int): String {
        val filter = if (cursor != null) "&synced_at=gt.${URLEncoder.encode(cursor, "UTF-8")}" else ""
        return authed({ Request.Builder().url("$base/rest/v1/$table?select=*$filter&order=synced_at.asc&limit=$limit") }) { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw ApiException(r.code, "$table: ${errorMessage(text)}")
            text
        }
    }

    /** Upsert por id. */
    fun push(table: String, jsonArray: String) {
        authed({
            Request.Builder().url("$base/rest/v1/$table?on_conflict=id")
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(jsonArray.toRequestBody(jsonType))
        }) { r ->
            if (!r.isSuccessful) throw ApiException(r.code, "$table: ${errorMessage(r.body?.string().orEmpty())}")
        }
    }

    fun uploadPhoto(path: String, bytes: ByteArray) {
        authed({
            Request.Builder().url("$base/storage/v1/object/fotos/$path")
                .header("x-upsert", "true")
                .post(bytes.toRequestBody("image/jpeg".toMediaType()))
        }) { r ->
            if (!r.isSuccessful) throw ApiException(r.code, "foto: ${errorMessage(r.body?.string().orEmpty())}")
        }
    }

    fun downloadPhoto(path: String): ByteArray? =
        authed({ Request.Builder().url("$base/storage/v1/object/authenticated/fotos/$path") }) { r ->
            if (r.isSuccessful) r.body?.bytes() else null
        }

    companion object {
        /** So para testes instrumentados: simula aparelho sem rede. */
        @Volatile var offlineForTests = false

        fun errorMessage(body: String): String = try {
            val o = JSONObject(body)
            listOf("msg", "error_description", "message", "error").map { o.optString(it) }.firstOrNull { it.isNotEmpty() } ?: body.take(300)
        } catch (_: Exception) { body.take(300) }
    }
}
