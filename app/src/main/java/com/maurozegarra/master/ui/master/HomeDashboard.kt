package com.maurozegarra.master.ui.master

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.maurozegarra.master.R
import com.maurozegarra.master.i18n.Strings
import com.maurozegarra.master.model.Dashboard
import com.maurozegarra.master.morning.Bedtime
import com.maurozegarra.master.morning.Countdown
import com.maurozegarra.master.morning.MorningAlarm
import com.maurozegarra.master.morning.MorningHomeActivity
import com.maurozegarra.master.morning.MorningStore
import com.maurozegarra.master.ui.theme.AppTheme
import com.maurozegarra.master.ui.theme.Dims
import com.maurozegarra.master.water.WaterAlarm
import com.maurozegarra.master.water.WaterHomeActivity
import com.maurozegarra.master.water.WaterPlan
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DayStyle

private val Mono = FontFamily(Font(R.font.jetbrains_mono_digits))
private val HM = DateTimeFormatter.ofPattern("H:mm")

/**
 * El tablero de la pantalla principal (TD-194): el reloj y, debajo, la próxima alarma, el
 * agua y la hora de dormir. Va fijo abajo: no se mueve aunque se abra "Archived" o cambie el
 * training de arriba. Cada tarjeta abre su pantalla, y por eso la barra ya no lleva el
 * despertador ni la gota.
 */
@Composable
internal fun HomeDashboard(accent: Color, t: Strings, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        // Alto para la hora, la fecha y la regla con los segundos encima, sin que se toquen.
        CometClock(accent, Modifier.fillMaxWidth().height(170.dp))
        Spacer(Modifier.height(12.dp))
        DashboardTiles(t)
        // El timer (TD-195), al pie: el potty de un toque y el + para uno nuevo.
        Spacer(Modifier.height(8.dp))
        TimerStrip(accent, t)
    }
}

/**
 * La "regla con cometa" (prototipo aprobado el 9-oct): HH:MM grande; debajo, 60 marcas que un
 * cometa recorre con los segundos, dejando una estela que se apaga, con los segundos encima;
 * al cerrar el minuto la regla se vacía con un barrido. Los dos puntos laten (ver
 * [Dashboard.beat]).
 *
 * Se redibuja por cuadro, pero solo la capa de dibujo: el instante se lee dentro del Canvas,
 * así que no recompone nada más. A unos 30 cuadros por segundo, que para un cometa que avanza
 * 5 dp por segundo sobra y gasta la mitad que a 60.
 */
@Composable
private fun CometClock(accent: Color, modifier: Modifier) {
    val text = AppTheme.colors.textPrimary
    val dim = AppTheme.colors.textDim
    val track = AppTheme.colors.track
    val measurer = rememberTextMeasurer()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameMillis { frame ->
                if (frame - last >= 32) {
                    last = frame
                    now = System.currentTimeMillis()
                }
            }
        }
    }
    val zone = remember { ZoneId.systemDefault() }
    val bigStyle = TextStyle(fontFamily = Mono, fontSize = 72.sp, color = text)
    val secStyle = TextStyle(fontFamily = Mono, fontSize = 17.sp, color = accent)
    val dateStyle = TextStyle(fontSize = 14.sp, color = dim)

    Canvas(modifier) {
        val z = Instant.ofEpochMilli(now).atZone(zone)
        val ms = z.nano / 1_000_000
        val s = z.second + ms / 1000.0
        val (sx, sy) = Dashboard.burnInShift(z.minute)
        translate(sx.dp.toPx(), sy.dp.toPx()) {
            // La hora: horas y minutos por separado, con el latido en medio.
            val hh = "%02d".format(z.hour)
            val mm = "%02d".format(z.minute)
            val hLay = measurer.measure(hh, bigStyle)
            val mLay = measurer.measure(mm, bigStyle)
            val colonW = 26.dp.toPx()
            val total = hLay.size.width + colonW + mLay.size.width
            val left = (size.width - total) / 2
            drawText(hLay, topLeft = Offset(left, 0f))
            drawText(mLay, topLeft = Offset(left + hLay.size.width + colonW, 0f))
            val digitsH = hLay.size.height.toFloat()
            drawHeartbeatColon(
                cx = left + hLay.size.width + colonW / 2,
                cy = digitsH * 0.52f,
                gap = digitsH * 0.30f,
                r = 4.5.dp.toPx(),
                ms = ms, text = text, accent = accent,
            )

            // La regla, al pie del lienzo.
            val base = size.height - 4.dp.toPx()
            val x0 = 12.dp.toPx()
            val x1 = size.width - 12.dp.toPx()
            val step = (x1 - x0) / 59
            val tickW = 1.6.dp.toPx()
            for (i in 0 until 60) {
                val x = x0 + i * step
                var h = (if (i % 5 == 0) 10 else 6).dp.toPx()
                var color = track
                var alpha = 1f
                val behind = s - i
                if (behind >= 0) {
                    val trail = (1 - behind / Dashboard.TRAIL_SEC).coerceIn(0.0, 1.0).toFloat()
                    color = accent
                    alpha = 0.22f + 0.78f * trail
                    h += 10.dp.toPx() * trail
                }
                drawRect(color.copy(alpha = alpha), Offset(x - tickW / 2, base - h), androidx.compose.ui.geometry.Size(tickW, h))
            }
            // La cabeza del cometa y los segundos encima.
            val hx = x0 + minOf(s, 59.999).toFloat() * step
            val head = Offset(hx, base - 12.dp.toPx())
            val glow = 14.dp.toPx()
            drawCircle(
                Brush.radialGradient(listOf(accent.copy(alpha = 0.9f), accent.copy(alpha = 0f)), center = head, radius = glow),
                radius = glow, center = head,
            )
            val sLay = measurer.measure("%02d".format(z.second), secStyle)
            val sxText = (hx - sLay.size.width / 2).coerceIn(0f, size.width - sLay.size.width)
            drawText(sLay, topLeft = Offset(sxText, base - 30.dp.toPx() - sLay.size.height))
            // El barrido que vacía la regla al empezar el minuto.
            val w = Dashboard.wipe(s)
            if (w > 0f) {
                drawRect(
                    accent.copy(alpha = 0.35f * w),
                    Offset(x0, base - 24.dp.toPx()),
                    androidx.compose.ui.geometry.Size((x1 - x0) * w, 25.dp.toPx()),
                )
            }

            // La fecha, entre la hora y la regla.
            val fecha = z.dayOfWeek.getDisplayName(DayStyle.FULL, java.util.Locale.ENGLISH) + ", " +
                z.month.getDisplayName(DayStyle.SHORT, java.util.Locale.ENGLISH) + " " + z.dayOfMonth
            val dLay = measurer.measure(fecha, dateStyle)
            drawText(dLay, topLeft = Offset((size.width - dLay.size.width) / 2, digitsH - 6.dp.toPx()))
        }
    }
}

