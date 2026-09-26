package com.maurozegarra.master.morning

import android.Manifest
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maurozegarra.master.i18n.Strings
import com.maurozegarra.master.ui.SwitchRow
import com.maurozegarra.master.ui.theme.AppTheme
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle

/**
 * La alarma en Settings (TD-151): encenderla y la hora de cada día.
 *
 * Tocar una hora la cambia; tocar el nombre del día lo apaga o lo enciende. Cualquier
 * cambio reprograma en el momento: la próxima que se ve abajo es la que está en AlarmManager,
 * no una cuenta aparte.
 */
@Composable
fun MorningSettings(t: Strings, accent: Color) {
    val ctx = LocalContext.current
    val store = remember { MorningStore(ctx) }
    var enabled by remember { mutableStateOf(store.enabled) }
    var schedule by remember { mutableStateOf(store.schedule()) }
    var cambios by remember { mutableStateOf(0) }

    val pedirNotificaciones = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { cambios++ }

    // Los permisos se dan FUERA del app, en los ajustes de Android: al volver hay que
    // volver a mirarlos, o el aviso seguiria ahi aunque ya se hubiera concedido.
    val ciclo = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(ciclo) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) cambios++
        }
        ciclo.lifecycle.addObserver(obs)
        onDispose { ciclo.lifecycle.removeObserver(obs) }
    }

    fun aplicar(s: MorningSchedule) {
        schedule = s
        store.saveSchedule(s)
        MorningAlarm.reschedule(ctx)
        cambios++
    }

    SwitchRow(
        label = t.morning.title,
        desc = t.morning.desc,
        checked = enabled,
        accent = accent,
        onCheckedChange = {
            enabled = it
            store.enabled = it
            MorningAlarm.reschedule(ctx)
            // Sin permiso de notificaciones no hay pantalla de alarma ni "It eased".
            if (it && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                pedirNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else if (it && !puedePantallaCompleta(ctx)) {
                // Y sin la pantalla completa, suena pero no enciende la pantalla: el 22-sep
                // sono como una notificacion y hubo que tocarla. Se lleva al ajuste de una
                // vez, al encenderla, en vez de esperar a que se lea el aviso de abajo.
                abrirAjustePantallaCompleta(ctx)
            }
            cambios++
        },
    )
    if (!enabled) return

    Spacer(Modifier.height(12.dp))
    Column {
        DayOfWeek.entries.forEach { d ->
            val hora = schedule.at(d)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    d.getDisplayName(TextStyle.FULL, t.locale),
                    color = if (hora != null) AppTheme.colors.textPrimary else AppTheme.colors.textFaded,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { aplicar(schedule.with(d, if (hora == null) LocalTime.of(7, 0) else null)) },
                )
                Text(
                    hora?.toString() ?: t.morning.off,
                    color = if (hora != null) accent else AppTheme.colors.textFaded,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable {
                        val h = hora ?: LocalTime.of(7, 0)
                        TimePickerDialog(ctx, { _, hh, mm -> aplicar(schedule.with(d, LocalTime.of(hh, mm))) }, h.hour, h.minute, true).show()
                    },
                )
            }
        }
    }

    // La próxima, tal como quedó programada -con el día saltado-. `cambios` la recalcula.
    val proxima = remember(cambios, schedule) { MorningAlarm.nextRing(ctx) }
    val manana = java.time.LocalDate.now().plusDays(1)
    val saltado = remember(cambios) { store.skipDate == manana }
    Spacer(Modifier.height(8.dp))
    Text(
        proxima?.let {
            "${t.morning.next}: ${it.dayOfWeek.getDisplayName(TextStyle.FULL, t.locale)} ${it.toLocalTime()}"
        } ?: t.morning.off,
        color = AppTheme.colors.textDim,
        fontSize = 13.sp,
    )

    // Saltar mañana sin tocar el horario: un feriado, un viaje. Solo si mañana sonaria.
    if (schedule.at(manana.dayOfWeek) != null) {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (saltado) t.morning.tomorrowSkipped else t.morning.skipTomorrow,
                color = if (saltado) AppTheme.colors.textPrimary else accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f)
                    .then(if (saltado) Modifier else Modifier.clickable {
                        store.skipDate = manana
                        MorningAlarm.reschedule(ctx)
                        cambios++
                    }),
            )
            if (saltado) {
                Text(
                    t.morning.undo,
                    color = accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable {
                        store.skipDate = null
                        MorningAlarm.reschedule(ctx)
                        cambios++
                    },
                )
            }
        }
    }

    // Sin permiso de alarma exacta no suena: se dice, y se lleva al ajuste.
    val programable = remember(cambios) { MorningAlarm.canSchedule(ctx) }
    if (!programable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Spacer(Modifier.height(12.dp))
        Text(
            t.morning.allowExact,
            color = accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable {
                ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
                cambios++
            },
        )
    }

    // La pantalla completa sobre el bloqueo. Desde Android 14 no se concede sola a un app
    // que no sea de alarmas en Play Store; sin ella, sonaria como una notificacion y habria
    // que desbloquear para contestar.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val puede = remember(cambios) { puedePantallaCompleta(ctx) }
        if (!puede) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t.morning.allowFullScreen,
                    color = accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable {
                        abrirAjustePantallaCompleta(ctx)
                        cambios++
                    },
                )
                Spacer(Modifier.width(4.dp))
            }
        }
    }
}

/**
 * Si la alarma puede encender la pantalla sobre el bloqueo.
 *
 * NO basta con canUseFullScreenIntent(): el 22-sep, en el Samsung del usuario, decia que si
 * -el permiso figuraba concedido- y el sistema rechazaba la pantalla completa igual, porque
 * la operacion que la decide estaba en su modo por defecto y ahi Samsung la niega. Se mira
 * esa operacion: solo "permitida" cuenta. En un Android que la conceda por defecto, esto
 * ensena el aviso de mas, que es el error barato.
 */
private fun puedePantallaCompleta(ctx: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
    val ops = ctx.getSystemService(android.app.AppOpsManager::class.java)
    val modo = runCatching {
        ops.unsafeCheckOpNoThrow("android:use_full_screen_intent", android.os.Process.myUid(), ctx.packageName)
    }.getOrNull() ?: return ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    return modo == android.app.AppOpsManager.MODE_ALLOWED
}

private fun abrirAjustePantallaCompleta(ctx: android.content.Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
    ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${ctx.packageName}")))
}
