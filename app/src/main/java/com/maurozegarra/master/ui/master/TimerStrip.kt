package com.maurozegarra.master.ui.master

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.key
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maurozegarra.master.R
import com.maurozegarra.master.i18n.Strings
import com.maurozegarra.master.timer.RecentTimer
import com.maurozegarra.master.timer.TimerAlarm
import com.maurozegarra.master.timer.TimerPlan
import com.maurozegarra.master.timer.TimerRun
import com.maurozegarra.master.timer.TimerStore
import com.maurozegarra.master.ui.SwipeAction
import com.maurozegarra.master.ui.SwipeActionsRow
import com.maurozegarra.master.ui.SwipeRowsController
import com.maurozegarra.master.ui.rememberSwipeRowsController
import com.maurozegarra.master.ui.TimesWordmark
import com.maurozegarra.master.ui.theme.AppTheme
import com.maurozegarra.master.ui.theme.Dims
import kotlinx.coroutines.delay
import java.time.ZoneId

private val TimerMono = FontFamily(Font(R.font.jetbrains_mono_digits))

/**
 * La franja del timer, al pie del tablero (TD-195). Cada timer en marcha con su cuenta, su
 * hora de fin, pausa y cancelar, y al pie la firma T\MES con el +, que abre el sheet de uno
 * nuevo. Sin timers queda solo la firma. El potty fijo salió el 14-oct, a pedido suyo.
 *
 * TD-198: el + iba fuera de la tarjeta, alineado arriba. Como el tablero está anclado al
 * fondo, la tarjeta crece hacia arriba: el + subía con cada timer y dejaba un hueco debajo.
 * Ahora va en la última fila, que no se mueve, y lo nuevo entra por arriba ([TimerPlan.stripOrder]).
 */
@Composable
internal fun TimerStrip(accent: Color, t: Strings) {
    val ctx = LocalContext.current
    val w = t.more.timer
    val runs by TimerStore.runs(ctx).collectAsState()
    val lista = runs.orEmpty()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // Solo hace falta contar mientras algo corre.
    LaunchedEffect(lista) {
        while (lista.any { !it.paused }) {
            now = System.currentTimeMillis()
            delay(250)
        }
        now = System.currentTimeMillis()
    }
    var sheet by remember { mutableStateOf(false) }
    val zone = remember { ZoneId.systemDefault() }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dims.card))
            .background(AppTheme.colors.surface),
    ) {
        val swipe = rememberSwipeRowsController()
        TimerPlan.stripOrder(lista).forEach { run -> key(run.id) { RunRow(run, now, zone, accent, t, swipe) } }
        // La firma, en gris como los encabezados de las tarjetas de arriba (12 dp, su
        // altura), y el + sin circulo como el de Water y Morning, en la columna del cancelar.
        Row(
            Modifier.fillMaxWidth().height(44.dp).padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TimesWordmark(AppTheme.colors.textDim, height = 12.dp)
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(44.dp).clip(CircleShape).clickable { sheet = true }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Add, contentDescription = w.newTimer, tint = accent)
            }
        }
    }

    if (sheet) NewTimerSheet(accent, t, onDismiss = { sheet = false })
}

/**
 * Una fila de la franja: ↻ y play/pausa, como su mini-timer (TD-198). La ✕ al lado del play
 * no le gustaba y era fácil tocarla sin querer: borrar pasó a deslizar la fila, con el mismo
 * panel que la lista de trainings y la del agua. Un timer se borra directo; una alarma pide
 * confirmación, porque perderla sin darse cuenta es justo lo que la alarma tenía que evitar.
 */
