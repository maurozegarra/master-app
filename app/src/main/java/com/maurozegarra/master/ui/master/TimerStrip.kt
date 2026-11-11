package com.maurozegarra.master.ui.master

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.drawWithContent
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
 * Sessions | Body), y se abre en la última que se usó. Las dos con el mismo alto, el de Alarm
 * (TD-211): cambiar de pestaña no hace saltar la hoja.
 */
@Composable
private fun NewTimerSheet(accent: Color, t: Strings, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val w = t.more.timer
    val store = remember { TimerStore(ctx) }
    var alarma by remember { mutableStateOf(store.alarmTab) }

    // Sin título, como los de Telegram (9-oct).
    com.maurozegarra.master.ui.AppSheet(onDismiss = onDismiss, tall = true) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = SHEET_PAD, end = SHEET_PAD, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SegmentToggle(
                options = listOf("timer" to w.timer, "alarm" to w.alarm),
                selected = if (alarma) "alarm" else "timer",
                accent = accent,
            ) { alarma = it == "alarm"; store.alarmTab = alarma }

            // El alto lo pone SIEMPRE Alarm (TD-211): se mide aunque se esté en Timer, y Timer
            // ocupa ese mismo alto con su propio scroll. El primer intento solo subió el tope de
            // la hoja, y cada pestaña seguía midiendo lo que su contenido: Timer llegaba al 92 %
            // y Alarm se quedaba en ~55 %. La que no se ve se mide pero no se coloca: ni se
            // dibuja ni recibe toques.
            androidx.compose.ui.layout.Layout(
                content = {
                    AlarmTab(accent, t, onStarted = onDismiss)
                    // Una barrita de scroll al costado mientras haya más de lo que entra (10-nov). Se
                    // probó desvanecer el pie, pero el aviso dependía de lo que cayera en el borde:
                    // con dos recientes caía el hueco antes de Presets y no se veía nada. La barrita
                    // avisa siempre, y dice cuánto falta. A todo el ancho de la hoja, con su margen
                    // por dentro: así va pegada al borde de la pantalla, como en Chrome o Telegram.
                    val desliza = rememberScrollState()
                    val barra = AppTheme.colors.textDim
                    Column(
                        Modifier
                            .toSheetEdge()
                            .drawWithContent {
                                drawContent()
                                val total = desliza.maxValue
                                if (total in 1 until Int.MAX_VALUE) {
                                    val alto = size.height
                                    val largo = maxOf(alto * alto / (alto + total), 24.dp.toPx())
                                    val y = (alto - largo) * desliza.value / total
                                    val ancho = 3.dp.toPx()
                                    drawRoundRect(
                                        barra.copy(alpha = 0.6f),
                                        topLeft = androidx.compose.ui.geometry.Offset(size.width - ancho - 4.dp.toPx(), y),
                                        size = androidx.compose.ui.geometry.Size(ancho, largo),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(ancho / 2),
                                    )
                                }
                            }
                            .verticalScroll(desliza)
                            .padding(horizontal = SHEET_PAD),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TimerTab(accent, t, onStarted = onDismiss)
                    }
                },
            ) { medibles, limites ->
                val ancho = limites.maxWidth
                val deAlarma = medibles[0].measure(androidx.compose.ui.unit.Constraints(minWidth = ancho, maxWidth = ancho))
                val alto = deAlarma.height
                val deTimer = if (alarma) null else medibles[1].measure(androidx.compose.ui.unit.Constraints.fixed(ancho, alto))
                layout(ancho, alto) { (deTimer ?: deAlarma).place(0, 0) }
            }
        }
    }
}

/**
 * La pestaña Timer (TD-211): todo arranca de un toque, y lo de arriba -cómo termina y el
 * nombre- vale para lo que se toque debajo. Fuera "+ Custom" con su rueda: para él bastaba
 * con que volviera "Ends at", y las duraciones las cubren los presets.
 */
@Composable
private fun TimerTab(accent: Color, t: Strings, onStarted: () -> Unit) {
    val ctx = LocalContext.current
    val w = t.more.timer
    val c = AppTheme.colors
    val zone = remember { ZoneId.systemDefault() }
    val store = remember { TimerStore(ctx) }
    var recientes by remember { mutableStateOf(store.recents()) }
    var pantalla by remember { mutableStateOf(false) }
    var nombre by remember { mutableStateOf("") }
    val ahora by produceState(System.currentTimeMillis()) { while (true) { delay(15_000); value = System.currentTimeMillis() } }
    val swipe = rememberSwipeRowsController()

    fun arrancar(ms: Long, suNombre: String = "") {
        TimerAlarm.start(ctx, nombre.trim().ifBlank { suNombre }, ms, pantalla)
        onStarted()
    }

    // Un switch y no el selector de pestañas (TD-211, 10-nov): con el mismo selector que
    // Timer | Alarm, "Notification" competía con "Timer". Es un sí o no; apagado, notificación.
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { pantalla = !pantalla }
            .padding(top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(w.endFullScreen, color = c.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        // Sin la zona mínima de 48 dp del switch: aquí la fila entera es la que se toca, y esos
        // 48 dp eran casi todo el aire que sobraba (10-nov).
        @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement provides false,
        ) {
            com.maurozegarra.master.ui.AppSwitch(pantalla, accent, outlined = true) { pantalla = it }
        }
    }
    NameField(nombre, { nombre = it }, accent, t)

    Text(w.endsAtLabel, color = c.textDim, fontSize = 13.sp)
    Row(Modifier.toSheetEdge().horizontalScroll(rememberScrollState()).padding(horizontal = SHEET_PAD), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TimerPlan.endsAtChips(ahora).forEach { fin ->
            OutlineChip(TimerPlan.clock12(fin, zone), accent) { arrancar(fin - System.currentTimeMillis()) }
        }
    }

    if (recientes.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text(w.recent, color = c.textDim, fontSize = 13.sp)
        // Borrar es deslizar, como un timer de la franja (TD-211).
        recientes.forEach { r ->
            key(r) {
                SwipeActionsRow(
                    actions = listOf(
                        SwipeAction(Icons.Outlined.Delete, t.delete) {
                            recientes = recientes - r
                            store.saveRecents(recientes)
                        },
                    ),
                    controller = swipe,
                    outlined = false,
                ) {
                    RecentRow(r, accent, t) { arrancar(r.durationMs, r.name) }
                }
            }
        }
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
                        .clickable { arrancar(sec * 1000L) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(TimerPlan.presetLabel(sec), color = c.textPrimary, fontSize = 15.sp)
                }
            }
        }
    }
}

