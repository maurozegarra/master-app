package com.maurozegarra.master.ui.master

import androidx.compose.foundation.background
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
import com.maurozegarra.master.util.pad2

/** Paleta de colores para etapas (ARGB Long), igual orden que en los mocks. */
internal val STAGE_COLORS: List<Long> = listOf(
    0xFFE2641EL, 0xFFEFAA2AL, 0xFF2E9E5BL, 0xFF159E8CL,
    0xFF1565C0L, 0xFF5E48C8L, 0xFFB4318FL, 0xFFC0392BL,
    0xFF455A64L, 0xFF6D4C41L,
)

internal fun fmtSec(s: Int): String = if (s < 60) "${s}s" else "${s / 60}:${pad2(s % 60)}"

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
        timeBased -> listOfNotNull(tiempo, kmh, kg).joinToString("  \u00b7  ")
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
internal fun SetLine(index: Int, sr: SetRecord, timeBased: Boolean, t: Strings, fontSize: TextUnit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("${index + 1}", color = AppTheme.colors.textFaded, fontSize = fontSize, modifier = Modifier.width(20.dp))
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

@Composable
internal fun PrimaryButton(
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) = AppPrimaryButton(label = label, accent = accent, modifier = modifier, enabled = enabled, onClick = onClick)

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
        options.forEach { (key, text) ->
            val active = key == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) accent else Color.Transparent)
                    .clickable { onSelect(key) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text,
                    color = if (active) AppTheme.colors.onAccent else AppTheme.colors.textDim,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
internal fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dims.card))
            .background(AppTheme.colors.surface)
            .padding(16.dp),
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
