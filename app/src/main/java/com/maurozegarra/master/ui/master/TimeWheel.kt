package com.maurozegarra.master.ui.master

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maurozegarra.master.timer.TimerPlan
import com.maurozegarra.master.ui.theme.AppTheme

/*
 * El selector de hora y de duración (TD-200), a partir de dos capturas suyas del 17-oct: la
 * app de alarmas del S26 (ruedas de hora, minutos y AM/PM) y el timer de su Watch8.
 *
 * Del reloj sale lo principal: UNA columna activa a la vez. Esa es la rueda -en el acento,
 * con el número anterior y el siguiente chicos arriba y abajo- y las demás quedan quietas, en
 * blanco y con su rótulo. Sin las líneas de selección del S26: el color ya dice qué se mueve.
 *
 * Las proporciones están MEDIDAS en la captura del reloj, con la altura del número grande
 * como unidad: los vecinos miden 0.33 y el hueco entre ellos y el grande es 0.17. En el primer
 * boceto el hueco era casi el doble y el 29 "se veía alejado del 30".
 */

/** Alto de los dígitos frente al tamaño de la letra: la altura de las mayúsculas de Roboto. */
private const val DIGIT_HEIGHT = 0.71f
private const val NEIGHBOR = 0.33f
private const val GAP = 0.17f

/** Del centro del número grande al centro de un vecino, en unidades de tamaño de letra. */
private const val NEIGHBOR_OFFSET = DIGIT_HEIGHT * (0.5f + GAP + NEIGHBOR / 2)

/**
 * El texto centrado en su caja por los DÍGITOS y no por la línea: sin esto el relleno de la
 * fuente empuja el número hacia abajo y las distancias medidas no se cumplen.
 */
private fun wheelStyle(size: TextUnit, weight: FontWeight) = TextStyle(
    fontSize = size,
    lineHeight = size,
    fontWeight = weight,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
)

/**
 * Una columna de la rueda. [focused] la convierte en la rueda: se arrastra arriba o abajo, y
 * cada salto de un vecino cambia el valor en uno, dando la vuelta en [range] como el reloj.
 *
 * TOCARLA abre el teclado numérico, como Alarm Clock Xtreme (17-oct): arrastrar de 05 a 45 era
 * largo. Lo que se escriba va a la columna en el acento. Los dígitos no los recibe la columna
 * sino [WheelKeyboard], uno para toda la rueda. Arrastrar es solo mover la rueda: con el teclado
 * abierto, sigue abierto ("yo quería mover la rueda, no hacer que el teclado se cierre").
 *
 * El alto es siempre el de la rueda abierta, también sin foco: así nada salta al cambiar de
 * columna (lineamiento: los controles no saltan).
 */
