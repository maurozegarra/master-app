package com.maurozegarra.master.morning

import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maurozegarra.master.i18n.I18n
import com.maurozegarra.master.ui.theme.AppTheme
import com.maurozegarra.master.ui.theme.MasterTheme
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * La pantalla de la alarma: apagarla ES contestar el dolor (TD-151).
 *
 * Once botones grandes, del 0 al 10, con los ojos recién abiertos y sin gafas: el número
 * se toca, no se busca. Posponer arriba, grande; apagar sin contestar abajo y pequeño, porque
 * es la salida que no se quiere que sea la cómoda.
 *
 * **Cada toque se confirma** (TD-157). La primera versión se cerraba en el mismo instante
 * del toque, y el 24-sep el usuario lo resumió así: *"lo toco y se apaga, me da la sensación
 * como que el app se cerró, o hubo un error"*. Ahora el sonido se corta en seco, vibra corto
 * y doble -distinto del largo de la alarma-, y la pantalla dice qué quedó guardado antes de
 * irse. Sigue siendo UN toque: la confirmación no pide nada, solo se puede corregir.
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
                var fase by remember { mutableStateOf<Fase>(Fase.Preguntando) }
                // Si ya se contesto una vez, "Change" corrige: no vuelve a anotar la hora ni a
                // poner otra notificacion de "It eased".
                var contestado by remember { mutableStateOf(false) }
                when (val f = fase) {
                    Fase.Preguntando -> AlarmScreen(
                        onPain = { n ->
                            vibrarConfirmacion()
                            MorningRingService.resetAutoSnoozes(this)
                            // Lo primero, y en el acto: guardar y callar la alarma. La
                            // confirmacion es solo pantalla; si el sistema cerrara el app
                            // durante esos segundos, el dato ya estaria guardado.
                            if (contestado) MorningAlarm.correct(this, n) else MorningAlarm.dismiss(this, pain = n)
                            contestado = true
                            fase = Fase.Guardado(n)
                        },
                        onSnooze = {
                            vibrarConfirmacion()
                            MorningRingService.resetAutoSnoozes(this)
                            MorningAlarm.snooze(this)
                            fase = Fase.Aviso(I18n.EN.morning.snoozed.format(MorningAlarm.SNOOZE_MIN))
                        },
                        onDismiss = {
                            vibrarConfirmacion()
                            MorningRingService.resetAutoSnoozes(this)
                            MorningAlarm.dismiss(this, pain = null)
                            fase = Fase.Aviso(I18n.EN.morning.turnedOff)
                        },
                        yaContestado = contestado,
                    )
                    is Fase.Guardado -> Guardado(f.pain, onChange = { fase = Fase.Preguntando }, onDone = { finish() })
                    is Fase.Aviso -> Aviso(f.texto, onDone = { finish() })
                }
            }
        }
    }

    /** Corta y doble: "recibido". La de la alarma es larga y en bucle; no se confunden. */
    private fun vibrarConfirmacion() {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        v?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 40, 90, 40), -1))
    }
}

private sealed interface Fase {
    data object Preguntando : Fase
    data class Guardado(val pain: Int) : Fase
    data class Aviso(val texto: String) : Fase
}

/** Cuánto se queda la confirmación antes de cerrarse sola. */
private const val GUARDADO_MS = 3_000L
private const val AVISO_MS = 1_200L

@Composable
private fun AlarmScreen(onPain: (Int) -> Unit, onSnooze: () -> Unit, onDismiss: () -> Unit, yaContestado: Boolean) {
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
        // Corrigiendo ya no se pospone ni se apaga: la alarma se callo al contestar.
        if (!yaContestado) {
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
        if (!yaContestado) {
            Text(
                t.morning.dismissWithout,
                color = AppTheme.colors.textDim,
                fontSize = 14.sp,
                modifier = Modifier.clickable(onClick = onDismiss).padding(12.dp),
            )
        }
    }
}

/**
 * Lo que quedó guardado: el número en grande, entrando con un pequeño rebote, y cómo
 * seguir. Se cierra sola; "Change" vuelve a la escala para corregir.
 */
@Composable
private fun Guardado(pain: Int, onChange: () -> Unit, onDone: () -> Unit) {
    val t = I18n.EN
    val accent = AppTheme.colors.accent
    val escala = remember { Animatable(0.6f) }
    LaunchedEffect(pain) {
        escala.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    }
    LaunchedEffect(pain) {
        delay(GUARDADO_MS)
        onDone()
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(AppTheme.colors.bg)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(160.dp)
                .scale(escala.value)
                .clip(CircleShape)
                .background(accent),
            contentAlignment = Alignment.Center,
        ) {
            Text("$pain", color = AppTheme.colors.onAccent, fontSize = 80.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(28.dp))
        Text("\u2713  " + t.morning.saved.format(pain), color = AppTheme.colors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(t.painScale.getOrElse(pain) { "" }, color = AppTheme.colors.textDim, fontSize = 15.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Text(t.morning.easeHint, color = AppTheme.colors.textDim, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Text(
            t.morning.change,
            color = AppTheme.colors.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, AppTheme.colors.textFaded, RoundedCornerShape(12.dp))
                .clickable(onClick = onChange)
                .padding(horizontal = 28.dp, vertical = 12.dp),
        )
    }
}

/** Posponer y apagar sin contestar: un mensaje corto antes de cerrarse. */
@Composable
private fun Aviso(texto: String, onDone: () -> Unit) {
    LaunchedEffect(texto) {
        delay(AVISO_MS)
        onDone()
    }
    Box(
        Modifier.fillMaxSize().background(AppTheme.colors.bg),
        contentAlignment = Alignment.Center,
    ) {
        Text("\u2713  $texto", color = AppTheme.colors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}
