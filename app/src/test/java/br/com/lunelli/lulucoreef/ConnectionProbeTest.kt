package br.com.lunelli.lulucoreef

import br.com.lunelli.lulucoreef.net.ConnectionProbe
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionProbeTest {
    @Test fun extraiMensagemDeErroDoSupabase() {
        assertEquals("Invalid login credentials", ConnectionProbe.errorMessage("""{"code":400,"error_code":"invalid_credentials","msg":"Invalid login credentials"}"""))
    }

    @Test fun corpoNaoJsonVoltaTruncado() {
        assertEquals("erro", ConnectionProbe.errorMessage("erro"))
    }
}
