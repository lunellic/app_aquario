package br.com.lunelli.lulucoreef

import android.content.pm.ActivityInfo
import android.util.Base64
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.lunelli.lulucoreef.data.Api
import br.com.lunelli.lulucoreef.data.TankConfig
import br.com.lunelli.lulucoreef.update.Atualizacao
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/**
 * O fluxo de atualizacao inteiro, no emulador API 22 (o Android do Fire), contra o Storage de
 * verdade: o aviso aparece no Painel, o toque em "Atualizar" baixa o APK publicado, confere e abre
 * o instalador do sistema. Se o app cair em qualquer ponto, o teste cai junto e o log mostra onde.
 *
 * Como o APK publicado e da mesma versao (ou anterior) da que esta rodando no teste, a trava de
 * versao e desligada so aqui. O instalador vai recusar no final (assinatura de debug x release),
 * e tudo bem: o que interessa e o app chegar ate ele sem cair.
 */
@RunWith(AndroidJUnit4::class)
class AtualizacaoTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as App

    @Before fun preparar() {
        Api.offlineForTests = true
        Atualizacao.ignorarVersaoParaTeste = true
        app.graph.session.clear()
        app.graph.db.clearAllTables()
    }

    @After fun limpar() { Atualizacao.ignorarVersaoParaTeste = false }

    private fun shot(nome: String) {
        Thread.sleep(800)
        val full = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val out = java.io.ByteArrayOutputStream()
        full.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        val b64 = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
        android.util.Log.i("LULUCO_SHOT", "PNG75 $nome INICIO ${full.width}x${full.height} ${out.size()}")
        b64.chunked(3000).forEachIndexed { i, part -> android.util.Log.i("LULUCO_SHOT", "PNG75 $nome ${i.toString().padStart(4, '0')} $part") }
        android.util.Log.i("LULUCO_SHOT", "PNG75 $nome FIM")
    }
    }

    @Test fun tocarEmAtualizarChegaAoInstaladorSemDerrubarOApp() {
        runBlocking { app.graph.db.tankConfig().upsert(TankConfig(volumeDosagemL = 125.0)) }
        app.graph.settings.update { it.copy(vitrineMinutes = 0, nightStartHour = 0, nightEndHour = 0) }
        app.graph.session.save(JSONObject("""{"access_token":"x","refresh_token":"y","expires_in":999999,"user":{"id":"00000000-0000-0000-0000-000000000001","email":"teste@example.com"}}"""))

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitUntil(30_000) { compose.onAllNodesWithTag("splash").fetchSemanticsNodes().isEmpty() }
            // o aviso depende de ler o versao.json na rede: da tempo
            compose.waitUntil(45_000) { compose.onAllNodesWithText("Atualizar").fetchSemanticsNodes().isNotEmpty() }
            shot("upd_aviso")
            compose.onNodeWithText("Atualizar").performClick()

            // acompanha ate terminar, fotografando o meio do caminho
            val inicio = System.currentTimeMillis()
            var fotografouMeio = false
            while (System.currentTimeMillis() - inicio < 180_000) {
                val e = Atualizacao.estado.value
                if (e is Atualizacao.Estado.Baixando && e.pct in 20..80 && !fotografouMeio) { shot("upd_baixando"); fotografouMeio = true }
                if (e is Atualizacao.Estado.Pronto || e is Atualizacao.Estado.Falhou) break
                Thread.sleep(200)
            }
            val fim = Atualizacao.estado.value
            android.util.Log.i("LULUCO_UPD", "teste terminou em: $fim")
            Thread.sleep(4_000) // instalador abrindo
            shot("upd_instalador")
            if (fim is Atualizacao.Estado.Falhou) fail("download falhou: ${fim.motivo}")
            assertTrue("nao terminou em 3 min: $fim", fim is Atualizacao.Estado.Pronto)
            // de volta ao app: ele tem que continuar vivo
            InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            Thread.sleep(2_000)
            shot("upd_depois")
        }
    }
}
