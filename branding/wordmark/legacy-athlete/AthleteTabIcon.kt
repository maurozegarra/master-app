// REFERENCIA (no compila tal cual): código del wordmark/ícono de Athlete extraído
// de mini-timer (ui/TimerApp.kt). Se conserva aquí como base para reubicar/rebrandizar
// el wordmark en Athletic. Ver branding/wordmark/README.md.
//
// Wordmark de Athlete = título "ATHLETE" en Neuropol + este ícono hexagonal con
// una "M" en Wallpoet recortada por BlendMode.DstOut.

package com.minitimer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawText
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.material3.LocalContentColor
import com.minitimer.ui.theme.Wallpoet

@Composable
private fun AthleteTabIcon(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    val measurer = rememberTextMeasurer()
    Canvas(modifier = modifier) {
        val sx = size.width / 24f
        val sy = size.height / 24f
        // Hexágono de 6 vértices (coordenadas en un lienzo virtual de 24x24).
        val pts = listOf(
            12f to 2.5f, 20.23f to 7.25f, 20.23f to 16.75f,
            12f to 21.5f, 3.77f to 16.75f, 3.77f to 7.25f,
        )
        val hex = Path().apply {
            moveTo(pts[0].first * sx, pts[0].second * sy)
            for (i in 1 until pts.size) lineTo(pts[i].first * sx, pts[i].second * sy)
            close()
        }
        val canvas = drawContext.canvas
        canvas.saveLayer(Rect(Offset.Zero, size), Paint())
        // Rotación -12° para el look "en cursiva" del hexágono.
        rotate(-12f, pivot = Offset(size.width / 2f, size.height / 2f)) {
            drawPath(hex, color)
        }
        // "M" en Wallpoet, recortada (DstOut) para "calar" la letra en el hexágono.
        val layout = measurer.measure(
            text = "M",
            style = TextStyle(
                fontFamily = Wallpoet,
                fontSize = (size.height * 0.5f).toSp(),
            ),
        )
        drawText(
            textLayoutResult = layout,
            color = Color.Black,
            topLeft = Offset(
                (size.width - layout.size.width) / 2f,
                (size.height - layout.size.height) / 2f,
            ),
            blendMode = BlendMode.DstOut,
        )
        canvas.restore()
    }
}