/**
 * Los dos puntos, latiendo: en cada golpe crecen, se tiñen de rojo y sueltan un halo; en
 * reposo quedan tenues, nunca apagados.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHeartbeatColon(
    cx: Float, cy: Float, gap: Float, r: Float, ms: Int, text: Color, accent: Color,
) {
    val b = Dashboard.beat(ms)
    val scale = 1 + 0.32f * b
    val rest = 0.38f
    for (y in listOf(cy - gap / 2, cy + gap / 2)) {
        val c = Offset(cx, y)
        if (b > 0.02f) {
            val halo = r * 3.2f * scale
            drawCircle(
                Brush.radialGradient(listOf(accent.copy(alpha = 0.55f * b), accent.copy(alpha = 0f)), center = c, radius = halo),
                radius = halo, center = c,
            )
        }
        drawCircle(lerp(text, accent, 0.85f * b).copy(alpha = rest + (1 - rest) * b), radius = r * scale, center = c)
    }
}

/** Lo que dicen las tres tarjetas en este momento. */
private data class TileData(val value: String, val caption: String, val dayLabel: String? = null)

/**
 * Las tres tarjetas. Se recalculan cada minuto y al volver a la pantalla: lo que muestran
 * cambia por minuto (cuánto falta) o cuando se anota algo en otra pantalla.
 */
@Composable
private fun DashboardTiles(t: Strings) {
    val ctx = LocalContext.current
    val d = t.more.dashboard
    var vuelta by remember { mutableIntStateOf(0) }
    val ciclo = LocalLifecycleOwner.current
    DisposableEffect(ciclo) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) vuelta++ }
        ciclo.lifecycle.addObserver(obs)
        onDispose { ciclo.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000)
            vuelta++
        }
    }

    val alarma = remember(vuelta) {
        val ring = MorningAlarm.nextRing(ctx)
        if (ring == null) TileData(d.off, " ")
        else TileData(
            ring.format(HM),
            t.morning.inTime.format(Countdown.text(Duration.between(ZonedDateTime.now(ring.zone), ring).toMinutes().toInt())),
            dayLabel = ring.dayOfWeek.getDisplayName(DayStyle.SHORT, java.util.Locale.ENGLISH),
        )
    }
    val agua = remember(vuelta) {
        val hoy = WaterAlarm.today(ctx)
        val total = WaterPlan.total(hoy.logs)
        val caption = when {
            total >= WaterPlan.GOAL_ML -> d.goalMet
            hoy.start == null -> d.notStarted
            else -> WaterAlarm.nextAt(ctx)?.let { d.next.format(it.format(HM)) } ?: " "
        }
        TileData(Dashboard.water(total), caption)
    }
    val cama = remember(vuelta) {
        val par = MorningAlarm.nextBedtime(ctx)
        if (par == null) TileData(d.off, " ")
        else {
            val (aviso, ring) = par
            val bed = Bedtime.bedBy(ring, MorningStore(ctx).bedtime())
            TileData(bed.format(HM), d.reminder.format(aviso.format(HM)))
        }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DashTile(Icons.Outlined.Alarm, alarma.dayLabel ?: d.alarm, alarma, Modifier.weight(1f)) { MorningHomeActivity.open(ctx) }
        DashTile(Icons.Outlined.WaterDrop, d.water, agua, Modifier.weight(1f)) { WaterHomeActivity.open(ctx) }
        DashTile(Icons.Outlined.Bedtime, d.bed, cama, Modifier.weight(1f)) { MorningHomeActivity.open(ctx) }
    }
}

@Composable
private fun DashTile(icon: ImageVector, label: String, data: TileData, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(Dims.card))
            .background(AppTheme.colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = AppTheme.colors.textDim, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, color = AppTheme.colors.textDim, fontSize = 12.sp, maxLines = 1)
        }
        Text(data.value, color = AppTheme.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = Mono, maxLines = 1)
        Text(data.caption, color = AppTheme.colors.textDim, fontSize = 11.sp, maxLines = 1)
    }
}
