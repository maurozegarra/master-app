package com.maurozegarra.master.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maurozegarra.master.ui.theme.AppTheme
import com.maurozegarra.master.ui.theme.Dims

/**
 * El botón primario del app (TD-177), con las seis decisiones de "Make any button look
 * expensive" (@motion_ui_interface), que el usuario trajo el 28-sep: *"el botón Start [...]
 * es rojo sólido y se vería mejor si sólo fuera borde y con el efecto exacto que menciona
 * el video"*.
 *
 * 1. **Tamaño**: [Dims.buttonHeight] de alto, por encima de los 44 que pide un pulgar.
 * 2. **Etiqueta**: 17 sp, semibold, en el texto principal de la paleta.
 * 3. **Contraste**: relleno satinado -de [AppTheme] track a surface- y un borde de 1 dp
 *    del acento, que es lo que lo separa de la página. Ya no es un bloque rojo.
 * 4. **Profundidad**: el borde de arriba iluminado ([TOP_EDGE_LIGHT]) y una sombra que cae
 *    hacia abajo: la luz viene de arriba.
 * 5. **Detalle**: píldora -radio = alto / 2, en porcentaje para no escribir el número- y
 *    un solo ícono, opcional, de 20 a 10 del texto.
 * 6. **Movimiento**: al tocar, se hunde 1 dp y la luz da una vuelta al borde. En un teléfono
 *    no hay "hover", así que ambas cosas van al toque: responde en 100 ms y termina en 300.
 *
 * Es el ÚNICO botón primario ([PrimaryButton] lo delega): cambiarlo aquí cambia los nueve
 * a la vez, que es lo que mantiene el app coherente.
 */
