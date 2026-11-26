package br.com.lunelli.lulucoreef.diag

import android.content.Context
import java.io.File

/**
 * Registro de queda do app.
 *
 * Quando o app fecha sozinho no tablet, nao ha como saber por que: o Fire nao mostra nada e o
 * computador nao esta ligado nele. Este registro grava o erro num arquivo no momento da queda, e
 * na proxima abertura o Painel mostra o que aconteceu. Com isso um "o app simplesmente fecha"
 * vira uma linha de erro que da para corrigir.
 */
object Queda {
    private fun arquivo(ctx: Context) = File(ctx.filesDir, "ultima_queda.txt")

    fun instalar(ctx: Context) {
        val app = ctx.applicationContext
        val anterior = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, erro ->
            runCatching { Diario.registrar("queda", "thread ${thread.name}\n" + android.util.Log.getStackTraceString(erro)) }
            runCatching {
                val pilha = android.util.Log.getStackTraceString(erro)
                arquivo(app).writeText(
                    "${java.text.SimpleDateFormat("dd/MM HH:mm:ss", java.util.Locale("pt", "BR")).format(java.util.Date())} " +
                        "· versão ${br.com.lunelli.lulucoreef.BuildConfig.VERSION_NAME} · thread ${thread.name}\n$pilha",
                )
            }
            anterior?.uncaughtException(thread, erro)
        }
    }

    /** O que foi gravado na ultima queda, ou nulo. */
    fun ultima(ctx: Context): String? = arquivo(ctx).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }

    fun limpar(ctx: Context) { arquivo(ctx).delete() }
}
