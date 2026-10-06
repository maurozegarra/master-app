package com.maurozegarra.master.ui.master

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maurozegarra.master.data.ExerciseIcons
import com.maurozegarra.master.i18n.Strings
import com.maurozegarra.master.model.SetRecord
import com.maurozegarra.master.ui.AppOutlineButton
import com.maurozegarra.master.ui.AppPrimaryButton
import com.maurozegarra.master.ui.AppStepper
import com.maurozegarra.master.ui.WheelTimePicker
import com.maurozegarra.master.ui.theme.AppTheme
import com.maurozegarra.master.ui.theme.FEEL_DOWN
import com.maurozegarra.master.ui.theme.FEEL_STEADY
import com.maurozegarra.master.ui.theme.FEEL_UP
import com.maurozegarra.master.ui.theme.STATUS_SKIPPED
import com.maurozegarra.master.ui.theme.Dims

/** Paleta de colores para etapas (ARGB Long), igual orden que en los mocks. */
internal val STAGE_COLORS: List<Long> = listOf(
    0xFFE2641EL, 0xFFEFAA2AL, 0xFF2E9E5BL, 0xFF159E8CL,
    0xFF1565C0L, 0xFF5E48C8L, 0xFFB4318FL, 0xFFC0392BL,
    0xFF455A64L, 0xFF6D4C41L,
)

/** Una duración, con el formato único de [TrainingPreview.duration]: "45 s", "5 min", "1:30". */
internal fun fmtSec(s: Int): String = com.maurozegarra.master.model.TrainingPreview.duration(s)

/** Un decimal solo cuando hace falta: 6.0 -> "6", 6.5 -> "6.5". Vale para kilos y km/h. */
internal fun fmtNum(d: Double): String {
    val r = (d * 10).toLong()
    return if (r % 10 == 0L) (r / 10).toString() else (r / 10.0).toString()
}

internal fun fmtKg(d: Double): String = fmtNum(d)

/**
 * Lo que dice una serie, en corto: "12 × 6 kg", "12 reps", "0:10" o "0:30 · 6 kg" (TD-118).
 *
 * Antes era "Set 1 · 12 reps · 6 kg": el "Set", los puntos y el "reps" se repetían en cada
 * fila sin decir nada que la columna no dijera ya. En las series por tiempo ya no sale el
 * número de reps, que ahí no mide nada -en McGill decía "10 reps · 10s" en cada aguante-.
 */
internal fun setSummary(sr: SetRecord, timeBased: Boolean, t: Strings): String {
    val kg = if (sr.weightKg > 0) "${fmtKg(sr.weightKg)} ${t.kg}" else null
    val kmh = sr.speedKmh?.let { "${fmtNum(it)} ${t.kmh}" }
    // Lo que salio de verdad, contra lo planeado, cuando no fue igual (TD-152): "6/8 reps",
    // "18s/25s". Es lo que dice que una serie costo sin tener que leer el icono.
    // En distancia, los metros (TD-095): "36 m × 17.5 kg" y no "2 × 17.5 kg".
    if (sr.distanceM != null) return listOfNotNull("${sr.distanceM} ${t.distance.unit}", kg).joinToString(" \u00d7 ")
    val reps = sr.repsDone?.let { "$it/${sr.reps}" } ?: "${sr.reps}"
    val tiempo = sr.plannedSec?.let { "${fmtSec(sr.durationSec)}/${fmtSec(it)}" } ?: fmtSec(sr.durationSec)
    return when {
        timeBased -> listOfNotNull(tiempo, kmh, kg).joinToString(" \u00b7 ")
        kg != null -> "$reps \u00d7 $kg"
        else -> "$reps ${t.repLabel}"
    }
}

/**
 * Una serie del historial en una línea: número, lo que se hizo y cómo se sintió (TD-118).
 *
 * El feedback va en ÍCONO -↑ ligero en verde, ✓ justo en ámbar, ↓ pesado en rojo; ver
 * [FEEL_UP]- y no en palabras: "Too light ↑" en
 * cada fila cargaba la lista de texto. Son las mismas flechas que los botones del player,
 * que es donde se aprende qué significan; aquí ya solo se reconoce lo que se tocó. Las
 * palabras siguen como descripción de accesibilidad. Sin marcar, no sale nada: no marcar
 * no es "justo".
 */
