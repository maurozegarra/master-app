package com.maurozegarra.master.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Una acción del panel que se revela al deslizar. */
data class SwipeAction(
    val icon: ImageVector,
    val tint: Color,
    /** Se usa como contentDescription: un gesto no es descubrible por lector de pantalla. */
    val label: String,
    val onClick: () -> Unit,
)

private enum class SwipeState { Closed, Open }

private val ButtonSize = 44.dp
private val ButtonGap = 10.dp
private val PanelEndPadding = 12.dp

/**
 * Coordina las filas de una misma lista para que solo una quede abierta.
 *
 * Guarda un token en vez de la fila misma: cada fila compara si el token abierto es el
 * suyo y se cierra si no lo es. Así la coordinación es reactiva y ninguna fila necesita
 * conocer a las demás.
 */
@Stable
class SwipeRowsController {
    internal var openToken by mutableStateOf<Any?>(null)
        private set

    /** Si alguna fila esta abierta. Sirve para armar el tap-para-descartar del contenedor. */
    val isAnyOpen: Boolean get() = openToken != null

    internal fun open(token: Any) {
        openToken = token
    }

    /** Cierra la fila abierta, si hay alguna. Usarlo al hacer scroll o al navegar. */
    fun closeAll() {
        openToken = null
    }

    /**
     * Con una fila abierta, el primer tap sirve para descartar: cierra y devuelve true
     * para que quien llama no ejecute su accion.
     *
     * Hace falta en los controles de OTRAS filas, que no estan cubiertos por el overlay
     * de la fila abierta. Sin esto, tocar el play de otra card arranca el training
     * cuando la intencion era cerrar el panel.
     */
    fun consumeTapIfOpen(): Boolean {
        if (openToken == null) return false
        closeAll()
        return true
    }
}

@Composable
fun rememberSwipeRowsController(): SwipeRowsController = remember { SwipeRowsController() }

/**
 * Fila que al deslizarse a la izquierda revela botones circulares de acción en el borde
 * derecho, y se queda abierta hasta que se toca una acción o se cierra deslizando.
 *
 * Se usa `AnchoredDraggable` y no `SwipeToDismissBox`: este último es un gesto de
 * descarte —la fila se va y su fondo es solo decorativo—, mientras que aquí el panel
 * tiene que quedarse anclado con botones tocables.
 *
 * Convive con el drag-reorder de [dragContainer] porque aquel exige un long-press previo,
 * así que un deslizamiento horizontal directo no lo dispara.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeActionsRow(
    actions: List<SwipeAction>,
    controller: SwipeRowsController,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (actions.isEmpty()) {
        Box(modifier) { content() }
        return
    }

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val token = remember { Any() }
    val panelWidth = ButtonSize * actions.size + ButtonGap * (actions.size - 1) + PanelEndPadding * 2

    val state = remember(actions.size) {
        val panelPx = with(density) { panelWidth.toPx() }
        AnchoredDraggableState(
            initialValue = SwipeState.Closed,
            anchors = DraggableAnchors {
                SwipeState.Closed at 0f
                SwipeState.Open at -panelPx
            },
            positionalThreshold = { distance -> distance * 0.4f },
            velocityThreshold = { with(density) { 120.dp.toPx() } },
            animationSpec = tween(220),
        )
    }

    // requireOffset() lanza si aún no hay anclas resueltas; las anclas se pasan en el
    // constructor, pero el primer frame puede llegar antes de que se calcule el offset.
    val offset = state.offset.let { if (it.isNaN()) 0f else it }

    Box(modifier) {
        // El panel solo se compone cuando la fila está abriéndose: si estuviera siempre
        // presente, sus botones seguirían siendo tocables por debajo de la fila cerrada.
        if (offset < -1f) {
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .padding(end = PanelEndPadding),
                horizontalArrangement = Arrangement.spacedBy(ButtonGap, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions.forEach { action ->
                    Box(
                        modifier = Modifier
                            .size(ButtonSize)
                            .clip(CircleShape)
                            .background(action.tint)
                            .clickable {
                                scope.launch { state.animateTo(SwipeState.Closed) }
                                action.onClick()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(action.icon, contentDescription = action.label, tint = Color.White)
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offsetX { offset.roundToInt() }
                .anchoredDraggable(state, Orientation.Horizontal),
        ) {
            content()

            // Con el panel abierto, un tap sobre la fila la cierra en vez de activar lo
            // que haya debajo: si no, tocar la card abierta navegaría al editor, que no
            // es lo que espera quien acaba de descubrir los botones.
            if (offset < -1f) {
                Box(
                    Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { scope.launch { state.animateTo(SwipeState.Closed) } },
                )
            }
        }
    }

    // Al abrirse, avisa al coordinador para que la fila anterior se cierre.
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == SwipeState.Open) controller.open(token)
    }

    // Y se cierra cuando el coordinador da paso a otra fila o pide cerrar todo.
    LaunchedEffect(controller.openToken) {
        if (controller.openToken !== token && state.currentValue == SwipeState.Open) {
            state.animateTo(SwipeState.Closed)
        }
    }

    // Si cambia el número de acciones (p. ej. un workout deja de ser rotativo) las anclas
    // se recrean, y la fila debe volver a su sitio en vez de quedar desplazada a medias.
    LaunchedEffect(actions.size) { state.animateTo(SwipeState.Closed) }
}

private fun Modifier.offsetX(offset: () -> Int): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
        placeable.placeRelative(offset(), 0)
    }
}
