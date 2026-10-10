package com.maurozegarra.master.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maurozegarra.master.R
import com.maurozegarra.master.i18n.I18n
import com.maurozegarra.master.ui.theme.AppTheme
import com.maurozegarra.master.ui.theme.MasterTheme
import java.time.LocalTime

/**
 * La pantalla completa del timer que suena como alarma (TD-195): el nombre, la hora, y dos
 * botones, Snooze (cinco minutos más) y Dismiss. Pedido el 9-oct: "alarma completa: dismiss
 * y snooze". Sin preguntar el dolor, que es solo de la alarma de la mañana.
 */
class TimerRingActivity : ComponentActivity() {

    /** Si el servicio se calla solo (nadie la tocó en 10 min), esta pantalla también se va. */
    private val alApagar = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val filtro = IntentFilter(TimerRingService.ACTION_STOPPED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(alApagar, filtro, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(alApagar, filtro)
        }
        val nombre = TimerRingService.ringingName(this)
        val w = I18n.EN.more.timer
        setContent {
            MasterTheme(darkTheme = true) {
                val c = AppTheme.colors
                val ahora = LocalTime.now()
                Column(
                    Modifier.fillMaxSize().background(c.bg).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(nombre.ifBlank { w.timer }, color = c.textPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(w.done, color = c.textDim, fontSize = 16.sp)
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "%d:%02d".format(ahora.hour, ahora.minute),
                        color = c.textPrimary, fontSize = 72.sp,
                        fontFamily = FontFamily(Font(R.font.jetbrains_mono_digits)),
                    )
                    Spacer(Modifier.height(48.dp))
                    BigButton(w.snooze, filled = false) {
                        confirmar()
                        TimerAlarm.snooze(this@TimerRingActivity, nombre)
                        finish()
                    }
                    Spacer(Modifier.height(12.dp))
                    BigButton(w.dismiss, filled = true) {
                        confirmar()
                        TimerAlarm.dismiss(this@TimerRingActivity)
                        finish()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(alApagar) }
        super.onDestroy()
    }

    /** Corta y doble, como la de la mañana: que el toque se sienta recibido. */
    private fun confirmar() {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        v?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 40, 90, 40), -1))
    }
}

@androidx.compose.runtime.Composable
private fun BigButton(label: String, filled: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(32.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(shape)
            .then(if (filled) Modifier.background(c.accent) else Modifier.border(2.dp, c.accent, shape))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (filled) c.onAccent else c.accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}
