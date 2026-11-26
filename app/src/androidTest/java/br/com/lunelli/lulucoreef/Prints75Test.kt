package br.com.lunelli.lulucoreef

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.lunelli.lulucoreef.data.Api
import br.com.lunelli.lulucoreef.data.Livestock
import br.com.lunelli.lulucoreef.data.ParamRange
import br.com.lunelli.lulucoreef.data.Product
import br.com.lunelli.lulucoreef.data.Routine
import br.com.lunelli.lulucoreef.data.TankConfig
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * So para o post 2 do LinkedIn: capturas em PNG, resolucao cheia, de tres telas da build 75
 * (calculadora de correcao, teste de agua com as roletas, quarentena). Codigo do app intocado:
 * este arquivo e o unico acrescimo, e o ramo nao volta para o main.
 * Os dados de demonstracao sao os mesmos do capturasDeTela da propria build 75.
 */
@RunWith(AndroidJUnit4::class)
class Prints75Test {
    @get:Rule val compose = createEmptyComposeRule()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as App

    @Before fun clean() {
        Api.offlineForTests = true
        app.graph.session.clear()
        app.graph.db.clearAllTables()
    }

    @After fun restore() { Api.offlineForTests = false }

    private fun menu(label: String) {
        // o botao do menu fica no cabecalho, que rola junto com a tela: traz de volta antes de tocar
        runCatching { compose.onNodeWithContentDescription("Menu").performScrollTo() }
        compose.onNodeWithContentDescription("Menu").performClick()
        compose.onNodeWithTag("menu_$label").performScrollTo().performClick()
    }

    private fun step(b: () -> Unit) { runCatching(b).onFailure { android.util.Log.w("LULUCO_SHOT", "passo falhou: ${it.message?.take(120)}") } }

    /** PNG sem reducao nem compressao com perda, em pedacos no logcat (o CI le de la). */
    private fun png(name: String) {
        compose.waitForIdle()
        Thread.sleep(1500)
        val full = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val out = java.io.ByteArrayOutputStream()
        full.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        val b64 = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
        android.util.Log.i("LULUCO_SHOT", "PNG75 $name INICIO ${full.width}x${full.height} ${out.size()}")
        b64.chunked(3000).forEachIndexed { i, part -> android.util.Log.i("LULUCO_SHOT", "PNG75 $name ${i.toString().padStart(4, '0')} $part") }
        android.util.Log.i("LULUCO_SHOT", "PNG75 $name FIM")
    }

    @Test fun capturasPost2() {
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

        app.graph.settings.update { it.copy(vitrineMinutes = 0, nightStartHour = 0, nightEndHour = 0) }
        app.graph.session.save(JSONObject("""{"access_token":"x","refresh_token":"y","expires_in":999999,"user":{"id":"00000000-0000-0000-0000-000000000001","email":"teste@example.com"}}"""))

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            Thread.sleep(2500)

            // 1. Calculadora: secao "Correcao de KH, Ca e Mg" (ultima da tela), com os valores que o
            //    app preenche sozinho (ultimo teste e alvo padrao).
            step { menu("Calculadoras") }
            step { compose.onNodeWithText("Ordem: magnésio primeiro", substring = true, ignoreCase = true).performScrollTo() }
            png("calculadora")
            step { compose.onNodeWithText("Correção de KH, Ca e Mg", ignoreCase = true).performScrollTo() }
            png("calculadora_titulo")

            // 2. Registro do teste de agua com as roletas
            step { menu("Registrar") }
            step { compose.onNodeWithText("Teste de água", ignoreCase = true).performClick() }
            png("registro_roletas")

            // 3. Quarentena: protocolos e o protocolo escolhido; depois o dia a dia
            // (o teste de agua tem "Voltar" no lugar do menu)
            step { compose.onNodeWithText("Voltar", ignoreCase = true).performClick() }
            step { menu("Saúde e quarentena") }
            step { compose.onNodeWithText("Quarentena", ignoreCase = true).performScrollTo() }
            png("quarentena_lista")
            step { compose.onNodeWithText("Cobre (Cupramine) + Prazipro", ignoreCase = true).performClick() }
            png("quarentena")
            step { compose.onNodeWithText("Dia a dia", ignoreCase = true).performScrollTo() }
            png("quarentena_dia_a_dia")
            // standby: entra sozinho depois de 1 minuto sem toque (mesmo truque do teste da propria build 75)
            step { compose.onNodeWithText("Voltar", ignoreCase = true).performScrollTo().performClick() }
            step { menu("Painel") }
            app.graph.settings.update { it.copy(vitrineMinutes = 1) }
            Thread.sleep(63_000)
            compose.mainClock.advanceTimeBy(11_000)
            png("standby")
        }
    }
}
