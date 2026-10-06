package com.maurozegarra.master.morning

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maurozegarra.master.i18n.Strings
import com.maurozegarra.master.ui.master.DayCircle
import com.maurozegarra.master.ui.master.listCard
import androidx.compose.material.icons.outlined.Delete
import com.maurozegarra.master.ui.theme.AppTheme
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle

/**
 * Las alarmas, una tarjeta cada una (TD-175): la hora grande -tocarla la cambia-, su
 * interruptor, y los siete días para marcar en cuáles suena. Agregar otra va en el "+" de
 * la barra (TD-182): cada vez que [addRequests] sube, se pide la hora.
 *
 * Antes era una lista de los siete días con su hora y un interruptor general; el usuario
 * lo pidió como un despertador cuando dejó el suyo. Cualquier cambio reprograma en el
 * momento: la próxima que se ve abajo es la que está en AlarmManager, no una cuenta aparte.
 */
@Composable
fun MorningSettings(t: Strings, accent: Color, addRequests: Int = 0) {
    val ctx = LocalContext.current
    val store = remember { MorningStore(ctx) }
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

    fun aplicar(s: MorningSchedule, avisar: Boolean = true) {
        val encendia = schedule.anyOn
        schedule = s
        store.saveSchedule(s)
        MorningAlarm.reschedule(ctx)
        // "Alarm in 8 h 27 min" al poner o cambiar una alarma, como su despertador de antes:
        // confirma en el acto que quedo bien puesta (28-sep). No al borrar ni al apagar.
        if (avisar) MorningAlarm.nextRing(ctx)?.let { ring ->
            val min = java.time.Duration.between(java.time.ZonedDateTime.now(ring.zone), ring).toMinutes().toInt()
            android.widget.Toast.makeText(ctx, t.morning.alarmIn.format(Countdown.text(min)), android.widget.Toast.LENGTH_SHORT).show()
        }
        // Al encender la primera, los permisos que la hacen sonar de verdad. Sin
        // notificaciones no hay pantalla de alarma ni "It eased"; sin pantalla completa
        // suena como una notificacion y hay que tocarla (22-sep).
        if (!encendia && s.anyOn) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                pedirNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else if (!puedePantallaCompleta(ctx)) {
                abrirAjustePantallaCompleta(ctx)
            }
        }
        cambios++
    }

    // La hora que se esta eligiendo y que hacer con ella: el reloj es un dialogo de Compose
    // (TD-183), que se pinta mientras esto no sea null.
    var pidiendo by remember { mutableStateOf<Pair<LocalTime, (LocalTime) -> Unit>?>(null) }
    fun elegirHora(inicial: LocalTime, listo: (LocalTime) -> Unit) {
        pidiendo = inicial to listo
    }
    pidiendo?.let { (inicial, listo) ->
        com.maurozegarra.master.ui.master.ClockTimeDialog(
            initial = inicial,
            accent = accent,
            t = t,
            onDismiss = { pidiendo = null },
            onConfirm = { h -> pidiendo = null; listo(h) },
        )
    }

    // La próxima, tal como quedó programada -con el día saltado-. `cambios` la recalcula.
    val proxima = remember(cambios, schedule) { MorningAlarm.nextRing(ctx) }
    // Cuanto falta, al minuto: se recalcula solo mientras la pantalla esta abierta.
    var ahora by remember { mutableStateOf(java.time.ZonedDateTime.now()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(20_000)
            ahora = java.time.ZonedDateTime.now()
        }
    }
    val manana = java.time.LocalDate.now().plusDays(1)
    val saltado = remember(cambios) { store.skipDate == manana }
    val swipe = com.maurozegarra.master.ui.rememberSwipeRowsController()

    // Una nueva se pide con la hora primero -es lo que la define- y nace encendida en los
    // dias que ninguna otra cubre (TD-182). El 0 inicial no pide nada.
    androidx.compose.runtime.LaunchedEffect(addRequests) {
        if (addRequests > 0) elegirHora(LocalTime.of(6, 0)) { h -> aplicar(schedule.add(h, schedule.newAlarmDays())) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // La cabecera lleva la proxima y el salto de mañana en su misma linea: sueltos
        // debajo de la lista, cada uno con su espacio, dejaban la pantalla llena de aire.
        Column {
            // "Skip tomorrow" en la linea del titulo, alineado con el: en una columna aparte
            // quedaba centrado entre el titulo y la proxima, un poco mas abajo (27-sep).
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.morning.alarms, color = AppTheme.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                // Saltar mañana sin tocar las alarmas: un feriado, un viaje. Solo si mañana sonaria.
                if (schedule.ringsOn(manana.dayOfWeek)) {
                    Text(
                        if (saltado) t.morning.undo else t.morning.skipTomorrow,
                        color = accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable {
                            store.skipDate = if (saltado) null else manana
                            MorningAlarm.reschedule(ctx)
                            cambios++
                        },
                    )
                }
            }
            Text(
                if (saltado) t.morning.tomorrowSkipped
                // Con cuanto falta, pedido por el usuario el 28-sep: lo que extrañaba de su
                // despertador. Va en la linea de la proxima, que es donde se mira.
                else proxima?.let {
                    val falta = Countdown.text(java.time.Duration.between(ahora, it).toMinutes().toInt())
                    "${t.morning.next}: ${it.dayOfWeek.getDisplayName(TextStyle.FULL, t.locale)} ${it.toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("H:mm"))} · ${t.morning.inTime.format(falta)}"
                }
                    ?: t.morning.alarmOff,
                color = AppTheme.colors.textDim,
                fontSize = 13.sp,
            )
        }
        schedule.alarms.forEach { a ->
            // Borrar como en la lista de trainings: deslizando a la izquierda.
            com.maurozegarra.master.ui.SwipeActionsRow(
                actions = listOf(
                    com.maurozegarra.master.ui.SwipeAction(
                        androidx.compose.material.icons.Icons.Outlined.Delete, t.morning.deleteAlarm,
                    ) { aplicar(schedule.remove(a.id), avisar = false) },
                ),
                controller = swipe,
            ) {
                AlarmCard(
                    a, t, accent,
                    onTime = { elegirHora(a.time) { h -> aplicar(schedule.update(a.copy(time = h))) } },
                    onToggle = { on -> aplicar(schedule.update(a.copy(enabled = on)), avisar = on) },
                    onDay = { d -> aplicar(schedule.update(a.copy(days = if (d in a.days) a.days - d else a.days + d))) },
                )
            }
        }
    }

    // El aviso para ir a dormir (TD-160). Es un bloque de AJUSTES, no un elemento de lista:
    // va en SectionCard, como Today, y no en la tarjeta de las alarmas.
    var cama by remember { mutableStateOf(store.bedtime()) }
    fun aplicarCama(c: BedtimeConfig) {
        cama = c
        store.saveBedtime(c)
        MorningAlarm.reschedule(ctx)
        cambios++
    }
    val avisoCama = remember(cambios, cama, schedule) { MorningAlarm.nextBedtime(ctx) }
    val hm = java.time.format.DateTimeFormatter.ofPattern("H:mm")
    // El margen derecho de las alarmas de arriba, para que los interruptores caigan en la
    // misma linea.
    com.maurozegarra.master.ui.master.SectionCard(endPadding = com.maurozegarra.master.ui.theme.Dims.rowPaddingEnd) {
        com.maurozegarra.master.ui.SwitchRow(
            label = t.morning.bedtime,
            desc = avisoCama?.let { (aviso, ring) -> t.morning.bedBy.format(Bedtime.bedBy(ring, cama).format(hm), aviso.format(hm)) },
            checked = cama.enabled,
            accent = accent,
            onCheckedChange = { aplicarCama(cama.copy(enabled = it)) },
        )
        if (cama.enabled) {
            Spacer(Modifier.height(12.dp))
            Text(t.morning.sleepGoal, color = AppTheme.colors.textDim, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            com.maurozegarra.master.ui.settings.SegmentedRow(
                // Duraciones y no horas: "7:30" al lado de "Bed by 23:30" se leia como reloj.
                options = listOf(420 to "7 h", 450 to "7 h 30", 480 to "8 h"),
                selected = cama.sleepMin,
                accent = accent,
                onSelect = { aplicarCama(cama.copy(sleepMin = it)) },
            )
            Spacer(Modifier.height(12.dp))
            Text(t.morning.reminderLead, color = AppTheme.colors.textDim, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            com.maurozegarra.master.ui.settings.SegmentedRow(
                options = listOf(15 to "15 min", 30 to "30 min", 45 to "45 min"),
                selected = cama.leadMin,
                accent = accent,
                onSelect = { aplicarCama(cama.copy(leadMin = it)) },
            )
        }
        // "Going to bed" aqui tambien, sin depender de la notificacion (30-sep): se le paso
        // la de las 21:00 y la hora de acostarse quedo sin anotar. Aunque el aviso este
        // apagado, anotar la hora sigue sirviendo.
        Spacer(Modifier.height(12.dp))
        val estaNoche = remember(cambios) {
            val zona = java.time.ZoneId.systemDefault()
            val dia = MorningLog.morningOf(System.currentTimeMillis(), zona).toString()
            store.entries().firstOrNull { it.date == dia }?.bedAt
        }
        estaNoche?.let {
            Text(
                t.morning.inBedAt.format(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalTime().format(hm)),
                color = AppTheme.colors.textDim,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(6.dp))
        }
        // Principal, con el borde del acento como Start (5-oct): es lo que se toca cada noche.
        com.maurozegarra.master.ui.AppPrimaryButton(
            label = t.morning.goingToBed,
            accent = accent,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                MorningAlarm.toBed(ctx)
                cambios++
            },
        )
    }

    if (!schedule.anyOn) return

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
        }
    }
}

