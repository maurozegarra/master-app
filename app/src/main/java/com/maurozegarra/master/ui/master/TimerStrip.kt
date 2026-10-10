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
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
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
import com.maurozegarra.master.ui.AppSwitch
import com.maurozegarra.master.ui.theme.AppTheme
import com.maurozegarra.master.ui.theme.Dims
import kotlinx.coroutines.delay
import java.time.ZoneId

private val TimerMono = FontFamily(Font(R.font.jetbrains_mono_digits))

/**
 * La franja del timer, al pie del tablero (TD-195). El potty listo para arrancar de un toque
 * mientras no esté corriendo, cada timer en marcha con su cuenta, su hora de fin, pausa y
 * cancelar, y el + a la derecha, que abre el sheet de uno nuevo. El + no se mueve: va arriba a
 * la derecha, a la altura de la primera fila.
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

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(Dims.card))
                .background(AppTheme.colors.surface),
        ) {
            if (lista.none { it.name == TimerPlan.POTTY_NAME }) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clickable { TimerAlarm.start(ctx, TimerPlan.POTTY_NAME, TimerPlan.POTTY_MS, alarm = false) }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(TimerPlan.POTTY_NAME, color = AppTheme.colors.textPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Text(TimerPlan.format(TimerPlan.POTTY_MS), color = AppTheme.colors.textPrimary, fontSize = 18.sp, fontFamily = TimerMono)
                }
            }
            lista.forEach { run -> RunRow(run, now, zone, accent, t) }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .padding(top = 4.dp)
                .size(44.dp)
                .clip(CircleShape)
                .border(2.dp, accent, CircleShape)
                .clickable { sheet = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = w.newTimer, tint = accent)
        }
    }

    if (sheet) NewTimerSheet(accent, t, onDismiss = { sheet = false })
}

@Composable
private fun RunRow(run: TimerRun, now: Long, zone: ZoneId, accent: Color, t: Strings) {
    val ctx = LocalContext.current
    val w = t.more.timer
    val c = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(start = 14.dp, end = 4.dp),
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
        StripIcon(if (run.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, if (run.paused) w.resume else w.pause, accent) {
            if (run.paused) TimerAlarm.resume(ctx, run.id) else TimerAlarm.pause(ctx, run.id)
        }
        StripIcon(Icons.Filled.Close, w.cancel, c.textDim) { TimerAlarm.cancel(ctx, run.id) }
    }
}

@Composable
private fun StripIcon(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/**
 * El sheet de un timer nuevo, en el orden de su Galaxy Watch8 (9-oct): arriba "+ Custom", que
 * despliega el teclado, los chips de "Ends at", el nombre y "Ring as alarm"; después los cinco
 * recientes y los presets. Un reciente o un preset arranca de un toque.
 */
@Composable
private fun NewTimerSheet(accent: Color, t: Strings, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val w = t.more.timer
    val c = AppTheme.colors
    val recientes = remember { TimerStore(ctx).recents() }
    var custom by remember { mutableStateOf(false) }

    // Sin título, como los de Telegram (9-oct). Con Custom abierto crece: el teclado no cabe
    // en el alto normal.
    com.maurozegarra.master.ui.AppSheet(onDismiss = onDismiss, tall = custom) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
            if (custom) CustomPanel(accent, t, onStarted = onDismiss)

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
 * El teclado de su mini-timer (los dígitos entran por la derecha), los chips de "Ends at", el
 * nombre y "Ring as alarm". El interruptor se prende solo al elegir una hora de fin y se apaga
 * al teclear una duración (decisión 1A del 9-oct); después se puede cambiar a mano.
 */
@Composable
private fun CustomPanel(accent: Color, t: Strings, onStarted: () -> Unit) {
    val ctx = LocalContext.current
    val w = t.more.timer
    val c = AppTheme.colors
    val zone = remember { ZoneId.systemDefault() }
    var digitos by remember { mutableStateOf("") }
    var finEn by remember { mutableStateOf<Long?>(null) }
    var alarma by remember { mutableStateOf(false) }
    var nombre by remember { mutableStateOf("") }
    val chips = remember { TimerPlan.endsAtChips(System.currentTimeMillis()) }

    fun teclear(d: String) {
        digitos = (digitos + d).trimStart('0').takeLast(6)
        if (finEn != null) { finEn = null; alarma = false }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // La pantalla del teclado: HH:MM:SS, o la hora de fin elegida.
        val d = digitos.padStart(6, '0')
        Text(
            finEn?.let { "${w.endsAtLabel} ${TimerPlan.clock(it, zone)}" } ?: "${d.substring(0, 2)}:${d.substring(2, 4)}:${d.substring(4, 6)}",
            color = if (digitos.isEmpty() && finEn == null) c.textDim else c.textPrimary,
            fontSize = 36.sp, fontFamily = if (finEn == null) TimerMono else null,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
        val teclas = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "<"))
        teclas.forEach { fila ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                fila.forEach { k ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (k.isEmpty()) Color.Transparent else c.track)
                            .then(
                                if (k.isEmpty()) Modifier
                                else Modifier.clickable { if (k == "<") digitos = digitos.dropLast(1) else teclear(k) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (k == "<") Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = null, tint = c.textPrimary)
                        else Text(k, color = c.textPrimary, fontSize = 20.sp, fontFamily = TimerMono)
                    }
                }
            }
        }

        Text(w.endsAtLabel, color = c.textDim, fontSize = 13.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            chips.forEach { fin ->
                val elegido = finEn == fin
                Box(
                    Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .then(if (elegido) Modifier.background(accent) else Modifier.border(1.dp, accent, RoundedCornerShape(20.dp)))
                        .clickable { finEn = fin; digitos = ""; alarma = true }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(TimerPlan.clock(fin, zone), color = if (elegido) c.onAccent else c.textPrimary, fontSize = 15.sp, fontFamily = TimerMono)
                }
            }
        }

        OutlinedTextField(
            value = nombre,
            onValueChange = { nombre = it.take(40) },
            label = { Text(w.name) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accent, focusedLabelColor = accent, cursorColor = accent,
                focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary,
                unfocusedBorderColor = c.textFaded, unfocusedLabelColor = c.textDim,
            ),
        )

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(w.ringAsAlarm, color = c.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
            AppSwitch(alarma, accent) { alarma = it }
        }

        val duracion = finEn?.let { it - System.currentTimeMillis() } ?: TimerPlan.digitsToMs(digitos)
        val listo = duracion > 0
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(if (listo) accent else c.track)
                .clickable(enabled = listo) {
                    TimerAlarm.start(ctx, nombre, finEn?.let { it - System.currentTimeMillis() } ?: duracion, alarma)
                    onStarted()
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(w.start, color = if (listo) c.onAccent else c.textDim, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}
