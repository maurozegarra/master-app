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
import androidx.compose.ui.graphics.drawscope.clipPath
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
    Drinks.SPARKLING -> SparklingBottle
    Drinks.BOTTLE -> StillBottle
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
    // Las botellas (8-oct): la sin gas es la misma agua que el vaso, en su azul; la con gas,
    // un celeste más claro, el color de las burbujas, que no usa ninguna otra bebida.
    Drinks.BOTTLE -> Color(0xFF42A5F5)
    Drinks.SPARKLING -> Color(0xFF80DEEA)
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
    Drinks.SPARKLING -> w.sparkling
    Drinks.BOTTLE -> w.stillBottle
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

/** Lo que trae cada bebida al elegirla: las botellas, su botella; lo demás, un vaso. */
private fun defaultMl(type: String): Int = when (type) {
    Drinks.SPARKLING -> WaterPlan.BOTTLE_ML
    Drinks.BOTTLE -> WaterPlan.STILL_BOTTLE_ML
    else -> WaterPlan.GLASS_ML
}

/**
 * Anotar con otra hora, o corregir una toma: la bebida, la cantidad y la hora. [editing] null
 * es una nueva, con la hora de ahora para cambiarla.
 */
@Composable
internal fun DrinkDialog(
    editing: WaterLog?,
    zone: ZoneId,
    /** La bebida de una toma nueva según su hora (8-oct): cambia si se cambia la hora. */
    drinkAt: (Long) -> String,
    onDismiss: () -> Unit,
    onSave: (WaterLog) -> Unit,
) {
    val t = I18n.EN
    val w = t.more.water
    val accent = AppTheme.colors.accent
    val ahora = remember { System.currentTimeMillis() }
    var tipo by remember { mutableStateOf(editing?.type ?: drinkAt(ahora)) }
    // Una toma nueva sigue a su hora mientras no se elija la bebida a mano.
    var tipoElegido by remember { mutableStateOf(editing != null) }
    // Con la botella sin gas el atajo es 500 y no 600 (8-oct): es la que compra en la tarde.
    val medidas = listOf(WaterPlan.GLASS_ML, if (tipo == Drinks.BOTTLE) WaterPlan.STILL_BOTTLE_ML else WaterPlan.BOTTLE_ML)
    var ml by remember { mutableStateOf(editing?.ml ?: defaultMl(tipo)) }
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
                                    // Cada botella trae su cantidad (8-oct): con gas 600, sin gas
                                    // 500; las de vaso, 200.
                                    .clickable {
                                        tipo = d
                                        tipoElegido = true
                                        ml = defaultMl(d)
                                        otraSel = false
                                        otra = ""
                                    }
                                    .padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                DrinkGlyph(d, sizeDp = 32)
                                Spacer(Modifier.height(4.dp))
                                Text(drinkName(d, w), color = if (sel) AppTheme.colors.textPrimary else AppTheme.colors.textDim, fontSize = 12.sp)
                            }
                        }
                        // La última fila guarda el ancho de las demás: sin esto, dos bebidas
                        // solas se estiraban al doble (8-oct).
                        repeat(4 - fila.size) { Spacer(Modifier.weight(1f)) }
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
            if (!tipoElegido) {
                tipo = drinkAt(dia.atTime(h).atZone(zone).toInstant().toEpochMilli())
                if (!otraSel) ml = defaultMl(tipo)
            }
            reloj = false
        }
    }
}

/**
 * Las dos botellas (8-oct), dibujadas aquí porque Material no tiene una botella de agua. La
 * silueta sale de la de su app de agua de antes: tapa con su anillo, cuello angosto, hombros
 * que bajan, cintura a media botella y la base ancha. La misma para las dos, para que se lean
 * como pareja; la de gas lleva burbujas y la sin gas el nivel del agua.
 *
 * En un lienzo de 20 x 40: alto el doble que ancho, como la botella.
 */
private const val BOTTLE_W = 20f
private const val BOTTLE_H = 40f
private const val BOTTLE_BODY =
    "M7.8,6.5L7.8,9C7.8,11 3.6,12.5 3.6,17C3.6,20 4.6,22.5 4.6,25C4.6,27.5 3.4,30 3.4,33L3.4,37" +
        "Q3.4,39 5.4,39L14.6,39Q16.6,39 16.6,37L16.6,33C16.6,30 15.4,27.5 15.4,25C15.4,22.5 16.4,20 16.4,17" +
        "C16.4,12.5 12.2,11 12.2,9L12.2,6.5Z"