@Composable
internal fun SetLine(index: Int, sr: SetRecord, timeBased: Boolean, t: Strings, fontSize: TextUnit, lastIndex: Int = index) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Un tramo de series iguales lleva su rango, "1–12" (TD-181); por eso el ancho es un
        // minimo y no fijo, con el respiro detras.
        Text(
            if (lastIndex > index) "${index + 1}–${lastIndex + 1}" else "${index + 1}",
            color = AppTheme.colors.textFaded,
            fontSize = fontSize,
            modifier = Modifier.widthIn(min = 20.dp).padding(end = 6.dp),
        )
        Text(setSummary(sr, timeBased, t), color = AppTheme.colors.textDim, fontSize = fontSize)
        // El peso (TD-117) o, en lo que no lo lleva, como fue (TD-152): mismas flechas y mismos
        // colores. Un solo icono por serie, porque una serie tiene una de las dos cosas.
        val feel = sr.feedbackDeltaKg ?: sr.effort?.toDouble()
        if (feel != null) {
            Spacer(Modifier.width(8.dp))
            Icon(
                when {
                    feel < 0 -> Icons.Filled.ArrowDownward
                    feel > 0 -> Icons.Filled.ArrowUpward
                    else -> Icons.Filled.Check
                },
                contentDescription = when {
                    feel < 0 -> t.effort.tooHeavy
                    feel > 0 -> t.effort.tooLight
                    else -> t.effort.justRight
                },
                tint = when {
                    feel < 0 -> FEEL_DOWN
                    feel > 0 -> FEEL_UP
                    else -> FEEL_STEADY
                },
                modifier = Modifier.size(14.dp),
            )
        }
        if (sr.skipped) {
            Spacer(Modifier.width(6.dp))
            StatusBadge(text = t.skipped, color = STATUS_SKIPPED)
        }
    }
}

/** Las series de un ejercicio del historial, en tramos de series iguales (TD-181). */
@Composable
internal fun SetLines(er: com.maurozegarra.master.model.ExerciseRecord, t: Strings, fontSize: TextUnit) {
    com.maurozegarra.master.model.SetRuns.of(er.sets).forEach { r ->
        SetLine(r.first, r.set, er.timeBased, t, fontSize, lastIndex = r.last)
    }
}

/** "rest 3 s–30 s": los descansos que pasaron entre series, como los dice la previa (TD-181). */
internal fun restLabel(er: com.maurozegarra.master.model.ExerciseRecord, t: Strings): String? =
    com.maurozegarra.master.model.TrainingPreview.rest(
        com.maurozegarra.master.model.SetRuns.rests(er.sets),
        com.maurozegarra.master.model.PreviewWords(rest = t.more.preview.rest),
    )

@Composable
internal fun PrimaryButton(
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
) = AppPrimaryButton(label = label, accent = accent, modifier = modifier, enabled = enabled, icon = icon, onClick = onClick)

@Composable
internal fun AddButton(
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) = AppOutlineButton(label = "+  $label", accent = accent, modifier = modifier, onClick = onClick)

@Composable
internal fun Stepper(
    label: String,
    value: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    min: Int = 0,
    max: Int = 3600,
    step: Int = 1,
    format: (Int) -> String = { it.toString() },
    onChange: (Int) -> Unit,
) = AppStepper(label, value, accent, modifier, min, max, step, format, onChange)

/**
 * Campo de duracion con rueda.
 *
 * [dim] atenua el valor para decir "esto no lo pusiste tu, lo hereda de otro sitio", y
 * [trailing] deja colgar una accion a la derecha (hoy, quitar ese valor propio). Ambos
 * llegan con valor por defecto: las llamadas que no los necesitan siguen escritas igual.
 */
@Composable
internal fun DurationWheelField(
    label: String,
    value: Int,
    accent: Color,
    t: Strings,
    modifier: Modifier = Modifier,
    min: Int = 0,
    max: Int = 36000,
    dim: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onChange: (Int) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = AppTheme.colors.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(AppTheme.colors.track)
                .clickable { editing = true }
                .padding(horizontal = 18.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                fmtSec(value),
                color = if (dim) AppTheme.colors.textDim else AppTheme.colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
            )
        }
        trailing?.let {
            Spacer(Modifier.size(8.dp))
            it()
        }
    }
    if (editing) {
        DurationWheelDialog(
            value = value, min = min, max = max, accent = accent, t = t,
            onDismiss = { editing = false },
            onConfirm = { onChange(it); editing = false },
        )
    }
}