@Composable
internal fun AppPrimaryButton(
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
) {
    val c = AppTheme.colors
    val forma = RoundedCornerShape(percent = 50)
    val toque = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val presionado by toque.collectIsPressedAsState()
    // Se hunde 1 dp: responde en 100 ms, frenando al final.
    val hundido by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (presionado && enabled) 1.dp else 0.dp,
        animationSpec = androidx.compose.animation.core.tween(100, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "hundido",
    )
    // Una vuelta de luz por el borde en 200 ms, cada vez que se toca.
    val vuelta = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(1f) }
    androidx.compose.runtime.LaunchedEffect(presionado) {
        if (presionado && enabled) {
            vuelta.snapTo(0f)
            vuelta.animateTo(1f, androidx.compose.animation.core.tween(200, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
        }
    }
    val borde = if (enabled) accent else c.textFaded
    Box(
        modifier
            .height(Dims.buttonHeight)
            .graphicsLayer { translationY = hundido.toPx() }
            .shadow(if (enabled) 8.dp else 0.dp, forma, clip = false)
            .clip(forma)
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(c.track, c.surface)))
            .border(1.dp, borde, forma)
            .drawBehind {
                // El borde de arriba iluminado: una línea fina dentro del contorno, solo en el
                // tramo recto, que es donde la luz de arriba daría.
                val r = size.height / 2f
                drawLine(
                    com.maurozegarra.master.ui.theme.TOP_EDGE_LIGHT,
                    androidx.compose.ui.geometry.Offset(r, 1.5.dp.toPx()),
                    androidx.compose.ui.geometry.Offset(size.width - r, 1.5.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .clickable(
                interactionSource = toque,
                indication = null,
                enabled = enabled,
                role = androidx.compose.ui.semantics.Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (vuelta.value < 1f) {
            GlowRing(
                cornerRadius = Dims.buttonHeight / 2,
                colors = glowColors(accent),
                strokeWidth = 2.dp,
                angle = vuelta.value * 360f,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                androidx.compose.material3.Icon(icon, contentDescription = null, tint = if (enabled) accent else c.textDim, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(
                label,
                color = if (enabled) c.textPrimary else c.textDim,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
            )
        }
    }
}

@Composable
internal fun AppOutlineButton(
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(Dims.buttonHeight),
        shape = RoundedCornerShape(Dims.button),
        border = BorderStroke(1.dp, AppTheme.colors.track),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
internal fun AppStepButton(
    symbol: String,
    accent: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = AppTheme.colors
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(c.track)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            symbol,
            color = if (enabled) accent else c.textDim,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
        )
    }
}

@Composable
internal fun AppStepper(
    label: String,
    value: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    min: Int = 0,
    max: Int = 3600,
    step: Int = 1,
    format: (Int) -> String = { it.toString() },
    onChange: (Int) -> Unit,
) {
    val c = AppTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = c.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        AppStepButton("−", accent) { onChange((value - step).coerceAtLeast(min)) }
        Box(Modifier.width(64.dp), contentAlignment = Alignment.Center) {
            Text(format(value), color = c.textPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        AppStepButton("+", accent) { onChange((value + step).coerceAtMost(max)) }
    }
}

@Composable
internal fun SwitchRow(
    label: String,
    desc: String?,
    checked: Boolean,
    accent: Color,
    onCheckedChange: (Boolean) -> Unit,
) {
    val c = AppTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                color = c.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (desc != null) {
                Spacer(Modifier.height(4.dp))
                Text(desc, color = c.textDim, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        AppSwitch(checked, accent, onCheckedChange)
    }
}

/**
 * El interruptor del app, con los colores de la paleta. Lo usan [SwitchRow] y las
 * tarjetas que llevan uno sin etiqueta, como las alarmas (TD-175).
 */
@Composable
internal fun AppSwitch(checked: Boolean, accent: Color, onCheckedChange: (Boolean) -> Unit) {
    val c = AppTheme.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = c.onAccent,
            checkedTrackColor = accent,
            uncheckedThumbColor = com.maurozegarra.master.ui.theme.SWITCH_THUMB_OFF,
            uncheckedTrackColor = c.track,
        ),
    )
}

/**
 * El campo de texto del app (TD-187), con los colores de la paleta: borde y cursor en el
 * acento al escribir, borde [AppTheme] track en reposo, texto principal, etiqueta tenue.
 *
 * Existe porque la misma receta estaba copiada a mano en siete pantallas, una octava la
 * tenía con otro gris, y tres diálogos -y el del pesaje (TD-169)- ni la tenían: salían con
 * los colores por defecto de Material, distintos del resto del app.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    /**
     * Con varias lineas, hasta cuantas crece antes de desplazar el texto por dentro. Como la
     * caja de mensajes de Telegram (ChatActivityEnterView: setMaxLines(6)): asi nunca es mas
     * alto que el sitio libre y la linea que se escribe siempre se ve.
     */
    maxLines: Int = Int.MAX_VALUE,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
    accent: Color = AppTheme.colors.accent,
) {
    val c = AppTheme.colors
    // Un campo de varias lineas pide verse entero cada vez que cambia el texto. El de Compose
    // solo lo pide al ganar el foco: al ir agregando lineas, la ultima quedaba detras del
    // teclado aunque la pantalla le dejara sitio (3-oct, la nota al terminar una sesion; el
    // registro de esa pantalla confirmo que el teclado si llegaba, 859 px).
    val verse = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    var enFoco by remember { mutableStateOf(false) }
    if (!singleLine) {
        LaunchedEffect(value, enFoco) {
            if (enFoco) verse.bringIntoView()
        }
    }
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .bringIntoViewRequester(verse)
            .onFocusChanged { enFoco = it.isFocused },
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it, color = c.textFaded) } },
        leadingIcon = leadingIcon,
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else maxLines,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accent,
            unfocusedBorderColor = c.track,
            focusedTextColor = c.textPrimary,
            unfocusedTextColor = c.textPrimary,
            cursorColor = accent,
            focusedLabelColor = accent,
            unfocusedLabelColor = c.textDim,
        ),
    )
}
