package br.com.lunelli.lulucoreef.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import br.com.lunelli.lulucoreef.BuildConfig
import br.com.lunelli.lulucoreef.net.TrustedHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
data class VersaoRemota(
    val build: Int, val versao: String, val notas: String, val quando: String,
    val apk: String? = null, val tamanho: Long? = null, val sha256: String? = null,
) {
    val nova: Boolean get() = build > BuildConfig.VERSION_CODE || Atualizacao.ignorarVersaoParaTeste
}

object Atualizacao {
    private const val PASTA = "app"

    /**
     * So para o teste no emulador: deixa baixar e abrir o instalador com a mesma versao que esta
     * instalada, para o fluxo inteiro (aviso, toque, download, conferencia, instalador) rodar no
     * CI sem precisar de um APK mais novo que o proprio teste.
     */
    @Volatile var ignorarVersaoParaTeste = false

    private fun log(msg: String) { android.util.Log.i("LULUCO_UPD", msg) }
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
                    apk = j.optString("apk", "").ifBlank { null },
                    tamanho = j.optLong("tamanho", 0L).takeIf { it > 0 },
                    sha256 = j.optString("sha256", "").ifBlank { null },
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Estado do download, fora de qualquer tela. */
    sealed interface Estado {
        data object Parado : Estado
        data class Baixando(val pct: Int, val tentativa: Int) : Estado
        data class Falhou(val motivo: String) : Estado
        data object Pronto : Estado
    }

    private val _estado = object {
        val fluxo = kotlinx.coroutines.flow.MutableStateFlow<Estado>(Estado.Parado)
        var value: Estado
            get() = fluxo.value
            set(v) { if (v !is Estado.Baixando || v.pct % 10 == 0) log("estado: $v"); fluxo.value = v }
    }
    val estado: kotlinx.coroutines.flow.StateFlow<Estado> = _estado.fluxo

    /** O descanso le isto para nao entrar no meio de um download. */
    val baixando: Boolean get() = _estado.value is Estado.Baixando

    /*
     * O download vive num escopo do processo, e nao da tela. Antes ele corria no escopo do aviso
     * do Painel: com o download lento, os dois minutos sem toque passavam, o tablet ia para o
     * descanso, o Painel saia da tela e levava o download junto, calado. Voltando ao Painel o
     * aviso aparecia de novo do zero. Era o "baixa ate 34% e some".
     */
    private val escopo = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    private var tarefa: kotlinx.coroutines.Job? = null

    fun iniciar(ctx: Context, v: VersaoRemota) {
        if (tarefa?.isActive == true) return
        val app = ctx.applicationContext
        _estado.value = Estado.Baixando(0, 1)
        tarefa = escopo.launch {
            var ultimoErro = ""
            for (tentativa in 1..TENTATIVAS) {
                try {
                    val apk = baixar(app, tentativa, v)
                    conferir(app, apk, v)
                    _estado.value = Estado.Pronto
                    kotlinx.coroutines.withContext(Dispatchers.Main) { instalar(app, apk) }
                    return@launch
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log("tentativa $tentativa falhou: ${android.util.Log.getStackTraceString(e).take(600)}")
                    ultimoErro = e.message ?: e.javaClass.simpleName
                    TrustedHttp.client(app).connectionPool.evictAll()
                    if (tentativa < TENTATIVAS) kotlinx.coroutines.delay(3_000L * tentativa)
                }
            }
            _estado.value = Estado.Falhou(
                "Parou $TENTATIVAS vezes seguidas ($ultimoErro). O que já veio fica guardado: tocar de novo continua de onde parou.",
            )
        }
    }

    private const val TENTATIVAS = 4

