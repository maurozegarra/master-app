package com.maurozegarra.master.ui.master

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maurozegarra.master.ui.theme.AppTheme
import com.maurozegarra.master.ui.theme.STATUS_DONE

/**
 * Componentes para mostrar DATOS: un número con su contexto y una curva en el tiempo.
 *
 * Nacieron con los pesajes (TD-169), pero no son de esa pantalla: cualquier serie que el app
 * quiera enseñar -el dolor de la mañana, una carga que sube- se dibuja con estos. Los dos van
 * sobre [SectionCard], con su radio y su relleno, para que un dato se vea como el resto de
 * los bloques del app.
 */

/**
 * Un número con su etiqueta, su unidad y una línea de contexto debajo (un cambio, una meta).
 * [valueColor] para el caso en que el número ya dice algo por su color, como estar bajo la meta.
 */
@Composable
internal fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    caption: String? = null,
    valueColor: Color = AppTheme.colors.textPrimary,
    /** Algo antes del valor, a su altura: la botella del agua mientras se dosifica (8-oct). */
    leading: (@Composable () -> Unit)? = null,
    /** La diferencia con el anterior, en la línea de abajo y con la flecha de Xiaomi (TD-207). */
    delta: Double? = null,
) {
    // Una tarjeta no crece, en ningun caso (7-nov): cada linea es una sola, y lo que no cabe se
    // corta con "…" en vez de pasar a la siguiente. Una tarjeta mas alta que sus vecinas
    // descuadra la fila (el agua el 4-oct, los pesajes el 7-nov), y una flecha sin sitio se
    // llego a partir en una columna de caracteres.
    SectionCard(modifier = modifier) {
        Text(label, color = AppTheme.colors.textDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.Bottom) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(6.dp))
            }
            Text(value, color = valueColor, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            if (unit != null) {
                Spacer(Modifier.width(3.dp))
                Text(unit, color = AppTheme.colors.textDim, fontSize = 12.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
        // Siempre ocupa su línea, aunque no haya contexto: las tarjetas de una fila quedan del
        // mismo alto. La diferencia va en esta misma línea y con este mismo Text -su alto de
        // línea- para que una tarjeta con flecha mida igual que una con "goal < 0.50". Al lado
        // del número, como en Xiaomi, no cabe en un tercio del ancho (7-nov).
        if (delta != null) {
            Text(
                deltaText(delta),
                inlineContent = deltaInline(delta),
                fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(caption ?: " ", color = AppTheme.colors.textDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/*
 * La diferencia como la enseña la app de la balanza de Xiaomi (TD-207), copiada de su captura
 * del 7-nov en este mismo teléfono (1080 px, 450 dpi: 2.8125 px por dp):
 *  - la flecha: 20 x 30 px, trazo de 4 px con puntas redondas, la cabeza en 45 grados; gris
 *    azulado #7F87A3;
 *  - el número: dígitos de 26 px de alto (12 sp), gris #7F7F7F, a 8 px de la flecha;
 *  - la base de la flecha, 1 px por debajo de la base del número.
 * Medido contra su captura en la v1.0.485: con 5 px de trazo y 13 sp salía más gruesa y más grande.
 * Se dibuja y no se escribe "↑": la del tipo de letra salía diminuta (7-nov).
 */
internal val DELTA_ARROW = Color(0xFF7F87A3)
internal val DELTA_TEXT = Color(0xFF7F7F7F)
private const val DELTA_SP = 12
private const val ARROW_ID = "delta-arrow"

/** La flecha de Xiaomi: 20 x 30 px; [up] hacia arriba, si no hacia abajo. */
@Composable
internal fun DeltaArrow(up: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = (20 / 2.8125).dp, height = (30 / 2.8125).dp)) {
        val px = size.width / 20f
        val trazo = 4f * px
        val cx = size.width / 2f
        val punta = 2.5f * px
        val cola = size.height - 2.5f * px
        val brazo = 7.5f * px
        // Hacia abajo es la misma flecha dada vuelta.
        fun y(v: Float) = if (up) v else size.height - v
        val cap = androidx.compose.ui.graphics.StrokeCap.Round
        drawLine(DELTA_ARROW, Offset(cx, y(punta)), Offset(cx, y(cola)), trazo, cap)
        drawLine(DELTA_ARROW, Offset(cx, y(punta)), Offset(cx - brazo, y(punta + brazo)), trazo, cap)
        drawLine(DELTA_ARROW, Offset(cx, y(punta)), Offset(cx + brazo, y(punta + brazo)), trazo, cap)
    }
}

/** El número de la diferencia, sin signo: lo dice la flecha. */
private fun deltaNumber(d: Double): String = fmtNum(kotlin.math.abs(d))

private fun deltaText(d: Double) = androidx.compose.ui.text.buildAnnotatedString {
    appendInlineContent(ARROW_ID, if (d > 0) "\u2191" else "\u2193")
    pushStyle(androidx.compose.ui.text.SpanStyle(color = DELTA_TEXT, fontSize = DELTA_SP.sp))
    append(deltaNumber(d))
    pop()
}

@Composable
private fun deltaInline(d: Double): Map<String, androidx.compose.foundation.text.InlineTextContent> = mapOf(
    ARROW_ID to androidx.compose.foundation.text.InlineTextContent(
        // 20 px de flecha y 4 de aire hasta el número: con el margen propio del dígito quedan
        // los 8 de Xiaomi (con 6 salían 10).
        androidx.compose.ui.text.Placeholder(
            width = (24 / 2.8125).sp, height = (30 / 2.8125).sp,
            placeholderVerticalAlign = androidx.compose.ui.text.PlaceholderVerticalAlign.AboveBaseline,
        ),
    ) {
        // 2 px más abajo: la base del dígito queda 1 px sobre la de la flecha, como en Xiaomi.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) { DeltaArrow(d > 0, Modifier.offset(y = (2 / 2.8125).dp)) }
    },
)

/** Flecha y número fuera de una tarjeta: dentro del campo del pesaje (TD-207). */
@Composable
internal fun DeltaLabel(d: Double, modifier: Modifier = Modifier) {
    Text(deltaText(d), inlineContent = deltaInline(d), maxLines = 1, modifier = modifier)
}

/**
 * Una serie en el tiempo: puntos unidos, el último resaltado, las referencias del máximo y el
 * mínimo, y una meta punteada si la hay.
 *
 * La escala se ajusta a los valores y a la meta, con un margen, para que una diferencia chica
 * se vea como lo que es y no como un precipicio. [xLabel] convierte la clave de cada punto
 * (una fecha ISO, por ejemplo) en lo que se lee debajo.
 */
@Composable
internal fun LineChartCard(
    title: String,
    points: List<Pair<String, Double>>,
    accent: Color,
    format: (Double) -> String,
    xLabel: (String) -> String,
    unit: String = "",
    goal: Double? = null,
    goalLabel: String? = null,
) {
    if (points.isEmpty()) return
    val dim = AppTheme.colors.textDim
    val faded = AppTheme.colors.textFaded
    val valores = points.map { it.second } + listOfNotNull(goal)
    val lo0 = valores.min()
    val hi0 = valores.max()
    val margen = maxOf((hi0 - lo0) * 0.25, 0.5)
    val lo = lo0 - margen
    val hi = hi0 + margen
    SectionCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = AppTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (goalLabel != null) Text(goalLabel, color = dim, fontSize = 11.sp)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.height(120.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(format(hi0), color = dim, fontSize = 10.sp)
                Text(format(lo0), color = dim, fontSize = 10.sp)
            }
            Spacer(Modifier.width(6.dp))
            Canvas(Modifier.weight(1f).height(120.dp)) {
                fun x(i: Int) = if (points.size == 1) size.width / 2 else size.width * i / (points.size - 1)
                fun y(v: Double) = (size.height * (1 - (v - lo) / (hi - lo))).toFloat()
                listOf(hi0, lo0).forEach { v ->
                    drawLine(faded.copy(alpha = 0.35f), Offset(0f, y(v)), Offset(size.width, y(v)), strokeWidth = 1f)
                }
                if (goal != null) {
                    drawLine(
                        STATUS_DONE,
                        Offset(0f, y(goal)),
                        Offset(size.width, y(goal)),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                    )
                }
                val path = Path()
                points.forEachIndexed { i, (_, v) ->
                    if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v))
                }
                drawPath(path, accent, style = Stroke(width = 4f))
                points.forEachIndexed { i, (_, v) ->
                    drawCircle(accent, radius = if (i == points.lastIndex) 9f else 5f, center = Offset(x(i), y(v)))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(30.dp))
            Text(xLabel(points.first().first), color = dim, fontSize = 10.sp, modifier = Modifier.weight(1f))
            Text(
                "${xLabel(points.last().first)} · ${format(points.last().second)} $unit".trim(),
                color = AppTheme.colors.textPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
