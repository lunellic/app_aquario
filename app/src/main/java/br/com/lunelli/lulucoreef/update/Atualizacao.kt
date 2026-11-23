package br.com.lunelli.lulucoreef.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import br.com.lunelli.lulucoreef.BuildConfig
import br.com.lunelli.lulucoreef.net.TrustedHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File

/**
 * Atualizacao do app sem computador: o tablet pergunta se tem versao nova, baixa e abre o
 * instalador.
 *
 * O arquivo fica no Storage do Supabase, e nao no GitHub, por um motivo concreto: o Fire OS 5
 * e de 2017 e nao conhece varias raizes de certificado de hoje. O app ja carrega as raizes que
 * faltam para falar com o Supabase (ver TrustedHttp), entao baixar de la usa um caminho de TLS
 * que comprovadamente funciona neste aparelho. Baixar do GitHub exigiria embutir outro conjunto
 * de raizes e torcer para nao mudarem.
 *
 * Pelo mesmo motivo nao usamos o DownloadManager do sistema: ele baixa com as raizes do Android,
 * que sao justamente as que estao velhas aqui.
 */
data class VersaoRemota(val build: Int, val versao: String, val notas: String, val quando: String) {
    val nova: Boolean get() = build > BuildConfig.VERSION_CODE
}

object Atualizacao {
    private const val PASTA = "app"
    private fun base() = "${BuildConfig.SUPABASE_URL}/storage/v1/object/public/$PASTA"

    /** Le o versao.json publicado pelo CI. Devolve null se nao deu para checar. */
    suspend fun checar(ctx: Context): VersaoRemota? = withContext(Dispatchers.IO) {
        try {
            // O Storage serve com cache longo e a gente quer o numero de agora.
            val req = Request.Builder()
                .url("${base()}/versao.json?t=${System.currentTimeMillis() / 60000}")
                .header("Cache-Control", "no-cache")
                .build()
            TrustedHttp.client(ctx).newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val j = JSONObject(resp.body?.string().orEmpty())
                VersaoRemota(
                    build = j.optInt("build", 0),
                    versao = j.optString("versao", ""),
                    notas = j.optString("notas", ""),
                    quando = j.optString("quando", ""),
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Baixa o APK. Vai para a pasta externa do proprio app porque, no Android 5.1, o instalador
     * e outro processo e nao consegue ler nada que esteja na area interna do app.
     */
    suspend fun baixar(ctx: Context, onProgresso: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val destino = File(ctx.getExternalFilesDir(null) ?: ctx.cacheDir, "luluco-reef.apk")
        val parcial = File(destino.parentFile, "luluco-reef.apk.part")
        val req = Request.Builder().url("${base()}/luluco-reef.apk?t=${System.currentTimeMillis()}").build()
        TrustedHttp.client(ctx).newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val corpo = resp.body ?: error("resposta vazia")
            val total = corpo.contentLength()
            parcial.outputStream().use { saida ->
                corpo.byteStream().use { entrada ->
                    val buf = ByteArray(64 * 1024)
                    var lidos = 0L
                    var ultimo = -1
                    while (true) {
                        val n = entrada.read(buf)
                        if (n < 0) break
                        saida.write(buf, 0, n)
                        lidos += n
                        if (total > 0) {
                            val pct = (lidos * 100 / total).toInt()
                            if (pct != ultimo) { ultimo = pct; onProgresso(pct) }
                        }
                    }
                }
            }
        }
        // So vira o arquivo final depois de baixar inteiro: APK pela metade abre o instalador e
        // falha com "pacote invalido", que parece defeito do app e nao download interrompido.
        if (destino.exists()) destino.delete()
        if (!parcial.renameTo(destino)) error("não deu para salvar o arquivo")
        destino.setReadable(true, false)
        destino
    }

    fun instalar(ctx: Context, apk: File) {
        val intent = Intent(Intent.ACTION_VIEW).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        val tipo = "application/vnd.android.package-archive"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.apk", apk)
            intent.setDataAndType(uri, tipo)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            intent.setDataAndType(Uri.fromFile(apk), tipo)
        }
        ctx.startActivity(intent)
    }
}
