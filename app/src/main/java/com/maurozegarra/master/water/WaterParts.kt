package com.maurozegarra.master.water

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BubbleChart
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.EmojiFoodBeverage
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Grass
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material.icons.outlined.SoupKitchen
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maurozegarra.master.i18n.I18n
import com.maurozegarra.master.i18n.WaterStrings
import com.maurozegarra.master.ui.AppTextField
import com.maurozegarra.master.ui.master.ClockTimeDialog
import com.maurozegarra.master.ui.master.SegmentToggle
import com.maurozegarra.master.ui.master.STAGE_COLORS
import com.maurozegarra.master.ui.theme.AppTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Cada bebida con su ícono y su color, para saber de un vistazo qué se tomó en el día: lo
 * que le gustaba de su app de antes (4-oct). Los colores salen de la paleta del app
 * ([STAGE_COLORS]), no inventados.
 */
internal fun drinkIcon(type: String): ImageVector = when (type) {
    Drinks.CREATINE -> Icons.Outlined.FitnessCenter
    Drinks.QUINOA -> Icons.Outlined.EmojiFoodBeverage
    Drinks.SOUP -> Icons.Outlined.SoupKitchen
    Drinks.SODA -> Icons.Outlined.BubbleChart
    Drinks.COFFEE -> Icons.Outlined.Coffee
    Drinks.MACA -> Icons.Outlined.Grass
    Drinks.LEMONADE -> Icons.Outlined.WbSunny
    else -> Icons.Outlined.LocalDrink
}

internal fun drinkColor(type: String): Color = when (type) {
    // El azul, el morado, el café y el magenta de la paleta son oscuros y sobre el fondo del
    // app se apagaban; aclararlos hacia el blanco los dejó deslavados (4-oct). Van versiones
    // vivas del mismo tono, elegidas con él. La paleta no se toca: la comparten las etapas.
    Drinks.WATER -> Color(0xFF42A5F5)
    Drinks.SODA -> Color(0xFF8C7CF0)
    Drinks.COFFEE -> Color(0xFFB07A50)
    Drinks.MACA -> Color(0xFFD65BB5)
    else -> paletteColor(type)
}

private fun paletteColor(type: String): Color = Color(
    when (type) {
        Drinks.CREATINE -> STAGE_COLORS[2]   // verde
        Drinks.QUINOA -> STAGE_COLORS[1]     // ámbar
        Drinks.SOUP -> STAGE_COLORS[0]       // naranja
        Drinks.SODA -> STAGE_COLORS[5]       // morado
        Drinks.COFFEE -> STAGE_COLORS[9]     // café
        Drinks.MACA -> STAGE_COLORS[6]       // magenta
        Drinks.LEMONADE -> STAGE_COLORS[3]   // verde azulado
        else -> STAGE_COLORS[4]              // azul
    },
)

internal fun drinkName(type: String, w: WaterStrings): String = when (type) {
    Drinks.CREATINE -> w.creatine
    Drinks.QUINOA -> w.quinoa
    Drinks.SOUP -> w.soup
    Drinks.SODA -> w.soda
    Drinks.COFFEE -> w.coffee
    Drinks.MACA -> w.maca
    Drinks.LEMONADE -> w.lemonade
    else -> w.water
}

/** El ícono de una bebida en su recuadro de color, con la misma receta que el de un ejercicio (ExerciseGlyph). */
@Composable
internal fun DrinkGlyph(type: String, sizeDp: Int = 40) {
    val c = drinkColor(type)
    Box(
        Modifier.size(sizeDp.dp).clip(RoundedCornerShape(12.dp)).background(c.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(drinkIcon(type), contentDescription = null, tint = c, modifier = Modifier.size((sizeDp * 0.55).dp))
    }
}

private const val OTHER = "other"

private val HORA = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

/**
 * Anotar con otra hora, o corregir una toma: la bebida, la cantidad y la hora. [editing] null
 * es una nueva, con la hora de ahora para cambiarla.
 */
@Composable
internal fun DrinkDialog(editing: WaterLog?, zone: ZoneId, onDismiss: () -> Unit, onSave: (WaterLog) -> Unit) {
    val t = I18n.EN
    val w = t.more.water
    val accent = AppTheme.colors.accent
    val ahora = remember { System.currentTimeMillis() }
    var tipo by remember { mutableStateOf(editing?.type ?: Drinks.WATER) }
    val medidas = listOf(WaterPlan.GLASS_ML, WaterPlan.BOTTLE_ML)
    var ml by remember { mutableStateOf(editing?.ml ?: WaterPlan.GLASS_ML) }
    var otra by remember { mutableStateOf(editing?.ml?.takeIf { it !in medidas }?.toString() ?: "") }
    // El campo de otra cantidad solo se enseña al elegir "Other": casi siempre es 200 o 600.
    var otraSel by remember { mutableStateOf(otra.isNotEmpty()) }
    var hora by remember { mutableStateOf(Instant.ofEpochMilli(editing?.at ?: ahora).atZone(zone).toLocalTime().withSecond(0).withNano(0)) }
    var reloj by remember { mutableStateOf(false) }
    val dia = editing?.let { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() } ?: LocalDate.now(zone)
    val cantidad = if (otraSel) otra.toIntOrNull()?.takeIf { it > 0 } ?: ml else ml

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppTheme.colors.surface,
        titleContentColor = AppTheme.colors.textPrimary,
        // La hora va en el título, a la derecha: en su propia fila ocupaba lo que un control.
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (editing == null) w.addTitle else w.editTitle, modifier = Modifier.weight(1f))
                Text(
                    hora.format(HORA),
                    color = accent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    // El offset devuelve el relleno de la zona de toque: así el texto termina en el
                    // borde de la cuadrícula de bebidas.
                    modifier = Modifier.offset(x = 6.dp).clip(RoundedCornerShape(8.dp)).clickable { reloj = true }.padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Las bebidas, con su ícono: dos filas de cuatro.
                Drinks.ALL.chunked(4).forEach { fila ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        fila.forEach { d ->
                            val sel = d == tipo
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, if (sel) accent else AppTheme.colors.track, RoundedCornerShape(12.dp))
                                    .clickable { tipo = d }
                                    .padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                DrinkGlyph(d, sizeDp = 32)
                                Spacer(Modifier.height(4.dp))
                                Text(drinkName(d, w), color = if (sel) AppTheme.colors.textPrimary else AppTheme.colors.textDim, fontSize = 12.sp)
                            }
                        }
                    }
                }
                SegmentToggle(
                    options = medidas.map { "$it" to "$it ml" } + (OTHER to w.other),
                    selected = if (otraSel) OTHER else "$ml",
                    accent = accent,
                ) { if (it == OTHER) otraSel = true else { ml = it.toInt(); otraSel = false } }
                if (otraSel) {
                    AppTextField(
                        value = otra,
                        onValueChange = { s -> otra = s.filter { it.isDigit() }.take(4) },
                        label = w.otherAmount,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(WaterLog(dia.atTime(hora).atZone(zone).toInstant().toEpochMilli(), cantidad, tipo))
            }) { Text(w.save, color = accent, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t.cancel, color = AppTheme.colors.textDim) }
        },
    )
    if (reloj) {
        ClockTimeDialog(initial = hora, accent = accent, t = t, onDismiss = { reloj = false }) { h ->
            hora = h
            reloj = false
        }
    }
}