@Composable
internal fun WheelField(
    value: Int,
    range: IntRange,
    label: String,
    focused: Boolean,
    accent: Color,
    onFocus: () -> Unit,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    size: TextUnit = 60.sp,
    minWidth: Dp = 0.dp,
    format: (Int) -> String = { "%02d".format(it) },
    keyboard: Boolean = false,
    onTap: () -> Unit = onFocus,
) {
    val c = AppTheme.colors
    val density = LocalDensity.current
    val sizePx = with(density) { size.toPx() }
    val offset = with(density) { (sizePx * NEIGHBOR_OFFSET).toDp() }
    val alto = with(density) { (sizePx * (2 * NEIGHBOR_OFFSET + NEIGHBOR * DIGIT_HEIGHT * 2)).toDp() }
    // Un número por cada salto de vecino: lo que se arrastra es lo que se ve moverse.
    val paso = sizePx * NEIGHBOR_OFFSET
    val valor by rememberUpdatedState(value)
    val cambiar by rememberUpdatedState(onChange)
    val enfocar by rememberUpdatedState(onFocus)
    val chico = wheelStyle(size * NEIGHBOR, FontWeight.Normal)
    val vecino = accent.copy(alpha = 0.55f)
    val teclas = LocalSoftwareKeyboardController.current

    Box(
        modifier
            .widthIn(min = minWidth)
            .height(alto)
            // Ya con el teclado: si se cerró con "atrás", tocar otra vez lo vuelve a sacar.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onTap(); if (keyboard) teclas?.show() }
            .pointerInput(range) {
                var acumulado = 0f
                detectVerticalDragGestures(
                    onDragStart = { acumulado = 0f; enfocar() },
                ) { change, dy ->
                    change.consume()
                    acumulado += dy
                    // Arrastrar hacia arriba trae el número de abajo, el siguiente.
                    var v = valor
                    while (acumulado <= -paso) { acumulado += paso; v = TimerPlan.wrap(v + 1, range) }
                    while (acumulado >= paso) { acumulado -= paso; v = TimerPlan.wrap(v - 1, range) }
                    if (v != valor) cambiar(v)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (focused) {
            Text(format(TimerPlan.wrap(value - 1, range)), style = chico, color = vecino, modifier = Modifier.offset(y = -offset))
            Text(format(TimerPlan.wrap(value + 1, range)), style = chico, color = vecino, modifier = Modifier.offset(y = offset))
        } else {
            // El rótulo, a la altura del vecino de arriba, como el "Min" del reloj junto al 99.
            Text(label, style = wheelStyle(15.sp, FontWeight.SemiBold), color = c.textDim, modifier = Modifier.offset(y = -offset))
        }
        Text(
            format(value),
            style = wheelStyle(size, FontWeight.Bold),
            // Sin resaltado propio: la columna en el acento ya dice adónde va lo que se escriba.
            // Hubo un recuadro de fondo (27-oct), que no estaba en ninguna referencia ni en el
            // boceto y tapaba a los vecinos.
            color = if (focused) accent else c.textPrimary,
        )
    }
}

/**
 * El campo que recibe el teclado de TODA la rueda: invisible, del tamaño de un punto. Cada
 * dígito va a la columna [column], y cuando esa ya no admite otro ([TimerPlan.typeDigit]) pasa a
 * la siguiente; en la última, el teclado se cierra. La flecha del teclado también avanza.
 *
 * Es uno solo a propósito. Al principio cada columna tenía el suyo y el foco saltaba de uno a
 * otro en medio de la escritura: en su S26 (27-oct) el salto no se daba, y el 4 y el 5 de los
 * minutos seguían cayendo en la hora. Aquí el foco no se mueve mientras se escribe, y por lo
 * mismo la acción del teclado es siempre la flecha: cambiarla reinicia la conexión con él.
 */
@Composable
internal fun WheelKeyboard(
    open: Boolean,
    column: Int,
    /** Cambia con cada arrastre: lo que se escriba después reemplaza lo que dejó la rueda. */
    reset: Int,
    ranges: List<IntRange>,
    onValue: (column: Int, value: Int) -> Unit,
    onColumn: (Int) -> Unit,
    onClosed: () -> Unit,
) {
    val pedir = remember { FocusRequester() }
    val teclas = LocalSoftwareKeyboardController.current
    val foco = LocalFocusManager.current
    var tengoFoco by remember { mutableStateOf(false) }
    val abierto by rememberUpdatedState(open)
    // Lo tecleado en esta columna: el primer dígito reemplaza. Se vacía al cambiar de columna
    // y al arrastrar.
    var tecleado by remember(column, reset) { mutableStateOf("") }

    LaunchedEffect(open) {
        if (open) {
            pedir.requestFocus()
            teclas?.show()
        } else if (tengoFoco) {
            // Se cerró al llenar la última columna. Solo si el foco es suyo: si se fue al campo
            // del nombre, quitarlo de ahí le cerraría el teclado a ese campo.
            teclas?.hide()
            foco.clearFocus()
        }
    }

    fun avanzar() {
        tecleado = ""
        if (column < ranges.lastIndex) onColumn(column + 1) else { teclas?.hide(); onClosed() }
    }

    // El campo ACUMULA lo que manda el teclado y de cada cambio se toma solo lo nuevo. Las dos
    // formas de vaciarlo fallaron el 27-oct escribiendo 11:55 (vistas en un registro en su S26):
    // con value = "" fijo, BasicTextField recuerda el último texto y calla si se repite, y el
    // segundo 5 nunca llegó; vaciándolo desde aquí, el teclado de Samsung no se entera y mandó
    // "55" de una vez, que puso 55 y encima otra vez 05.
    var campo by remember { mutableStateOf(TextFieldValue("")) }
    BasicTextField(
        value = campo,
        onValueChange = { nuevo ->
            val antes = campo.text
            val nuevos = if (nuevo.text.length > antes.length && nuevo.text.startsWith(antes)) nuevo.text.substring(antes.length) else ""
            campo = nuevo
            nuevos.filter { it.isDigit() }.forEach { d ->
                val r = TimerPlan.typeDigit(tecleado, d, ranges[column])
                tecleado = r.buffer
                r.value?.let { onValue(column, it) }
                if (r.advance) avanzar()
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { avanzar() }),
        modifier = Modifier
            .size(1.dp)
            .alpha(0f)
            .focusRequester(pedir)
            .onFocusChanged {
                tengoFoco = it.isFocused
                if (!it.isFocused && abierto) onClosed()
            },
    )
}

/** Los dos puntos entre columnas, a la altura de los dígitos. */
@Composable
internal fun WheelColon(size: TextUnit = 60.sp) {
    Text(
        ":",
        style = wheelStyle(size * 0.8f, FontWeight.Bold),
        color = AppTheme.colors.textPrimary,
        modifier = Modifier.padding(horizontal = 4.dp).offset(y = (-3).dp),
    )
}

/**
 * AM y PM apilados, como en la alarma del S26: el elegido en blanco y el otro tenue. Se elige
 * de un toque, o deslizando como las otras columnas (27-oct, "agrégale el deslizar"): hacia
 * arriba PM, hacia abajo AM. Sin vecinos: con dos opciones no hay número de arriba ni de abajo.
 *
 * El toque cuenta al APOYAR el dedo, y el gesto entero se queda aquí. Con un clic normal
 * (27-oct, medido con un registro en su S26) el toque duró 2.2 s y el dedo se corrió 24 px: la
 * hoja lo tomó por un arrastre, el clic se anuló y la rueda perdió el teclado.
 */
@Composable
internal fun AmPmToggle(pm: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val cambiar by rememberUpdatedState(onChange)
    Column(
        modifier
            .padding(start = 12.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val abajo = awaitFirstDown()
                    abajo.consume()
                    // Las dos palabras miden lo mismo: la mitad de arriba es AM.
                    cambiar(abajo.position.y >= size.height / 2f)
                    val margen = viewConfiguration.touchSlop
                    do {
                        val e = awaitPointerEvent()
                        e.changes.forEach { it.consume() }
                        val dy = (e.changes.firstOrNull()?.position?.y ?: abajo.position.y) - abajo.position.y
                        if (dy <= -margen) cambiar(true) else if (dy >= margen) cambiar(false)
                    } while (e.changes.any { it.pressed })
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        listOf(false to "AM", true to "PM").forEach { (esPm, texto) ->
            Text(
                texto,
                style = wheelStyle(20.sp, FontWeight.Bold),
                color = if (esPm == pm) c.textPrimary else c.textFaded,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
    }
}
