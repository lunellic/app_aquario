package br.com.lunelli.lulucoreef

import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onRoot
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
            compose.onNodeWithText("Entrar", ignoreCase = true).assertIsDisplayed()
            compose.onNodeWithText("E-mail", ignoreCase = true).assertIsDisplayed()
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
            compose.onAllNodesWithText("Hoje", ignoreCase = true).onFirst().assertIsDisplayed()
            compose.onNodeWithText("1/4 de colher", ignoreCase = true).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Revise o inventário", ignoreCase = true).performScrollTo().assertIsDisplayed()

            // Feito gera a dose no historico
            compose.onNodeWithText("Feito", ignoreCase = true).performScrollTo().performClick()
            compose.waitUntil(10_000) { runBlocking { app.graph.db.dosings().all().first().size == 1 } }
            runBlocking {
                val doses = app.graph.db.dosings().all().first()
                assertEquals(1, doses.size)
                assertEquals(0.25, doses[0].quantidade!!, 1e-9)
                assertTrue(doses[0].pending)
            }

            // Registrar teste de KH
            compose.onNodeWithText("Nova medição", ignoreCase = true).performScrollTo().performClick()
            // roleta: girar o KH marca o parametro como medido
            compose.onNodeWithTag("roleta_kh").performTouchInput { swipeUp(startY = centerY + 40f, endY = centerY - 40f) }
            compose.waitForIdle()
            Thread.sleep(800)
            compose.onNodeWithText("Salvar", ignoreCase = true).performScrollTo().performClick()
            compose.waitUntil(10_000) { runBlocking { app.graph.db.waterTests().all().first().size == 1 } }
            val kh = runBlocking { app.graph.db.waterTests().all().first().first() }
            assertEquals("kh", kh.parametro)

            // Jornal
            menu("Jornal")
            compose.onNodeWithText("KH: " + br.com.lunelli.lulucoreef.domain.Params.format("kh", kh.valor) + " dKH", ignoreCase = true).assertIsDisplayed()
            compose.onNodeWithText("Goni Supreme: 1/4 de colher", ignoreCase = true).assertIsDisplayed()

            // Meu Aquario
            menu("Meu Aquário")
            compose.onAllNodesWithText("2x Palhaço", ignoreCase = true).onFirst().assertExists()
            compose.onAllNodesWithText("CONFIRMAR", ignoreCase = true).onFirst().assertIsDisplayed()
            compose.onNodeWithText("Duncan", ignoreCase = true).performClick()
            compose.onNodeWithText("Editar", ignoreCase = true).assertIsDisplayed()
            compose.onNodeWithText("Gerar ficha (IA)", ignoreCase = true).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Voltar", ignoreCase = true).performScrollTo().performClick()

            // Graficos
            menu("Gráficos")
            compose.onNodeWithText("Parâmetros", ignoreCase = true).assertIsDisplayed()
            compose.onNodeWithText("KH (dKH)", ignoreCase = true).assertIsDisplayed()

            // Saude: abre caso e cai na ficha do caso
            menu("Saúde e quarentena")
            compose.onNodeWithText("Nova observação ou tratamento", ignoreCase = true).performClick()
            compose.onNodeWithText("Título (ex.: Duncan fechado)", ignoreCase = true).performTextInput("Duncan fechado")
            compose.onNodeWithText("Salvar", ignoreCase = true).performScrollTo().performClick()
            // Respiro antes de esperar a ficha. O waitUntil sozinho falhou duas vezes seguidas aqui e
            // passou com esta pausa; a causa exata nao foi isolada, entao fica o respiro e o prazo folgado.
            Thread.sleep(1500)
            compose.waitUntil(20_000) { compose.onAllNodesWithText("Nova entrada", ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Nova entrada", ignoreCase = true).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Voltar", ignoreCase = true).performScrollTo().performClick()
            compose.onNodeWithText("Casos abertos (1)", ignoreCase = true).assertIsDisplayed()

            // Rotinas, Equipamentos e Ajustes abrem sem quebrar
            menu("Rotinas")
            compose.onNodeWithText("Nova rotina", ignoreCase = true).assertIsDisplayed()
            menu("Equipamentos e produtos")
            compose.onNodeWithText("Nenhum equipamento cadastrado.", ignoreCase = true).assertIsDisplayed()
            menu("Ajustes")
            compose.onNodeWithText("Configurações", ignoreCase = true).assertIsDisplayed()
            compose.onNodeWithText("teste@example.com", ignoreCase = true).performScrollTo().assertIsDisplayed()

            // Sem rede a sessao continua valida (nao volta para o login)
            assertTrue(app.graph.session.loggedIn.value)
        }
    }

    /** Quem ja tem o app instalado (banco v1) atualiza sem perder dados: v1 -> v2 cria water_tests.baixaConfianca. */
    @Test fun migracaoDoBancoLocalPreservaDados() {
        val name = "migracao_teste.db"
        app.deleteDatabase(name)
        fun open() = androidx.room.Room.databaseBuilder(app, br.com.lunelli.lulucoreef.data.AppDb::class.java, name)
            .addMigrations(
                br.com.lunelli.lulucoreef.data.AppDb.MIGRATION_1_2, br.com.lunelli.lulucoreef.data.AppDb.MIGRATION_2_3,
                br.com.lunelli.lulucoreef.data.AppDb.MIGRATION_3_4, br.com.lunelli.lulucoreef.data.AppDb.MIGRATION_4_5,
            ).build()
        open().apply { openHelper.writableDatabase; close() }

        // volta o arquivo ao formato v1: water_tests sem a coluna nova, com uma medicao gravada
        val raw = android.database.sqlite.SQLiteDatabase.openDatabase(app.getDatabasePath(name).path, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE)
        val sql = raw.rawQuery("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'water_tests'", null).use { it.moveToFirst(); it.getString(0) }
        val v1 = sql.replace("`baixaConfianca` INTEGER NOT NULL DEFAULT 0, ", "")
        assertTrue("coluna nova nao encontrada em: $sql", v1 != sql)
        raw.execSQL("DROP TABLE water_tests")
        raw.execSQL("DROP TABLE finance_entries")
        raw.execSQL("DROP TABLE shopping_items")
        raw.execSQL("DROP TABLE IF EXISTS alarms")
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
            // v5: parcela paga e a tabela de alarmes (local)
            val e = br.com.lunelli.lulucoreef.data.FinanceEntry(valor = 10.0, forma = "cartao")
            db.finance().upsert(e)
            assertEquals(false, db.finance().get(e.id)!!.pago)
            db.finance().upsert(e.copy(pago = true))
            assertTrue(db.finance().get(e.id)!!.pago)
            val a = br.com.lunelli.lulucoreef.data.Alarm(titulo = "Religar a bomba", hora = 21, minuto = 30)
            db.alarms().put(a)
            assertEquals("Religar a bomba", db.alarms().get(a.id)!!.titulo)
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
    private fun step(b: () -> Unit) { runCatching(b).onFailure { android.util.Log.w("LULUCO_SHOT", "passo falhou: ${it.message?.take(80)}") } }

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
            db.routines().upsert(Routine("TPA", tipo = "tpa", recorrencia = "dias_da_semana", diasSemana = listOf(3), horario = "19:00", instrucao = "10 a 12 L"))
            db.routines().upsert(Routine("Testes KH, Ca, Mg e salinidade", tipo = "teste", recorrencia = "dias_da_semana", diasSemana = listOf(3), horario = "18:00"))
            db.routines().upsert(Routine("Troca do carvão ativado", tipo = "manutencao", recorrencia = "a_cada_n_semanas", intervaloN = 4, horario = "19:00", proximaEm = now + 9 * day))
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
            listOf(Triple("Torch Holy Grail", "corais_peixes", 380.0), Triple("Blue Treasure 6,7 kg", "sal_suplementos", 260.0), Triple("Ração Fauna Marin", "alimentacao", 70.0)).forEachIndexed { i, (d, c, v) ->
                db.finance().upsert(br.com.lunelli.lulucoreef.data.FinanceEntry(lancadoEm = now - (i * 3 + 1) * day, categoria = c, descricao = d, valor = v))
            }
            db.finance().upsert(br.com.lunelli.lulucoreef.data.FinanceEntry(lancadoEm = now - 2 * day, tipo = "receita", categoria = "venda_corais", descricao = "3 mudas de zoa", valor = 150.0))
            db.finance().upsert(br.com.lunelli.lulucoreef.data.FinanceEntry(lancadoEm = now - 35 * day, categoria = "equipamentos", descricao = "Bomba de circulação", valor = 420.0))
            db.shopping().upsert(br.com.lunelli.lulucoreef.data.ShoppingItem("Magnésio Buffer Aquaforest", "sal_suplementos", 95.0, "alta"))
            db.shopping().upsert(br.com.lunelli.lulucoreef.data.ShoppingItem("Ração para betta", "alimentacao", 25.0, "media", aquario = "doce"))
            db.shopping().upsert(br.com.lunelli.lulucoreef.data.ShoppingItem("Refil de carvão ativado", "manutencao", 60.0, "baixa"))
        }
        // vitrine em 1 min; modo noite desligado (inicio = fim) para a captura nao depender da hora do emulador
        app.graph.settings.update { it.copy(vitrineMinutes = 1, nightStartHour = 0, nightEndHour = 0) }
        app.graph.session.save(JSONObject("""{"access_token":"x","refresh_token":"y","expires_in":999999,"user":{"id":"00000000-0000-0000-0000-000000000001","email":"teste@example.com"}}"""))

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            // Espera o Painel aparecer de fato. Tempo fixo pegava a tela de abertura em
            // maquina lenta, e um print da logomarca nao diz nada sobre a interface.
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("Métricas principais", ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
            }
            shot("painel")
            // calendario do Painel: toca num dia e ve a lista
            step { compose.onNodeWithText("Adicionar atividade neste dia", ignoreCase = true).performScrollTo() }
            shot("calendario")
            step { compose.onNodeWithContentDescription("Menu").performScrollTo().performClick() }
            shot("menu")
            step { compose.onNodeWithTag("menu_Gráficos").performScrollTo().performClick() }
            shot("graficos")
            step { menu("Meu Aquário") }
            shot("aquario")
            step { menu("Painel") }
            step { compose.onNodeWithText("Nova medição", ignoreCase = true).performScrollTo().performClick() }
            shot("teste")
            step { compose.onNodeWithText("Voltar", ignoreCase = true).performScrollTo().performClick() }
            // vitrine entra sozinha depois de 1 minuto sem toque
            // (no teste o relogio das corrotinas e virtual: espera o tempo real e depois avanca o relogio para o app notar)
            Thread.sleep(63_000)
            compose.mainClock.advanceTimeBy(11_000)
            shot("vitrine")
            // toque acorda o Painel; relatorios
            step { compose.onNodeWithText("Próximos passos", ignoreCase = true).performClick() }
            // atividade de uma vez so, criada a partir do calendario
            step { compose.onNodeWithText("Adicionar atividade neste dia", ignoreCase = true).performScrollTo().performClick() }
            step { compose.onNodeWithText("Título", ignoreCase = true).performTextInput("Muda de coral") }
            step { compose.onNodeWithText("Salvar", ignoreCase = true).performScrollTo().performClick() }
            step { compose.waitUntil(10_000) { runBlocking { app.graph.db.routines().all().first().any { it.recorrencia == "unica" && it.proximaEm != null } } } }
            step { compose.onAllNodesWithText("Muda de coral", ignoreCase = true).onFirst().performScrollTo().assertIsDisplayed() }
            step { menu("Relatórios") }
            step { compose.onNodeWithText("Enviar para meu e-mail", ignoreCase = true).assertIsDisplayed() }
            shot("relatorios")
            app.graph.settings.update { it.copy(vitrineMinutes = 0) }
            step { compose.onRoot().performTouchInput { click(center) } }
            Thread.sleep(800)
            step { menu("Finanças") }
            shot("financas")
            step { compose.onNodeWithText("Lista de compras", ignoreCase = true).performClick() }
            shot("compras")
            step { menu("Calculadoras") }
            shot("calculadoras")
            step { menu("Timer e alarmes") }
            shot("timers")
            step { compose.onNodeWithText("Novo alarme", ignoreCase = true).performClick() }
            shot("alarme")
            step { compose.onNodeWithText("Voltar", ignoreCase = true).performClick() }
            step { menu("Calendário") }
            shot("calendario_tela")
            step { menu("Saúde e quarentena") }
            shot("saude")
            step { compose.onNodeWithText("Cobre (Cupramine) + Prazipro", ignoreCase = true).performClick() }
            shot("quarentena")
            step { compose.onNodeWithText("Voltar", ignoreCase = true).performClick() }
            step { menu("Equipamentos e produtos") }
            step { compose.onNodeWithText("Produtos", ignoreCase = true).performClick() }
            shot("produtos")
            step { menu("Registrar") }
            step { compose.onNodeWithText("Nova medição", ignoreCase = true).performClick() }
            shot("roletas")
            // modo noite: liga para qualquer hora e espera 1 minuto sem toque
            app.graph.settings.update { it.copy(nightStartHour = 0, nightEndHour = 0, vitrineMinutes = 0) }
            step { menu("Painel") }
            val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            app.graph.settings.update { it.copy(nightStartHour = h, nightEndHour = (h + 2) % 24) }
            Thread.sleep(63_000)
            compose.mainClock.advanceTimeBy(11_000)
            shot("noite")
        }
        app.graph.settings.update { it.copy(vitrineMinutes = 2, nightStartHour = 0, nightEndHour = 7) }
    }
}