/**
 * El nombre, que se usa poco (TD-211): un "+ Name" chico, y el campo solo cuando se toca. Con
 * algo escrito se queda abierto.
 */
@Composable
private fun NameField(value: String, onChange: (String) -> Unit, accent: Color, t: Strings) {
    val w = t.more.timer
    var abierto by remember { mutableStateOf(value.isNotEmpty()) }
    if (!abierto) {
        // El "+" como texto y no como ícono: el ícono trae margen propio y el + quedaba más
        // adentro que los títulos de la hoja (10-nov).
        Text(
            androidx.compose.ui.text.buildAnnotatedString {
                pushStyle(androidx.compose.ui.text.SpanStyle(color = accent, fontWeight = FontWeight.Bold, fontSize = 18.sp))
                append("+  ")
                pop()
                append(w.nameShort)
            },
            color = AppTheme.colors.textPrimary,
            fontSize = 15.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { abierto = true }
                .padding(end = 8.dp, top = 8.dp, bottom = 8.dp),
        )
        return
    }
    val pedir = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { pedir.requestFocus() }
    com.maurozegarra.master.ui.AppTextField(
        value = value,
        onValueChange = { onChange(it.take(40)) },
        label = w.name,
        modifier = Modifier.fillMaxWidth().focusRequester(pedir),
        accent = accent,
    )
}

/** El margen de la hoja del +. Los chips lo cruzan para llegar al borde ([toSheetEdge]). */
private val SHEET_PAD = 16.dp

/**
 * Una fila que se desliza de lado llega hasta el borde de la pantalla, por encima del margen de
 * la hoja: cortada en el margen, el último chip parecía un error y no algo que sigue (10-nov).
 * Dentro lleva el mismo margen, así el primer chip queda alineado con los títulos.
 */
private fun Modifier.toSheetEdge(): Modifier = this.layout { medible, limites ->
    val margen = SHEET_PAD.roundToPx()
    val ancho = limites.maxWidth + margen * 2
    val p = medible.measure(limites.copy(minWidth = ancho, maxWidth = ancho))
    layout(limites.maxWidth, p.height) { p.place(-margen, 0) }
}

/**
 * Un chip con borde: los Ends at y los recientes de la alarma. [onLongClick], si lo trae, abre
 * un menú con Delete (TD-211).
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun OutlineChip(text: String, accent: Color, onDelete: (() -> Unit)? = null, t: Strings? = null, onClick: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, accent, RoundedCornerShape(20.dp))
                .combinedClickable(onClick = onClick, onLongClick = onDelete?.let { { menu = true } })
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, color = AppTheme.colors.textPrimary, fontSize = 15.sp, maxLines = 1)
        }
        if (onDelete != null && t != null) {
            androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(t.delete) },
                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                    onClick = { menu = false; onDelete() },
                )
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
            .padding(end = 4.dp, top = 10.dp, bottom = 10.dp),
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
    val store = remember { TimerStore(ctx) }
    var recientes by remember { mutableStateOf(store.alarmRecents()) }
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
            // Un reciente suena de un toque, como los del timer: es una hora que se repite. Con
            // un toque largo se borra (TD-211): son chips, no filas que deslizar.
            Row(Modifier.toSheetEdge().horizontalScroll(rememberScrollState()).padding(horizontal = SHEET_PAD), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                recientes.forEach { r ->
                    key(r) {
                        OutlineChip(
                            listOf(TimerPlan.clock12(r.minuteOfDay), r.name).filter { it.isNotBlank() }.joinToString("  "),
                            accent,
                            onDelete = { recientes = recientes - r; store.saveAlarmRecents(recientes) },
                            t = t,
                        ) { TimerAlarm.startAt(ctx, r.name, r.minuteOfDay); onStarted() }
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        NameField(nombre, { nombre = it }, accent, t)
        PrimaryButton(label = w.setAlarm, accent = accent, modifier = Modifier.fillMaxWidth()) {
            TimerAlarm.startAt(ctx, nombre, minutoDelDia)
            onStarted()
        }
    }
}
