package br.com.lunelli.lulucoreef.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import br.com.lunelli.lulucoreef.R
import br.com.lunelli.lulucoreef.net.ConnectionProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ConnectionProofScreen() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    val steps = remember { mutableStateListOf<ConnectionProbe.Step>() }

    val form: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row {
                Image(painterResource(R.drawable.logo_luluco), contentDescription = "Luluco Reef",
                    modifier = Modifier.size(96.dp).clip(RoundedCornerShape(16.dp)))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Luluco Reef", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Prova de conexão com o Supabase", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedTextField(email, { email = it }, label = { Text("E-mail") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(password, { password = it }, label = { Text("Senha") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
            Button(
                enabled = !running && email.isNotBlank() && password.isNotEmpty(),
                onClick = {
                    running = true
                    steps.clear()
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            try {
                                ConnectionProbe(context).run(email, password) { step -> scope.launch { steps.add(step) } }
                            } catch (e: Exception) {
                                scope.launch { steps.add(ConnectionProbe.Step(false, "Erro inesperado", ConnectionProbe.describe(e))) }
                            }
                        }
                        running = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text(if (running) "Testando..." else "Entrar e testar") }
        }
    }

    val log: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (steps.isEmpty()) Text("O resultado de cada etapa aparece aqui.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            steps.forEach { s ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp)) {
                    Text((if (s.ok) "OK  " else "FALHOU  ") + s.title, fontWeight = FontWeight.Bold,
                        color = if (s.ok) Luluco.Ok else Luluco.Critical)
                    Text(s.detail, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!running && steps.isNotEmpty()) {
                val allOk = steps.all { it.ok }
                Text(if (allOk) "Tudo certo neste aparelho." else "Houve falha. Mande uma foto desta tela.",
                    fontWeight = FontWeight.Bold, color = if (allOk) Luluco.Ok else Luluco.Coral)
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(20.dp)) {
        if (maxWidth > maxHeight) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                form(Modifier.weight(1f))
                log(Modifier.weight(1.2f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                form(Modifier.fillMaxWidth())
                log(Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}
