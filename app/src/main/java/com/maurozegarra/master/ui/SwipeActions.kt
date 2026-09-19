package com.maurozegarra.master.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
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
import com.maurozegarra.master.ui.theme.AppTheme
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Una acción del panel que se revela al deslizar. */
/**
 * Una acción del panel que aparece al deslizar una fila.
 *
 * **Sin color propio, a propósito.** Antes cada acción traía el suyo y se pintaba como un
 * círculo macizo —rojo para borrar, naranja duplicar, azul editar, verde asignar—. El rojo
 * macizo grita "algo va mal" aunque solo estés a punto de borrar algo a conciencia, y con
 * cuatro colores fuertes en la misma fila el panel pesaba más que la lista. Ahora todas se
 * dibujan igual, en blanco y huecas, y lo que las distingue es el icono.
 */
data class SwipeAction(
    val icon: ImageVector,
    /** Se usa como contentDescription: un gesto no es descubrible por lector de pantalla. */
    val label: String,
    val onClick: () -> Unit,
)

private enum class SwipeState { Closed, Open, Triggered }

private val ButtonSize = 44.dp
private val ButtonGap = 10.dp
private val PanelEndPadding = 12.dp

/**
 * Cuánto hay que arrastrar hacia la derecha para que la acción se dispare al soltar.
 *
 * 96dp y no menos: a la derecha no hay botón que tocar -el gesto ES la acción-, así que
 * tiene que costar lo suficiente como para no dispararse con un roce al hacer scroll.
 */
private val RightTrigger = 96.dp

/**
 * Grosor del borde del círculo de acción.
 *
 * 1dp no es un número al azar: es el que usa toda la pantalla principal —los círculos del
 * calendario, el del acento y el borde de la tarjeta—, así que el panel de acciones se
 * dibuja con el mismo trazo que el resto del app.
 */
private val SwipeBorder = 1.dp

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
    /**
     * Apagado, la fila no se desliza y, si estaba abierta, se cierra. Se apaga en vez de
     * quitar las acciones para no cambiar la forma del arbol: quitarlas cambia el Box que
     * envuelve el contenido y Compose lo rehace entero, con el estado que lleve dentro.
     */
    enabled: Boolean = true,
    /**
     * Acción del gesto hacia la DERECHA, si la fila tiene una (TD-138).
     *
     * No es un botón más: al arrastrar asoma su icono y la acción se ejecuta **al soltar**
     * pasado [RightTrigger]; la fila vuelve sola a su sitio. Se hizo así porque el panel
     * izquierdo ya lleva cuatro botones y un quinto ocuparía casi el ancho de la tarjeta,
     * y porque el long-press, que sería la otra vía, ya es el arrastre para reordenar.
     */
    rightAction: SwipeAction? = null,
    content: @Composable () -> Unit,
) {
    if (actions.isEmpty() && rightAction == null) {
        Box(modifier) { content() }
        return
    }

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val token = remember { Any() }
    val panelWidth = ButtonSize * actions.size + ButtonGap * (actions.size - 1) + PanelEndPadding * 2

    val state = remember(actions.size, rightAction != null) {
        val panelPx = with(density) { panelWidth.toPx() }
        AnchoredDraggableState(
            initialValue = SwipeState.Closed,
            anchors = DraggableAnchors {
                SwipeState.Closed at 0f
                if (actions.isNotEmpty()) SwipeState.Open at -panelPx
                if (rightAction != null) SwipeState.Triggered at with(density) { RightTrigger.toPx() }
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
                    // Círculo hueco sobre el fondo de la lista, que es negro puro: sin
                    // relleno, borde blanco de 1dp —el mismo grosor que usan los círculos
                    // y las tarjetas de la pantalla principal— e icono de contorno.
                    Box(
                        modifier = Modifier
                            .size(ButtonSize)
                            .clip(CircleShape)
                            .border(SwipeBorder, AppTheme.colors.textPrimary, CircleShape)
                            .clickable {
                                scope.launch { state.animateTo(SwipeState.Closed) }
                                action.onClick()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            action.icon,
                            contentDescription = action.label,
                            tint = AppTheme.colors.textPrimary,
                        )
                    }
                }
            }
        }

        // El icono del gesto a la derecha, que asoma bajo la tarjeta mientras se arrastra.
        // No es tocable a propósito: el gesto es la acción, y un botón ahí invitaría a
        // soltar antes de tiempo y a tocarlo, que es justo lo que no dispara nada.
        if (rightAction != null && offset > 1f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(start = PanelEndPadding),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    modifier = Modifier
                        .size(ButtonSize)
                        .clip(CircleShape)
                        .border(SwipeBorder, AppTheme.colors.textPrimary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        rightAction.icon,
                        contentDescription = rightAction.label,
                        tint = AppTheme.colors.textPrimary,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offsetX { offset.roundToInt() }
                .anchoredDraggable(state, Orientation.Horizontal, enabled = enabled),
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
        if (state.currentValue == SwipeState.Triggered && rightAction != null) {
            // Vuelve a su sitio ANTES de ejecutar: la fila suele irse de la lista al
            // archivarse, y si se fuera desplazada, la siguiente que ocupe su lugar
            // heredaría el desplazamiento.
            state.animateTo(SwipeState.Closed)
            rightAction.onClick()
        }
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

    LaunchedEffect(enabled) { if (!enabled) state.animateTo(SwipeState.Closed) }
}

private fun Modifier.offsetX(offset: () -> Int): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
        placeable.placeRelative(offset(), 0)
    }
}
