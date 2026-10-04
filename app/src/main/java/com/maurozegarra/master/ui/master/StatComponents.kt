package com.maurozegarra.master.ui.master

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
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
) {
    SectionCard(modifier = modifier) {
        Text(label, color = AppTheme.colors.textDim, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = valueColor, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (unit != null) {
                Spacer(Modifier.width(3.dp))
                Text(unit, color = AppTheme.colors.textDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
        // Siempre ocupa su línea, aunque no haya contexto: las tarjetas de una fila quedan del
        // mismo alto.
        Text(caption ?: " ", color = AppTheme.colors.textDim, fontSize = 11.sp)
    }
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
