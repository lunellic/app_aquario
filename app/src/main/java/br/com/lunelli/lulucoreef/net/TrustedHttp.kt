package br.com.lunelli.lulucoreef.net

import android.content.Context
import br.com.lunelli.lulucoreef.R
import okhttp3.OkHttpClient
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * OkHttp que confia nas raizes do sistema + raizes embutidas em res/raw.
 * O Android 5.1 (Fire OS 5) nao conhece GTS Root R1/R3/R4 nem ISRG Root X1/X2,
 * usadas hoje pelo Supabase (Cloudflare/Google Trust Services) e pelo Let's Encrypt.
 * A validacao de certificado continua completa: so ampliamos o conjunto de ancoras.
 */
object TrustedHttp {
    private val embeddedRoots = intArrayOf(
        R.raw.gts_root_r1, R.raw.gts_root_r3, R.raw.gts_root_r4,
        R.raw.isrg_root_x1, R.raw.isrg_root_x2,
    )

    @Volatile private var cached: OkHttpClient? = null
    @Volatile var systemRootCount = 0; private set
    @Volatile var embeddedSubjects: Set<String> = emptySet(); private set

    fun client(context: Context): OkHttpClient =
        cached ?: synchronized(this) { cached ?: build(context.applicationContext).also { cached = it } }

    private fun build(context: Context): OkHttpClient {
        val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }

        var sys = 0
        try {
            val system = KeyStore.getInstance("AndroidCAStore").apply { load(null) }
            val aliases = system.aliases()
            while (aliases.hasMoreElements()) {
                val alias = aliases.nextElement()
                if (!alias.startsWith("system:")) continue
                val cert = system.getCertificate(alias) ?: continue
                store.setCertificateEntry("sys_$alias", cert)
                sys++
            }
        } catch (_: Exception) {
            // Sem acesso ao repositorio do sistema: seguimos so com as embutidas.
        }
        systemRootCount = sys

        val factory = CertificateFactory.getInstance("X.509")
        val subjects = HashSet<String>()
        embeddedRoots.forEachIndexed { i, resId ->
            context.resources.openRawResource(resId).use { input ->
                val cert = factory.generateCertificate(input) as X509Certificate
                store.setCertificateEntry("embedded_$i", cert)
                subjects += cert.subjectX500Principal.name
            }
        }
        embeddedSubjects = subjects

        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(store)
        val trustManager = tmf.trustManagers.first { it is X509TrustManager } as X509TrustManager

        val ssl = SSLContext.getInstance("TLSv1.2")
        ssl.init(null, arrayOf(trustManager), null)

        return OkHttpClient.Builder()
            .sslSocketFactory(ssl.socketFactory, trustManager)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