@Composable
private fun DurationWheelDialog(
    value: Int,
    min: Int,
    max: Int,
    accent: Color,
    t: Strings,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var h by remember { mutableStateOf(value / 3600) }
    var m by remember { mutableStateOf((value % 3600) / 60) }
    var s by remember { mutableStateOf(value % 60) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppTheme.colors.surface,
        titleContentColor = AppTheme.colors.textPrimary,
        title = { Text(t.durationTitle) },
        text = {
            WheelTimePicker(
                h = h, m = m, s = s, accent = accent, t = t,
                onChange = { nh, nm, ns -> h = nh; m = nm; s = ns },
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm((h * 3600 + m * 60 + s).coerceIn(min, max))
            }) { Text(t.save, color = accent, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t.cancel, color = AppTheme.colors.textDim) }
        },
    )
}

/**
 * Elegir una hora del dia con un reloj de 12 horas y AM/PM (TD-183): la hora y los minutos
 * arriba, y el dial para marcarlos.
 *
 * Era el TimePickerDialog de Android en 24 horas, con los colores del sistema. El usuario lo
 * pidio el 1-oct con la captura de otra app: *"no me gusta el formato 24 horas"*.
 *
 * **Hecho a mano y no con el TimePicker de material3**, que fue lo primero y tumbaba el app al
 * abrirse (NoSuchMethodError sobre maybeCachedBoxMeasurePolicy). Es lo mismo que paso con
 * PullToRefreshContainer en TD-068: foundation esta clavada en 1.6.8 por el
 * resolutionStrategy.force de TD-030 y el resto de Compose va en 1.10; un componente de
 * material3 que no se ha usado nunca hay que probarlo en el telefono antes de darlo por
 * bueno. Este usa solo Canvas, gestos y Text, que el app ya usa.
 */
@Composable
internal fun ClockTimeDialog(
    initial: java.time.LocalTime,
    accent: Color,
    t: Strings,
    onDismiss: () -> Unit,
    onConfirm: (java.time.LocalTime) -> Unit,
) {
    val c = AppTheme.colors
    var hora by remember { mutableStateOf(initial.hour) }
    var minuto by remember { mutableStateOf(initial.minute) }
    // Primero la hora; al soltar el dial pasa solo a los minutos, como en el reloj de Android.
    var enMinutos by remember { mutableStateOf(false) }
    val pm = hora >= 12
    val hora12 = (hora % 12).let { if (it == 0) 12 else it }
    AlertDialog(
        onDismissRequest = onDismiss,
        // Mas ancho que el dialogo por defecto: el dial y la cabecera no caben en 280 dp.
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(horizontal = 24.dp),
        containerColor = c.surface,
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ClockField("%02d".format(hora12), !enMinutos, accent) { enMinutos = false }
                    Text(":", color = c.textPrimary, fontSize = 44.sp, modifier = Modifier.padding(horizontal = 6.dp))
                    ClockField("%02d".format(minuto), enMinutos, accent) { enMinutos = true }
                    Spacer(Modifier.width(12.dp))
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, c.textFaded, RoundedCornerShape(10.dp)),
                    ) {
                        ClockPeriod("AM", !pm, accent) { if (pm) hora -= 12 }
                        ClockPeriod("PM", pm, accent) { if (!pm) hora += 12 }
                    }
                }
                Spacer(Modifier.height(24.dp))
                ClockDial(
                    value = if (enMinutos) minuto else hora12,
                    minutes = enMinutos,
                    accent = accent,
                    onChange = { v -> if (enMinutos) minuto = v else hora = (v % 12) + if (pm) 12 else 0 },
                    onRelease = { if (!enMinutos) enMinutos = true },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(java.time.LocalTime.of(hora, minuto)) }) {
                Text(t.save, color = accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t.cancel, color = c.textDim) }
        },
    )
}

/** La hora o los minutos de la cabecera del reloj; tocarlo lo pone en el dial. */
@Composable
private fun ClockField(text: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    val c = AppTheme.colors
    Box(
        Modifier
            .size(width = 88.dp, height = 72.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) accent else c.track)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) c.onAccent else c.textPrimary, fontSize = 44.sp)
    }
}

