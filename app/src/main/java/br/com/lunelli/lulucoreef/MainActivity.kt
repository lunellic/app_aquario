package br.com.lunelli.lulucoreef

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import br.com.lunelli.lulucoreef.reminders.Reminders
import br.com.lunelli.lulucoreef.ui.AppRoot
import br.com.lunelli.lulucoreef.ui.LulucoTheme

/**
 * Modo quiosque: o tablet e usado so para o app.
 * - tela cheia (immersive sticky) em todas as telas, reaplicada sempre que as barras voltam;
 * - aparece por cima da tela de bloqueio e liga a tela (funciona sem PIN/senha no aparelho);
 * - tela sempre ligada enquanto o app esta aberto.
 */
class MainActivity : ComponentActivity() {
    private val rehide = Runnable { hideBars() }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
        )
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        hideBars()
        // Se as barras aparecerem (arrastar da borda, teclado, dialogo), somem de novo sozinhas.
        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
                window.decorView.removeCallbacks(rehide)
                window.decorView.postDelayed(rehide, 2500)
            }
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        Reminders.schedule(this)
        setContent { LulucoTheme { AppRoot() } }
    }

    override fun onResume() {
        super.onResume()
        hideBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    @Suppress("DEPRECATION")
    private fun hideBars() {
        // Sem as flags LAYOUT_*: o conteudo continua sendo redimensionado quando o teclado abre.
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
    }

    companion object {
        private fun homeAlias(context: Context) = ComponentName(context, "br.com.lunelli.lulucoreef.HomeAlias")

        /** O app esta oferecido como tela inicial (launcher) do aparelho? */
        fun isHomeEnabled(context: Context): Boolean =
            context.packageManager.getComponentEnabledSetting(homeAlias(context)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED

        /** Liga/desliga a oferta como tela inicial. O Fire OS 5 costuma ignorar launchers de terceiros. */
        fun setHomeEnabled(context: Context, enabled: Boolean) {
            context.packageManager.setComponentEnabledSetting(
                homeAlias(context),
                if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
