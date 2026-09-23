package com.maurozegarra.master.morning

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maurozegarra.master.i18n.I18n
import com.maurozegarra.master.ui.theme.AppTheme
import com.maurozegarra.master.ui.theme.MasterTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * La pantalla de la alarma: apagarla ES contestar el dolor (TD-151).
 *
 * Once botones grandes, del 0 al 10, con los ojos recién abiertos y sin gafas: el número
 * se toca, no se busca. Posponer arriba, grande; apagar sin contestar abajo y pequeño, porque
 * es la salida que no se quiere que sea la cómoda.
 *
 * Sobre la pantalla de bloqueo y encendiéndola, como la de cualquier despertador.
 */
class MorningActivity : ComponentActivity() {

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
        setContent {
            MasterTheme(darkTheme = true) {
                AlarmScreen(
                    onPain = { n -> terminar { MorningAlarm.dismiss(this, pain = n) } },
                    onSnooze = { terminar { MorningAlarm.snooze(this) } },
                    onDismiss = { terminar { MorningAlarm.dismiss(this, pain = null) } },
                )
            }
        }
    }

    private fun terminar(accion: () -> Unit) {
        MorningRingService.resetAutoSnoozes(this)
        accion()
        finish()
    }
}

@Composable
private fun AlarmScreen(onPain: (Int) -> Unit, onSnooze: () -> Unit, onDismiss: () -> Unit) {
    val t = I18n.EN
    val accent = AppTheme.colors.accent
    Column(
        Modifier
            .fillMaxSize()
            .background(AppTheme.colors.bg)
            // Las barras del sistema, fuera: sin esto "Turn off without answering" quedaba
            // debajo de la barra de navegacion, cortado (22-sep).
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Text(
            LocalTime.now().format(DateTimeFormatter.ofPattern("H:mm")),
            color = AppTheme.colors.textPrimary,
            fontSize = 72.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(AppTheme.colors.surface)
                .clickable(onClick = onSnooze)
                .padding(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("${t.morning.snooze} · ${MorningAlarm.SNOOZE_MIN} min", color = AppTheme.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.weight(1f))
        Text(
            t.painOnWaking,
            color = AppTheme.colors.textPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        // Tres filas de cuatro: dedos gordos a las cinco de la mañana.
        (0..10).chunked(4).forEach { fila ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                fila.forEach { n ->
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(accent)
                            .clickable { onPain(n) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$n", color = AppTheme.colors.onAccent, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    }
                }
                repeat(4 - fila.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(10.dp))
        }
        Text(
            t.painScale.getOrElse(0) { "" } + "  ·  10 = " + t.painScale.getOrElse(10) { "" },
            color = AppTheme.colors.textDim,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        Text(
            t.morning.dismissWithout,
            color = AppTheme.colors.textDim,
            fontSize = 14.sp,
            modifier = Modifier.clickable(onClick = onDismiss).padding(12.dp),
        )
    }
}
