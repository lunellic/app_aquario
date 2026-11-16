package br.com.lunelli.lulucoreef

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import br.com.lunelli.lulucoreef.reminders.Reminders
import br.com.lunelli.lulucoreef.ui.AppRoot
import br.com.lunelli.lulucoreef.ui.LulucoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Tablet de parede: a tela fica ligada enquanto o app estiver aberto.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        Reminders.schedule(this)
        setContent { LulucoTheme { AppRoot() } }
    }
}