private const val BOTTLE_CAP =
    "M6.6,1.8Q6.6,0.8 7.6,0.8L12.4,0.8Q13.4,0.8 13.4,1.8L13.4,5.2L6.6,5.2Z" +
        "M5.8,5.2L14.2,5.2L14.2,6.5L5.8,6.5Z"
/** Donde empieza el agua de una botella llena (bajo los hombros) y donde termina (el fondo). */
private const val WATER_TOP = 13f
private const val WATER_BOTTOM = 39f

private fun bottle(name: String, inside: String, insideIsStroke: Boolean): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 12.dp, defaultHeight = 24.dp, viewportWidth = BOTTLE_W, viewportHeight = BOTTLE_H)
        .addPath(
            pathData = androidx.compose.ui.graphics.vector.addPathNodes(BOTTLE_BODY + BOTTLE_CAP),
            stroke = androidx.compose.ui.graphics.SolidColor(Color.Black),
            strokeLineWidth = 2.4f,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        )
        .addPath(
            pathData = androidx.compose.ui.graphics.vector.addPathNodes(inside),
            fill = if (insideIsStroke) null else androidx.compose.ui.graphics.SolidColor(Color.Black),
            stroke = if (insideIsStroke) androidx.compose.ui.graphics.SolidColor(Color.Black) else null,
            strokeLineWidth = 2.2f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        .build()

private val SparklingBottle: ImageVector by lazy {
    bottle(
        "SparklingBottle",
        // Tres burbujas que suben, de grande a chica.
        "M9,32m-1.6,0a1.6,1.6 0 1,0 3.2,0a1.6,1.6 0 1,0 -3.2,0" +
            "M11.6,26.5m-1.2,0a1.2,1.2 0 1,0 2.4,0a1.2,1.2 0 1,0 -2.4,0" +
            "M9.4,21.5m-0.9,0a0.9,0.9 0 1,0 1.8,0a0.9,0.9 0 1,0 -1.8,0",
        insideIsStroke = false,
    )
}

private val StillBottle: ImageVector by lazy {
    // El nivel del agua, una ola suave a la altura de la cintura.
    bottle("StillBottle", "M5,24c1.7,-1.1 3.4,-1.1 5,0s3.3,1.1 5,0", insideIsStroke = true)
}

/**
 * La botella mientras se dosifica (8-oct), para la tarjeta "Pace": el agua que le QUEDA, abajo
 * y con su nivel, como la botella en la mano. [thirds] es lo que queda: 2 al abrirla (el primer
 * tercio se toma al anotarla), 1 una hora después, 0 en la última hora. Se probó también
 * pintar lo que lleva y una línea de "hasta aquí", que se leía como una etiqueta.
 */
@Composable
internal fun BottleDose(type: String, thirds: Int) {
    val c = drinkColor(type)
    val line = AppTheme.colors.textDim
    val cuerpo = remember { androidx.compose.ui.graphics.vector.PathParser().parsePathString(BOTTLE_BODY).toPath() }
    val tapa = remember { androidx.compose.ui.graphics.vector.PathParser().parsePathString(BOTTLE_CAP).toPath() }
    androidx.compose.foundation.Canvas(Modifier.size(width = 15.dp, height = 30.dp)) {
        val sx = size.width / BOTTLE_W
        val sy = size.height / BOTTLE_H
        val m = androidx.compose.ui.graphics.Matrix().apply { scale(sx, sy) }
        val body = androidx.compose.ui.graphics.Path().apply { addPath(cuerpo); transform(m) }
        val cap = androidx.compose.ui.graphics.Path().apply { addPath(tapa); transform(m) }
        val alto = WATER_BOTTOM - WATER_TOP
        val nivel = WATER_BOTTOM - alto * thirds / 3f
        clipPath(body) {
            drawRect(c, topLeft = androidx.compose.ui.geometry.Offset(0f, nivel * sy), size = androidx.compose.ui.geometry.Size(size.width, size.height))
        }
        val trazo = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.4.dp.toPx(), join = androidx.compose.ui.graphics.StrokeJoin.Round)
        drawPath(body, line, style = trazo)
        drawPath(cap, line, style = trazo)
    }
}