@Composable
private fun ClockPeriod(text: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    val c = AppTheme.colors
    Box(
        Modifier
            .size(width = 52.dp, height = 36.dp)
            .background(if (selected) accent else c.track)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) c.onAccent else c.textDim, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * El dial: doce marcas -las horas, o los minutos de 5 en 5- y la aguja hasta lo elegido.
 * Se toca o se arrastra; los minutos van de uno en uno aunque solo se rotulen de 5 en 5.
 */
@Composable
private fun ClockDial(value: Int, minutes: Boolean, accent: Color, onChange: (Int) -> Unit, onRelease: () -> Unit) {
    val c = AppTheme.colors
    // Lo que marca un punto del dial: el angulo desde las 12, en el sentido del reloj.
    fun valueAt(p: androidx.compose.ui.geometry.Offset, lado: Float): Int {
        var grados = Math.toDegrees(kotlin.math.atan2((p.x - lado / 2).toDouble(), (lado / 2 - p.y).toDouble()))
        if (grados < 0) grados += 360.0
        return if (minutes) Math.round(grados / 6).toInt() % 60
        else Math.round(grados / 30).toInt().let { if (it % 12 == 0) 12 else it % 12 }
    }
    Box(
        Modifier
            .size(256.dp)
            .clip(CircleShape)
            .background(c.track)
            .pointerInput(minutes) {
                detectTapGestures { onChange(valueAt(it, size.width.toFloat())); onRelease() }
            }
            .pointerInput(minutes) {
                detectDragGestures(
                    onDragStart = { onChange(valueAt(it, size.width.toFloat())) },
                    onDragEnd = onRelease,
                ) { cambio, _ -> onChange(valueAt(cambio.position, size.width.toFloat())) }
            },
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val r = 100.dp.toPx()
            val a = Math.toRadians(value * if (minutes) 6.0 else 30.0)
            val punta = androidx.compose.ui.geometry.Offset(
                center.x + (r * kotlin.math.sin(a)).toFloat(),
                center.y - (r * kotlin.math.cos(a)).toFloat(),
            )
            drawLine(accent, center, punta, strokeWidth = 2.dp.toPx())
            drawCircle(accent, radius = 4.dp.toPx(), center = center)
            drawCircle(accent, radius = 22.dp.toPx(), center = punta)
        }
        val marcas = if (minutes) (0 until 12).map { it * 5 } else listOf(12) + (1..11)
        marcas.forEachIndexed { i, n ->
            val a = Math.toRadians(i * 30.0)
            Text(
                n.toString(),
                color = if (n == value) c.onAccent else c.textPrimary,
                fontSize = 16.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = (100 * kotlin.math.sin(a)).dp, y = (-100 * kotlin.math.cos(a)).dp),
            )
        }
    }
}

/** Conjunto de chips tipo segmented control. */
@Composable
internal fun SegmentToggle(
    options: List<Pair<String, String>>,
    selected: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppTheme.colors.track)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // La opción elegida, solo con borde, como el Start y el play (5-oct): relleno de acento
        // competía con lo demás de la pantalla. El riel gris se queda para que se lea como un
        // solo control.
        options.forEach { (key, text) ->
            val active = key == selected
            val forma = RoundedCornerShape(10.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(forma)
                    .then(if (active) Modifier.border(1.dp, accent, forma) else Modifier)
                    .clickable { onSelect(key) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text,
                    color = if (active) accent else AppTheme.colors.textDim,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

/**
 * La tarjeta de una fila de lista: la de un training, y la de una alarma (TD-175). Mismo
 * radio, fondo, contorno y relleno, para que todo lo que es "un elemento" se vea igual.
 */
@Composable
internal fun Modifier.listCard(): Modifier = this
    .fillMaxWidth()
    .clip(RoundedCornerShape(Dims.row))
    .background(AppTheme.colors.surface)
    // El mismo radio que el clip: si no, el contorno se dibuja por fuera de la forma
    // recortada y las esquinas se ven dobles.
    .border(1.dp, AppTheme.colors.textDim.copy(alpha = 0.3f), RoundedCornerShape(Dims.row))
    .padding(start = Dims.rowPadding, end = Dims.rowPaddingEnd, top = 12.dp, bottom = 12.dp)

/**
 * Un día en un círculo: el de la semana de la lista de trainings, y los días de una alarma
 * (TD-175). Marcado, lleno del acento; si no, solo el contorno.
 */
@Composable
internal fun DayCircle(text: String, selected: Boolean, accent: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(if (selected) accent else Color.Transparent)
            .border(1.dp, if (selected) accent else AppTheme.colors.track, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) AppTheme.colors.onAccent else AppTheme.colors.textPrimary,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun SectionCard(
    modifier: Modifier = Modifier,
    /** El margen derecho. [Dims.rowPaddingEnd] cuando se apila con filas de lista (TD-160). */
    endPadding: androidx.compose.ui.unit.Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dims.card))
            .background(AppTheme.colors.surface)
            .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = endPadding),
    ) { content() }
}