    /**
     * Baixa o APK, CONTINUANDO de onde parou. Com o Wi-Fi do Fire caindo no meio, recomecar do zero
     * a cada tentativa podia nunca terminar; pedindo so os bytes que faltam (cabecalho Range), cada
     * tentativa soma em cima da anterior.
     *
     * Vai para a pasta externa do proprio app porque, no Android 5.1, o instalador e outro processo
     * e nao consegue ler nada que esteja na area interna do app.
     */
    private fun baixar(ctx: Context, tentativa: Int, v: VersaoRemota): File {
        val build = v.build
        val pasta = ctx.getExternalFilesDir(null) ?: ctx.cacheDir
        val destino = File(pasta, "luluco-reef.apk")
        // O pedaco leva o numero do build no nome. Se saiu versao nova no meio de um download
        // interrompido, continuar o arquivo velho com o fim do novo daria um APK de dois pais.
        val parcial = File(pasta, "luluco-reef-$build.apk.part")
        pasta.listFiles { f -> f.name.endsWith(".apk.part") && f.name != parcial.name }?.forEach { it.delete() }
        val jaTenho = if (parcial.exists()) parcial.length() else 0L

        // O arquivo com o numero do build no nome nunca muda, entao o cache do CDN nao tem como
        // entregar outra versao, e continuar de onde parou e seguro. O de nome fixo so fica de
        // reserva para um versao.json antigo, sem o campo, e ai vai com o cache-buster.
        val url = v.apk?.let { "${base()}/$it" } ?: "${base()}/luluco-reef.apk?t=${System.currentTimeMillis() / 60000}"
        if (v.apk == null && parcial.exists()) parcial.delete()
        val req = Request.Builder()
            .url(url)
            .apply { if (jaTenho > 0) header("Range", "bytes=$jaTenho-") }
            .build()
        TrustedHttp.client(ctx).newCall(req).execute().use { resp ->
            // 416: pedi a partir do fim, ou seja, o pedaco ja estava completo
            if (resp.code == 416 && jaTenho > 0) {
                if (destino.exists()) destino.delete()
                if (!parcial.renameTo(destino)) error("não deu para salvar o arquivo")
                destino.setReadable(true, false)
                return destino
            }
            val continuando = resp.code == 206
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val corpo = resp.body ?: error("resposta vazia")
            // 200 com Range pedido quer dizer que o servidor ignorou o pedido e mandou tudo:
            // comeca do zero, senao o arquivo sai com o comeco duplicado.
            val base = if (continuando) jaTenho else 0L
            val total = if (corpo.contentLength() > 0) base + corpo.contentLength() else -1L
            java.io.FileOutputStream(parcial, continuando).use { saida ->
                corpo.byteStream().use { entrada ->
                    val buf = ByteArray(64 * 1024)
                    var lidos = base
                    var ultimo = -1
                    while (true) {
                        val n = entrada.read(buf)
                        if (n < 0) break
                        saida.write(buf, 0, n)
                        lidos += n
                        val pct = if (total > 0) (lidos * 100 / total).toInt() else 0
                        if (pct != ultimo) { ultimo = pct; _estado.value = Estado.Baixando(pct, tentativa) }
                    }
                    if (total > 0 && lidos < total) error("conexão caiu em ${lidos * 100 / total}%")
                }
            }
        }
        // So vira o arquivo final depois de baixar inteiro: APK pela metade abre o instalador e
        // falha com "pacote invalido", que parece defeito do app e nao download interrompido.
        if (destino.exists()) destino.delete()
        if (!parcial.renameTo(destino)) error("não deu para salvar o arquivo")
        destino.setReadable(true, false)
        return destino
    }

    /**
     * Confere o arquivo ANTES de abrir o instalador. Foi o que faltou: com o CDN servindo o APK
     * antigo, o app baixava em dois segundos, abria o instalador, e ele reinstalava a mesma
     * versao. Parecia que tinha atualizado e o aviso voltava. Agora tamanho, assinatura do
     * conteudo e numero de versao de dentro do APK tem que bater, senao para e diz o que veio.
     */
    private fun conferir(ctx: Context, apk: File, v: VersaoRemota) {
        fun descarta(motivo: String): Nothing { apk.delete(); error(motivo) }
        if (v.tamanho != null && apk.length() != v.tamanho) descarta("o arquivo veio com ${apk.length()} bytes e devia ter ${v.tamanho}")
        if (v.sha256 != null) {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            apk.inputStream().use { inp -> val b = ByteArray(64 * 1024); while (true) { val n = inp.read(b); if (n < 0) break; md.update(b, 0, n) } }
            val hex = md.digest().joinToString("") { "%02x".format(it) }
            if (!hex.equals(v.sha256, ignoreCase = true)) descarta("o arquivo baixado não confere com o publicado")
        }
        @Suppress("DEPRECATION")
        val info = ctx.packageManager.getPackageArchiveInfo(apk.absolutePath, 0) ?: descarta("o arquivo baixado não é um APK válido")
        @Suppress("DEPRECATION")
        val codigo = info.versionCode
        if (codigo != v.build) descarta("veio a versão 0.0.$codigo em vez da ${v.versao}")
        if (codigo <= BuildConfig.VERSION_CODE && !ignorarVersaoParaTeste) descarta("o arquivo é a mesma versão que já está instalada")
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
        log("abrindo o instalador: ${intent.data}")
        ctx.startActivity(intent)
    }
}
