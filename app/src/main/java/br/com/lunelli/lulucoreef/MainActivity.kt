package br.com.lunelli.lulucoreef

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import br.com.lunelli.lulucoreef.ui.ConnectionProofScreen
import br.com.lunelli.lulucoreef.ui.LulucoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LulucoTheme { ConnectionProofScreen() } }
    }
}