/** La hora en JetBrains Mono, como en la barra de MASTER: cifras de ancho fijo. */
private val MonoDigits = androidx.compose.ui.text.font.FontFamily(
    androidx.compose.ui.text.font.Font(com.maurozegarra.master.R.font.jetbrains_mono_digits),
)

/**
 * Una alarma, con la tarjeta de un training (TD-175): la hora a la izquierda, el
 * interruptor a la derecha, y debajo los días con el círculo de la semana. Todo sale de
 * los componentes y la paleta que ya usa el resto del app.
 */
@Composable
private fun AlarmCard(
    a: MorningAlarmSpec,
    t: Strings,
    accent: Color,
    onTime: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDay: (DayOfWeek) -> Unit,
) {
    val suena = a.enabled && a.days.isNotEmpty()
    Column(Modifier.listCard()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                a.time.format(java.time.format.DateTimeFormatter.ofPattern("H:mm")),
                color = if (suena) AppTheme.colors.textPrimary else AppTheme.colors.textFaded,
                fontSize = 28.sp,
                fontFamily = MonoDigits,
                modifier = Modifier.weight(1f).clickable(onClick = onTime),
            )
            com.maurozegarra.master.ui.AppSwitch(a.enabled, accent, onToggle)
        }
        Spacer(Modifier.height(8.dp))
        // Apagada, los días se atenúan como la hora (5-oct): seguían en rojo vivo y parecía
        // que la alarma iba a sonar. Se pueden seguir tocando para dejarla lista.
        Row(
            Modifier.fillMaxWidth().alpha(if (a.enabled) 1f else 0.4f),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            DayOfWeek.entries.forEach { d ->
                DayCircle(
                    d.getDisplayName(TextStyle.NARROW, t.locale),
                    selected = d in a.days,
                    accent = accent,
                    modifier = Modifier.clickable { onDay(d) },
                    outlined = true,
                )
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
