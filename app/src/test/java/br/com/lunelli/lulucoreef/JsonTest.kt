package br.com.lunelli.lulucoreef

import br.com.lunelli.lulucoreef.data.ApiJson
import br.com.lunelli.lulucoreef.data.Routine
import br.com.lunelli.lulucoreef.data.WaterTest
import br.com.lunelli.lulucoreef.data.parseTs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonTest {
    @Test fun leLinhaDoPostgrest() {
        val row = """{"id":"11111111-1111-1111-1111-111111111111","user_id":"u","created_at":"2026-11-16T06:00:00.123456+00:00",
            "updated_at":"2026-11-16T06:00:00+00:00","deleted_at":null,"synced_at":"2026-11-16T06:00:01+00:00",
            "titulo":"Amino Coral","tipo":"dosagem","product_id":"p","equipment_id":null,"recorrencia":"dias_da_semana",
            "dias_semana":[1,3,5],"intervalo_n":null,"horario":"20:00:00","proxima_em":null,"instrucao":null,"ativa":true}"""
        val r = ApiJson.decodeFromString(Routine.serializer(), row)
        assertEquals(listOf(1, 3, 5), r.diasSemana)
        assertEquals("p", r.productId)
        assertEquals(parseTs("2026-11-16T06:00:00Z"), r.updatedAt)
        assertFalse(r.pending)
    }

    @Test fun enviaSnakeCaseSemCamposLocais() {
        val t = WaterTest(medidoEm = parseTs("2026-11-16T10:30:00-03:00"), parametro = "kh", valor = 8.2, unidade = "dKH", pending = true)
        val json = ApiJson.encodeToString(WaterTest.serializer(), t)
        assertTrue(json, json.contains("\"medido_em\":\"2026-11-16T13:30:00Z\""))
        assertTrue(json, json.contains("\"deleted_at\":null"))
        assertFalse(json, json.contains("pending"))
        assertFalse(json, json.contains("user_id"))
    }

    @Test fun fichaDeEspecieJsonb() {
        val row = """{"id":"a","livestock_id":"l","conteudo_json":{"nome_cientifico":"Duncanopsammia axifuga","conflitos":[]},
            "gerada_em":"2026-11-16T06:00:00+00:00","confirmada_pelo_usuario":false,"created_at":"2026-11-16T06:00:00+00:00","updated_at":"2026-11-16T06:00:00+00:00","deleted_at":null}"""
        val s = ApiJson.decodeFromString(br.com.lunelli.lulucoreef.data.SpeciesInfo.serializer(), row)
        assertTrue(s.conteudoJson, s.conteudoJson.contains("\"nome_cientifico\":\"Duncanopsammia axifuga\""))
        val back = ApiJson.encodeToString(br.com.lunelli.lulucoreef.data.SpeciesInfo.serializer(), s)
        assertTrue(back, back.contains("\"conteudo_json\":{\"nome_cientifico\""))
    }
}