@Composable
internal fun ColorDot(color: Long, size: Int = 18, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color(color)),
    )
}

/**
 * Etiqueta de estado: COMPLETE, PARTIAL, SKIPPED, ASSIGNED, IN PROGRESS, rotativo.
 *
 * Existe porque había **seis escritas a mano** repitiendo la misma receta, y no salían
 * iguales: cinco con esquina de 4dp —rectángulos— y una con 20 —pastilla—. Esa diferencia
 * se veía.
 *
 * Pastilla para todas, y con [CircleShape] sobre una caja que se ajusta al contenido, no
 * con un radio a ojo: así los extremos quedan completamente redondeados sea cual sea el
 * alto, y no hay ningún número que volver a cuadrar si cambia el tamaño del texto.
 *
 * El color llega por parámetro porque lo decide el estado, no la etiqueta. El texto nunca
 * se parte: si un día no cabe, que se recorte y se note, en vez de romperse en dos líneas
 * y estirar en silencio la tarjeta que lo contiene.
 */
@Composable
internal fun StatusBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
internal fun ExerciseGlyph(name: String, color: Long, sizeDp: Int = 44, exerciseId: String = "") {
    val emoji = ExerciseIcons.emoji(exerciseId, name)
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(color).copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center,
    ) {
        if (emoji != null) {
            Text(emoji, fontSize = (sizeDp / 1.9).sp)
        } else {
            Text(
                name.trim().take(1).uppercase().ifBlank { "?" },
                color = Color(color),
                fontWeight = FontWeight.Bold,
                fontSize = (sizeDp / 2.2).sp,
            )
        }
    }
}

@Composable
internal fun VSpace(h: Int) = Spacer(Modifier.height(h.dp))

/**
 * Cuántos minutos tardó en aflojar el dolor de la mañana (TD-125). Lo usan la sesión y,
 * desde TD-176, la pantalla Morning para corregirlos.
 *
 * Opciones sueltas y no un contador: nadie mide esto con cronómetro, se dice "unos quince".
 * Los valores están elegidos para que el paso normal -entre 10 y 15 minutos- se conteste de
 * un toque, y para que la cifra que cambiaría el cuadro -45 o más- exista y se pueda marcar.
 */
@Composable
internal fun FadeMinutes(value: Int?, accent: Color, t: Strings, onPick: (Int) -> Unit) {
    // Lo que viene de la alarma son minutos EXACTOS -24- y no uno de los botones: sin decirlo
    // aqui, ningun boton se encendia y parecia que no habia dato (24-sep, TD-156).
    val exacto = value?.takeIf { it !in listOf(0, 5, 10, 15, 20, 30, 45, 60) }
    Text(
        if (exacto != null) "${t.painFadeMin}: $exacto min" else t.painFadeMin,
        color = if (exacto != null) AppTheme.colors.textPrimary else AppTheme.colors.textDim,
        fontSize = 13.sp,
    )
    Spacer(Modifier.height(6.dp))
    listOf(listOf(0, 5, 10, 15), listOf(20, 30, 45, 60)).forEach { fila ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            fila.forEach { min ->
                val activo = value == min
                Box(
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (activo) accent else AppTheme.colors.track)
                        .clickable { onPick(min) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (min == 60) "60+" else "$min",
                        color = if (activo) AppTheme.colors.onAccent else AppTheme.colors.textDim,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}
