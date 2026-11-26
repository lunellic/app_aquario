package br.com.lunelli.lulucoreef.diag

import android.content.Context
import android.os.Build
import br.com.lunelli.lulucoreef.BuildConfig
import br.com.lunelli.lulucoreef.data.Api
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Diario de diagnostico: quedas e cada passo da atualizacao, gravados num arquivo e enviados ao
 * Supabase (tabela app_diagnosticos) na proxima vez que da.
 *
 * Existe porque "o app simplesmente fecha" no tablet nao deixava rastro nenhum: o Fire nao mostra
 * nada, o tablet nao fica ligado num computador, e o emulador do CI nao reproduzia. Com o diario,
 * o que aconteceu no aparelho de verdade chega aqui sozinho, sem depender de print.
 *
 * O arquivo e gravado na hora (inclusive no meio de uma queda) e o envio e separado: o que nao deu
 * para mandar agora vai na proxima abertura.
 */
object Diario {
    @Volatile private var app: Context? = null
    private const val LIMITE = 96_000L

    fun iniciar(ctx: Context) { app = ctx.applicationContext }

    private fun arquivo(ctx: Context) = File(ctx.filesDir, "diario.jsonl")

    private fun agoraIso(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
        .apply { timeZone = TimeZone.getDefault() }.format(Date())

    val aparelho: String get() = "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    fun registrar(tipo: String, texto: String) {
        val c = app ?: return
        runCatching {
            val linha = JSONObject()
                .put("criado_em", agoraIso())
                .put("versao", BuildConfig.VERSION_CODE)
                .put("aparelho", aparelho)
                .put("tipo", tipo)
                .put("texto", texto.take(6000))
                .toString()
            synchronized(this) {
                val f = arquivo(c)
                if (f.length() > LIMITE) f.writeText("") // diario cheio de coisa velha: recomeca
                f.appendText(linha + "\n")
            }
        }
    }

    /** Manda o que estiver guardado. Silencioso: diagnostico nunca pode atrapalhar o app. */
    fun enviar(api: Api) {
        val c = app ?: return
        if (BuildConfig.DEBUG) return // o CI nao polui o diario do tablet
        runCatching {
            val enviando = File(c.filesDir, "diario.enviando.jsonl")
            synchronized(this) {
                val f = arquivo(c)
                if (!enviando.exists()) {
                    if (!f.exists() || f.length() == 0L) return
                    if (!f.renameTo(enviando)) return
                }
            }
            val linhas = enviando.readLines().filter { it.isNotBlank() }
            val arr = JSONArray()
            linhas.forEach { l -> runCatching { arr.put(JSONObject(l)) } }
            if (arr.length() > 0) api.enviarDiagnosticos(arr.toString())
            enviando.delete()
        }
    }
}