@Composable
private fun RunRow(run: TimerRun, now: Long, zone: ZoneId, accent: Color, t: Strings, swipe: SwipeRowsController) {
    val ctx = LocalContext.current
    val w = t.more.timer
    val c = AppTheme.colors
    var confirmar by remember { mutableStateOf(false) }
    SwipeActionsRow(
        actions = listOf(SwipeAction(Icons.Outlined.Delete, t.delete) { if (run.alarm) confirmar = true else TimerAlarm.cancel(ctx, run.id) }),
        controller = swipe,
    ) {
        // Con fondo propio: al deslizar, la fila tapa el panel del tacho hasta que se abre.
        Row(
            Modifier.fillMaxWidth().background(c.surface).height(56.dp).padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (run.alarm) {
                        Icon(Icons.Outlined.Alarm, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(run.name.ifBlank { w.timer }, color = c.textPrimary, fontSize = 15.sp, maxLines = 1)
                }
                Text(
                    if (run.paused) w.pausedLeft.format(TimerPlan.format(run.pausedLeftMs ?: 0))
                    else (if (run.alarm) w.ringsAt else w.endsAt).format(TimerPlan.clock(run.endAt, zone)),
                    color = c.textDim, fontSize = 11.sp, maxLines = 1,
                )
            }
            Text(
                TimerPlan.format(TimerPlan.left(run, now)),
                color = if (run.paused) c.textDim else c.textPrimary,
                fontSize = 20.sp, fontFamily = TimerMono,
            )
            StripIcon(Icons.Filled.Refresh, w.restart, c.textDim) {
                if (!swipe.consumeTapIfOpen()) TimerAlarm.restart(ctx, run.id)
            }
            // Al borde, en la columna del + de abajo.
            StripIcon(if (run.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, if (run.paused) w.resume else w.pause, accent) {
                if (!swipe.consumeTapIfOpen()) {
                    if (run.paused) TimerAlarm.resume(ctx, run.id) else TimerAlarm.pause(ctx, run.id)
                }
            }
        }
    }
    if (confirmar) {
        AlertDialog(
            onDismissRequest = { confirmar = false },
            containerColor = c.surface,
            titleContentColor = c.textPrimary,
            textContentColor = c.textDim,
            title = { Text(t.delete) },
            text = { Text(w.deleteAlarmConfirm.format(TimerPlan.clock(run.endAt, zone))) },
            confirmButton = {
                TextButton(onClick = { TimerAlarm.cancel(ctx, run.id); confirmar = false }) {
                    Text(t.delete, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmar = false }) { Text(t.cancel, color = c.textDim) }
            },
        )
    }
}

@Composable
private fun StripIcon(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/**
 * El sheet del + (TD-200): dos pestañas, Timer y Alarm, con el selector del app (el de
 * Sessions | Body), y se abre en la última que se usó.
 *
 * Antes la alarma vivía dentro del timer -unos chips de "Ends at" y un interruptor- y una
 * hora lejana obligaba a calcular la duración. Sus palabras (14-oct): "cuando uno habla de
 * alarma, justamente no quiere ponerse a calcular". Ahora la alarma se pone por HORA.
 *
 * Timer: "+ Custom" despliega la rueda, y debajo los recientes y los presets de su Galaxy
 * Watch8, que arrancan de un toque.
 */
@Composable
private fun NewTimerSheet(accent: Color, t: Strings, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val w = t.more.timer
    val c = AppTheme.colors
    val store = remember { TimerStore(ctx) }
    val recientes = remember { store.recents() }
    var alarma by remember { mutableStateOf(store.alarmTab) }
    var custom by remember { mutableStateOf(false) }

    // Sin título, como los de Telegram (9-oct). La alarma y la rueda del timer no caben en el
    // alto normal.
    com.maurozegarra.master.ui.AppSheet(onDismiss = onDismiss, tall = custom || alarma) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SegmentToggle(
                options = listOf("timer" to w.timer, "alarm" to w.alarm),
                selected = if (alarma) "alarm" else "timer",
                accent = accent,
            ) { alarma = it == "alarm"; store.alarmTab = alarma }
            Spacer(Modifier.height(4.dp))

            if (alarma) {
                AlarmTab(accent, t, onStarted = onDismiss)
                return@Column
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dims.card))
                    .background(c.track)
                    .clickable { custom = !custom }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = accent)
                Spacer(Modifier.width(8.dp))
                Text(w.custom, color = c.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            if (custom) CustomTimer(accent, t, onStarted = onDismiss)

            if (recientes.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(w.recent, color = c.textDim, fontSize = 13.sp)
                recientes.forEach { r -> RecentRow(r, accent, t) { TimerAlarm.start(ctx, r.name, r.durationMs, r.alarm); onDismiss() } }
            }

            Spacer(Modifier.height(8.dp))
            Text(w.presets, color = c.textDim, fontSize = 13.sp)
            TimerPlan.PRESETS_SEC.chunked(4).forEach { fila ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    fila.forEach { sec ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .border(1.dp, accent, RoundedCornerShape(22.dp))
                                .clickable { TimerAlarm.start(ctx, "", sec * 1000L, alarm = false); onDismiss() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(TimerPlan.presetLabel(sec), color = c.textPrimary, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentRow(r: RecentTimer, accent: Color, t: Strings, onClick: () -> Unit) {
    val c = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (r.alarm) {
            Icon(Icons.Outlined.Alarm, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(r.name.ifBlank { t.more.timer.timer }, color = c.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1)
        Text(TimerPlan.format(r.durationMs), color = c.textPrimary, fontSize = 16.sp, fontFamily = TimerMono)
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = accent)
    }
}

/**
 * La duración de un timer con la rueda (TD-200), como el timer de su Watch8: Hr, Min y Sec, y
 * una sola columna activa. Reemplaza al teclado: así el timer y la alarma se usan igual.
 *
 * "When it ends" era "Ring as alarm", y el nombre confundía, más al lado de una pestaña que se
 * llama Alarm. Él lo ve como dos timers: el normal y el de pantalla completa que puede
 * despertar el teléfono (lo mostró en Multi Timer: Fullscreen / Simple Notification).
 */
@Composable
private fun CustomTimer(accent: Color, t: Strings, onStarted: () -> Unit) {
    val ctx = LocalContext.current
    val w = t.more.timer
    val c = AppTheme.colors
    val zone = remember { ZoneId.systemDefault() }
    var h by remember { mutableStateOf(0) }
    var m by remember { mutableStateOf(5) }
    var sg by remember { mutableStateOf(0) }
    // Como en el reloj, la que se mueve primero es la de los minutos: es la que más se usa.
    var foco by remember { mutableStateOf(1) }
    var teclado by remember { mutableStateOf(false) }
    var arrastre by remember { mutableStateOf(0) }
    var pantalla by remember { mutableStateOf(false) }
    var nombre by remember { mutableStateOf("") }
    val ahora by produceState(System.currentTimeMillis()) { while (true) { delay(15_000); value = System.currentTimeMillis() } }
    val duracion = TimerPlan.durationMs(h, m, sg)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            // Arrastrar mueve la rueda, con el teclado abierto o no; tocar lo abre (ver WheelField).
            WheelField(
                h, 0..TimerPlan.TIMER_HOURS_MAX, w.hr, foco == 0, accent, { foco = 0; arrastre++ }, { h = it }, minWidth = 84.dp,
                keyboard = teclado, onTap = { foco = 0; teclado = true },
            )
            WheelColon()
            WheelField(
                m, 0..59, w.min, foco == 1, accent, { foco = 1; arrastre++ }, { m = it }, minWidth = 84.dp,
                keyboard = teclado, onTap = { foco = 1; teclado = true },
            )
            WheelColon()
            WheelField(
                sg, 0..59, w.sec, foco == 2, accent, { foco = 2; arrastre++ }, { sg = it }, minWidth = 84.dp,
                keyboard = teclado, onTap = { foco = 2; teclado = true },
            )
            WheelKeyboard(
                teclado, foco, arrastre, listOf(0..TimerPlan.TIMER_HOURS_MAX, 0..59, 0..59),
                onValue = { col, v -> when (col) { 0 -> h = v; 1 -> m = v; else -> sg = v } },
                onColumn = { foco = it }, onClosed = { teclado = false },
            )
        }
        Text(
            if (duracion > 0) w.endsAtTime.format(TimerPlan.clock12(ahora + duracion, zone)) else "",
            color = c.textDim, fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        Spacer(Modifier.height(4.dp))
        Text(w.whenItEnds, color = c.textDim, fontSize = 13.sp)
        SegmentToggle(
            options = listOf("notification" to w.endNotification, "full" to w.endFullScreen),
            selected = if (pantalla) "full" else "notification",
            accent = accent,
        ) { pantalla = it == "full" }
        Text(if (pantalla) w.endFullScreenHint else w.endNotificationHint, color = c.textDim, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))

        com.maurozegarra.master.ui.AppTextField(
            value = nombre,
            onValueChange = { nombre = it.take(40) },
            label = w.name,
            modifier = Modifier.fillMaxWidth(),
            accent = accent,
        )
        PrimaryButton(label = w.start, accent = accent, enabled = duracion > 0, modifier = Modifier.fillMaxWidth()) {
            TimerAlarm.start(ctx, nombre, duracion, pantalla)
            onStarted()
        }
    }
}

/**
 * La pestaña Alarm (TD-200): la hora directa, en 12 horas con AM/PM como el reloj del app
 * (TD-183), y los minutos de uno en uno, "como una alarma normal". Debajo, cuándo suena -hoy o
 * mañana, y en cuánto- para confirmar de un vistazo lo que se puso: la cuenta la hace el app.
 *
 * Siempre a pantalla completa: para eso es una alarma. Suena una vez y se va de la franja, y no
 * se mezcla con las alarmas de la mañana del despertador.
 */
@Composable
private fun AlarmTab(accent: Color, t: Strings, onStarted: () -> Unit) {
    val ctx = LocalContext.current
    val w = t.more.timer
    val c = AppTheme.colors
    val zone = remember { ZoneId.systemDefault() }
    val recientes = remember { TimerStore(ctx).alarmRecents() }
    val inicial = remember { TimerPlan.defaultAlarmMinute(System.currentTimeMillis(), zone) }
    var hora12 by remember { mutableStateOf(TimerPlan.hour12(inicial / 60)) }
    var minuto by remember { mutableStateOf(inicial % 60) }
    var pm by remember { mutableStateOf(inicial >= 12 * 60) }
    var foco by remember { mutableStateOf(0) }
    var teclado by remember { mutableStateOf(false) }
    var arrastre by remember { mutableStateOf(0) }
    var nombre by remember { mutableStateOf("") }
    val ahora by produceState(System.currentTimeMillis()) { while (true) { delay(15_000); value = System.currentTimeMillis() } }
    val minutoDelDia = TimerPlan.to24(hora12, pm) * 60 + minuto
    val suena = TimerPlan.nextAt(minutoDelDia, ahora, zone)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            WheelField(
                hora12, 1..12, w.hour, foco == 0, accent, { foco = 0; arrastre++ }, { hora12 = it }, minWidth = 72.dp, format = { "$it" },
                keyboard = teclado, onTap = { foco = 0; teclado = true },
            )
            WheelColon()
            WheelField(
                minuto, 0..59, w.min, foco == 1, accent, { foco = 1; arrastre++ }, { minuto = it }, minWidth = 96.dp,
                keyboard = teclado, onTap = { foco = 1; teclado = true },
            )
            WheelKeyboard(
                teclado, foco, arrastre, listOf(1..12, 0..59),
                onValue = { col, v -> if (col == 0) hora12 = v else minuto = v },
                onColumn = { foco = it }, onClosed = { teclado = false },
            )
            AmPmToggle(pm, { pm = it })
        }
        Text(
            w.ringsIn.format(if (TimerPlan.isTomorrow(suena, ahora, zone)) w.tomorrow else w.today, TimerPlan.untilLabel(suena - ahora)),
            color = c.textDim, fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        if (recientes.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(w.recent, color = c.textDim, fontSize = 13.sp)
            // Un reciente suena de un toque, como los del timer: es una hora que se repite.
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                recientes.forEach { r ->
                    Box(
                        Modifier
                            .height(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.dp, accent, RoundedCornerShape(20.dp))
                            .clickable { TimerAlarm.startAt(ctx, r.name, r.minuteOfDay); onStarted() }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(listOf(TimerPlan.clock12(r.minuteOfDay), r.name).filter { it.isNotBlank() }.joinToString("  "), color = c.textPrimary, fontSize = 15.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        com.maurozegarra.master.ui.AppTextField(
            value = nombre,
            onValueChange = { nombre = it.take(40) },
            label = w.name,
            modifier = Modifier.fillMaxWidth(),
            accent = accent,
        )
        PrimaryButton(label = w.setAlarm, accent = accent, modifier = Modifier.fillMaxWidth()) {
            TimerAlarm.startAt(ctx, nombre, minutoDelDia)
            onStarted()
        }
    }
}
