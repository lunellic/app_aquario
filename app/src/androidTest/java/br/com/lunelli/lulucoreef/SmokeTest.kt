package br.com.lunelli.lulucoreef

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.lunelli.lulucoreef.data.Api
import br.com.lunelli.lulucoreef.data.Livestock
import br.com.lunelli.lulucoreef.data.ParamRange
import br.com.lunelli.lulucoreef.data.Product
import br.com.lunelli.lulucoreef.data.Routine
import br.com.lunelli.lulucoreef.data.TankConfig
import br.com.lunelli.lulucoreef.net.TrustedHttp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Roda no emulador API 22 do CI (mesmo Android do Fire HD 8). */
@RunWith(AndroidJUnit4::class)
class SmokeTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as App

    @Before fun clean() {
        Api.offlineForTests = true
        app.graph.session.clear()
        app.graph.db.clearAllTables()
    }

    /** Abre o menu lateral e escolhe um item. */
    private fun menu(label: String) {
        compose.onNodeWithContentDescription("Menu").performClick()
        compose.onNodeWithTag("menu_$label").performScrollTo().performClick()
    }

    @After fun restore() {
        Api.offlineForTests = false
    }

    /** O ponto mais arriscado do projeto: TLS com o Supabase a partir das raizes do Android 5.1 + embutidas. */
    @Test fun tlsComSupabaseValidaCertificado() {
        val req = Request.Builder().url(BuildConfig.SUPABASE_URL + "/auth/v1/health").header("apikey", BuildConfig.SUPABASE_KEY).build()
        TrustedHttp.client(app).newCall(req).execute().use { r ->
            assertEquals(200, r.code)
            assertEquals("TLSv1.2", r.handshake!!.tlsVersion.javaName)
        }
        // Registro do comportamento sem as raizes embutidas (no API 22 espera-se falha de certificado).
        val plain = try {
            OkHttpClient().newCall(req).execute().use { "HTTP ${it.code}" }
        } catch (e: Exception) {
            e.javaClass.simpleName
        }
        println("LULUCO sem raizes embutidas: $plain; raizes do sistema: ${TrustedHttp.systemRootCount}")
    }

    @Test fun semSessaoMostraLogin() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("Entrar").assertIsDisplayed()
            compose.onNodeWithText("E-mail").assertIsDisplayed()
        }
    }

    @Test fun fluxoPrincipalOffline() {
        val goni = Product("Goni Supreme", marca = "Shask", doseQuantidade = 1.0, doseUnidade = "colher", doseBaseLitros = 400.0, pending = false)
        runBlocking {
            val db = app.graph.db
            db.tankConfig().upsert(TankConfig())
            listOf(
                ParamRange("kh", 7.5, 9.0, 7.0, 10.0, "dKH"), ParamRange("ca", 400.0, 450.0, 380.0, 480.0, "ppm"),
                ParamRange("mg", 1250.0, 1400.0, 1200.0, 1450.0, "ppm"), ParamRange("salinidade", 1.024, 1.026, 1.023, 1.027, "SG"),
            ).forEach { db.paramRanges().upsert(it) }
            db.products().upsert(goni)
            db.routines().upsert(Routine("Goni Supreme", tipo = "dosagem", productId = goni.id, recorrencia = "diaria", horario = "00:01:00"))
            db.livestock().upsert(Livestock("Duncan", tipo = "coral", categoria = "LPS", revisar = true))
            db.livestock().upsert(Livestock("Palhaço", tipo = "peixe", quantidade = 2, revisar = true))
        }
        app.graph.session.save(JSONObject("""{"access_token":"x","refresh_token":"y","expires_in":999999,"user":{"id":"00000000-0000-0000-0000-000000000001","email":"teste@example.com"}}"""))

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitForIdle()

            // Painel: rotina do dia com a dose para 100 L
            compose.onNodeWithText("Hoje").assertIsDisplayed()
            compose.onNodeWithText("1/4 de colher").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Revise o inventário").performScrollTo().assertIsDisplayed()

            // Feito gera a dose no historico
            compose.onNodeWithText("Feito").performScrollTo().performClick()
            compose.waitUntil(10_000) { runBlocking { app.graph.db.dosings().all().first().size == 1 } }
            runBlocking {
                val doses = app.graph.db.dosings().all().first()
                assertEquals(1, doses.size)
                assertEquals(0.25, doses[0].quantidade!!, 1e-9)
                assertTrue(doses[0].pending)
            }

            // Registrar teste de KH
            compose.onNodeWithText("Nova medição").performScrollTo().performClick()
            compose.onNodeWithText("KH").performTextInput("8,2")
            compose.onNodeWithText("Salvar").performScrollTo().performClick()
            compose.waitUntil(10_000) { runBlocking { app.graph.db.waterTests().all().first().size == 1 } }

            // Historico unificado
            menu("Histórico")
            compose.onNodeWithText("KH: 8,2 dKH").assertIsDisplayed()
            compose.onNodeWithText("Goni Supreme: 1/4 de colher").assertIsDisplayed()

            // Meu Aquario
            menu("Meu Aquário")
            compose.onNodeWithText("Corais (1)").assertIsDisplayed()
            compose.onNodeWithText("2x Palhaço").assertIsDisplayed()
            compose.onAllNodesWithText("confirmar").onFirst().assertIsDisplayed()
            compose.onNodeWithText("Duncan").performClick()
            compose.onNodeWithText("Editar").assertIsDisplayed()
            compose.onNodeWithText("Gerar ficha (IA)").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Voltar").performScrollTo().performClick()

            // Graficos
            menu("Gráficos")
            compose.onNodeWithText("Parâmetros").assertIsDisplayed()
            compose.onNodeWithText("KH (dKH)").assertIsDisplayed()

            // Saude: abre caso e cai na ficha do caso
            menu("Saúde")
            compose.onNodeWithText("Novo caso").performClick()
            compose.onNodeWithText("Título (ex.: Duncan fechado)").performTextInput("Duncan fechado")
            compose.onNodeWithText("Salvar").performScrollTo().performClick()
            compose.onNodeWithText("Nova entrada").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Voltar").performScrollTo().performClick()
            compose.onNodeWithText("Casos abertos (1)").assertIsDisplayed()

            // Rotinas, Equipamentos e Ajustes abrem sem quebrar
            menu("Rotinas")
            compose.onNodeWithText("Produtos e Rotinas").assertIsDisplayed()
            menu("Equipamentos")
            compose.onNodeWithText("Nenhum equipamento cadastrado.").assertIsDisplayed()
            menu("Assistente")
            compose.onNodeWithText("Analisar meu aquário").assertIsDisplayed()
            menu("Ajustes")
            compose.onNodeWithText("Configurações").assertIsDisplayed()
            compose.onNodeWithText("teste@example.com").performScrollTo().assertIsDisplayed()

            // Sem rede a sessao continua valida (nao volta para o login)
            assertTrue(app.graph.session.loggedIn.value)
        }
    }

    /** Quem ja tem o app instalado (banco v1) atualiza sem perder dados: v1 -> v2 cria water_tests.baixaConfianca. */
    @Test fun migracaoDoBancoLocalPreservaDados() {
        val name = "migracao_teste.db"
        app.deleteDatabase(name)
        fun open() = androidx.room.Room.databaseBuilder(app, br.com.lunelli.lulucoreef.data.AppDb::class.java, name)
            .addMigrations(br.com.lunelli.lulucoreef.data.AppDb.MIGRATION_1_2).build()
        open().apply { openHelper.writableDatabase; close() }

        // volta o arquivo ao formato v1: water_tests sem a coluna nova, com uma medicao gravada
        val raw = android.database.sqlite.SQLiteDatabase.openDatabase(app.getDatabasePath(name).path, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE)
        val sql = raw.rawQuery("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'water_tests'", null).use { it.moveToFirst(); it.getString(0) }
        val v1 = sql.replace("`baixaConfianca` INTEGER NOT NULL DEFAULT 0, ", "")
        assertTrue("coluna nova nao encontrada em: $sql", v1 != sql)
        raw.execSQL("DROP TABLE water_tests")
        raw.execSQL(v1)
        raw.execSQL("INSERT INTO water_tests (medidoEm, parametro, valor, unidade, id, createdAt, updatedAt, pending) VALUES (1000, 'kh', 8.2, 'dKH', 'abc', 1000, 1000, 1)")
        raw.version = 1
        raw.close()

        val db = open()
        runBlocking {
            val t = db.waterTests().all().first().single()
            assertEquals(8.2, t.valor, 1e-9)
            assertEquals(false, t.baixaConfianca)
            assertTrue(t.pending)
            db.waterTests().upsert(t.copy(baixaConfianca = true))
            assertTrue(db.waterTests().get("abc")!!.baixaConfianca)
        }
        db.close()
        app.deleteDatabase(name)
    }

    /** Tira uma captura da tela e a escreve no logcat (base64) para conferencia visual a partir do log do CI. */
    private fun shot(name: String) {
        compose.waitForIdle()
        Thread.sleep(1200) // deixa as animacoes de entrada terminarem
        val full = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val w = 800
        val bmp = android.graphics.Bitmap.createScaledBitmap(full, w, full.height * w / full.width, true)
        val out = java.io.ByteArrayOutputStream()
        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 72, out)
        val b64 = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
        b64.chunked(3000).forEachIndexed { i, part -> android.util.Log.i("LULUCO_SHOT", "$name ${i.toString().padStart(3, '0')} $part") }
        android.util.Log.i("LULUCO_SHOT", "$name FIM")
    }

    /** Dados parecidos com os reais do Lucas, para ver as telas como elas vao aparecer no tablet. */
    @Test fun capturasDeTela() {
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        runBlocking {
            val db = app.graph.db
            db.tankConfig().upsert(TankConfig(volumeDosagemL = 125.0, salAtual = "Blue Treasure Reef Salt"))
            listOf(
                ParamRange("kh", 7.5, 9.0, 7.0, 10.0, "dKH"), ParamRange("ca", 400.0, 450.0, 380.0, 480.0, "ppm"),
                ParamRange("mg", 1250.0, 1400.0, 1200.0, 1450.0, "ppm"), ParamRange("salinidade", 1.024, 1.026, 1.023, 1.027, "SG"),
            ).forEach { db.paramRanges().upsert(it) }
            val goni = Product("Goni Supreme", marca = "Shask", doseQuantidade = 1.0, doseUnidade = "colher", doseBaseLitros = 400.0)
            val amino = Product("Amino Coral", marca = "Shask", doseQuantidade = 2.0, doseUnidade = "gotas", doseBaseLitros = 100.0)
            db.products().upsert(goni); db.products().upsert(amino)
            db.routines().upsert(Routine("Goni Supreme", tipo = "dosagem", productId = goni.id, recorrencia = "diaria", horario = "00:01:00"))
            db.routines().upsert(Routine("Amino Coral", tipo = "dosagem", productId = amino.id, recorrencia = "diaria", horario = "23:58:00"))
            fun t(p: String, v: Double, d: Double, low: Boolean = false) = br.com.lunelli.lulucoreef.data.WaterTest((now - d * day).toLong(), p, v, "x", baixaConfianca = low)
            listOf(t("kh", 7.8, 21.0), t("kh", 7.9, 17.0), t("kh", 8.4, 12.0, true), t("kh", 8.2, 10.0, true), t("kh", 7.7, 9.0), t("kh", 7.7, 7.0), t("kh", 7.6, 6.0), t("kh", 7.85, 0.5),
                t("ca", 550.0, 17.0), t("ca", 500.0, 12.0), t("ca", 440.0, 0.5), t("mg", 1140.0, 21.0), t("mg", 1200.0, 12.0), t("mg", 1290.0, 0.5),
                t("salinidade", 1.0225, 17.0), t("salinidade", 1.025, 16.0), t("salinidade", 1.026, 12.0), t("salinidade", 1.025, 0.5),
            ).forEach { db.waterTests().upsert(it) }
            db.waterChanges().upsert(br.com.lunelli.lulucoreef.data.WaterChange(now - 3 * day, 14.5, sal = "Blue Treasure Reef Salt"))
            listOf("Torch Holy Grail" to "LPS", "Hammer Gold" to "LPS", "Duncan" to "LPS", "Zoanthus Rasta" to "zoa", "Montipora setosa" to "SPS").forEach { (n, c) ->
                db.livestock().upsert(Livestock(n, tipo = "coral", categoria = c, quantidade = if (n == "Hammer Gold") 4 else 1))
            }
            db.livestock().upsert(Livestock("Ocellaris Chocolate", tipo = "peixe", nomeCientifico = "Amphiprion ocellaris"))
        }
        // vitrine em 1 min; modo noite desligado (inicio = fim) para a captura nao depender da hora do emulador
        app.graph.settings.update { it.copy(vitrineMinutes = 1, nightStartHour = 0, nightEndHour = 0) }
        app.graph.session.save(JSONObject("""{"access_token":"x","refresh_token":"y","expires_in":999999,"user":{"id":"00000000-0000-0000-0000-000000000001","email":"teste@example.com"}}"""))

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            Thread.sleep(2500)
            shot("painel")
            compose.onNodeWithContentDescription("Menu").performClick()
            shot("menu")
            compose.onNodeWithTag("menu_Gráficos").performScrollTo().performClick()
            shot("graficos")
            menu("Meu Aquário")
            shot("aquario")
            menu("Painel")
            compose.onNodeWithText("Nova medição").performScrollTo().performClick()
            shot("teste")
            compose.onNodeWithText("Voltar").performScrollTo().performClick()
            // vitrine entra sozinha depois de 1 minuto sem toque
            // (no teste o relogio das corrotinas e virtual: espera o tempo real e depois avanca o relogio para o app notar)
            Thread.sleep(63_000)
            compose.mainClock.advanceTimeBy(11_000)
            shot("vitrine")
        }
        app.graph.settings.update { it.copy(vitrineMinutes = 2, nightStartHour = 0, nightEndHour = 7) }
    }
}
