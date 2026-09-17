package com.maurozegarra.master.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.maurozegarra.master.ui.theme.AppTheme
import kotlin.math.roundToInt

/**
 * Deslizar para refrescar, escrito a mano.
 *
 * No se usa `PullToRefreshContainer` de material3 porque en este proyecto tumba el app al
 * componer la lista: se corre Compose UI 1.10 contra `foundation-layout` clavado en 1.6.8
 * —un pin que viene de antes—, y ese componente llama a una API de layout que en 1.6.8 no
 * existe (`maybeCachedBoxMeasurePolicy`). Aquí solo se usan primitivas que el app ya usa
 * en otras pantallas.
 *
 * El gesto es deliberadamente el secundario: lo asignado llega solo al abrir el app, y el
 * sondeo en segundo plano avisa aunque esté cerrado. Esto es la salida para el impaciente,
 * y por eso no ocupa un milímetro de pantalla hasta que alguien tira **a propósito**.
 */
class PullToSyncState internal constructor(
    private val thresholdPx: Float,
    private val deadZonePx: Float,
) {

    /**
     * Lo que el DEDO lleva arrastrado pasado el tope de la lista, en bruto.
     *
     * Se guarda aparte de [offset] porque la zona muerta se descuenta de aquí: el
     * indicador no empieza a existir hasta que este arrastre pasa de [deadZonePx].
     */
    private var arrastre = 0f

    /** Cuánto se ha tirado, ya amortiguado y sin la zona muerta, en píxeles. */
    var offset by mutableStateOf(0f)
        private set

    /** Si se soltó pasado el umbral y toca ir a la red. Lo apaga [finish]. */
    var refreshing by mutableStateOf(false)
        private set

    val progress: Float get() = (offset / thresholdPx).coerceIn(0f, 1f)

    fun finish() {
        refreshing = false
        arrastre = 0f
        offset = 0f
    }

    private fun recalcular() {
        offset = ((arrastre - deadZonePx) * DRAG_FACTOR).coerceIn(0f, thresholdPx * MAX_STRETCH)
    }

    private fun soltar() {
        if (refreshing) return
        if (offset >= thresholdPx) {
            refreshing = true
        } else {
            arrastre = 0f
            offset = 0f
        }
    }

    val connection = object : NestedScrollConnection {

        /**
         * Al empujar hacia arriba se recoge primero el tirón. Sin esto la lista empezaría
         * a desplazarse con el indicador todavía desplegado encima.
         */
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (refreshing || available.y >= 0f || arrastre <= 0f) return Offset.Zero
            val used = minOf(-available.y, arrastre)
            arrastre -= used
            recalcular()
            return Offset(0f, -used)
        }

        /**
         * Solo llega lo que la lista NO pudo consumir, o sea el sobrante cuando ya está
         * arriba del todo. De ahí que tirar en mitad de la lista no haga nada.
         *
         * Y solo cuenta si viene del DEDO. El sobrante de un impulso —subir la lista de un
         * manotazo y que llegue al tope con carrera— no es nadie pidiendo nada: inflaba el
         * tirón solo, y como el fling ya había pasado por [onPreFling], el indicador se
         * quedaba puesto girando sin que nadie lo soltara nunca.
         */
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (refreshing || source != NestedScrollSource.Drag || available.y <= 0f) return Offset.Zero
            arrastre += available.y
            recalcular()
            return Offset(0f, available.y)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            soltar()
            return Velocity.Zero
        }

        /** Red de seguridad: nada puede quedarse desplegado después de un impulso. */
        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            soltar()
            return Velocity.Zero
        }
    }

    private companion object {
        /** Se avanza la mitad de lo que se arrastra: el tirón tiene que costar. */
        const val DRAG_FACTOR = 0.5f
        const val MAX_STRETCH = 1.6f
    }
}

@Composable
fun rememberPullToSyncState(): PullToSyncState {
    val density = LocalDensity.current
    val threshold = with(density) { THRESHOLD.toPx() }
    val deadZone = with(density) { DEAD_ZONE.toPx() }
    return remember(threshold, deadZone) { PullToSyncState(threshold, deadZone) }
}

/**
 * El indicador. No existe mientras nadie tira.
 *
 * La rueda que gira sale **solo mientras se sincroniza**. Mientras se tira se ve una flecha
 * que se va enderezando hasta apuntar hacia arriba al llegar al umbral: girar es lo que hace
 * el app cuando está ocupado, y ponerlo a girar antes de que nadie haya pedido nada es
 * decirle al usuario que algo está pasando cuando no pasa nada.
 */
@Composable
fun PullToSyncIndicator(state: PullToSyncState, accent: Color, modifier: Modifier = Modifier) {
    if (state.offset <= 0f && !state.refreshing) return
    val size = 36.dp
    val sizePx = with(LocalDensity.current) { size.toPx() }
    Box(
        modifier = modifier
            .offset { IntOffset(0, (state.offset - sizePx).roundToInt()) }
            .alpha(state.progress.coerceAtLeast(0.35f))
            .size(size)
            .clip(CircleShape)
            .background(AppTheme.colors.surface),
        contentAlignment = Alignment.Center,
    ) {
        if (state.refreshing) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = accent,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                Icons.Default.ArrowDownward,
                contentDescription = null,
                modifier = Modifier.size(20.dp).rotate(state.progress * 180f),
                tint = if (state.progress >= 1f) accent else AppTheme.colors.textDim,
            )
        }
    }
}

private val THRESHOLD = 72.dp

/**
 * Los primeros milímetros no cuentan.
 *
 * Al mirar el primer training de la lista el dedo se pasa del tope sin querer, y por eso
 * asomaba el indicador: "se activa al apenas desplazarme hacia arriba, la idea era que
 * saliera con un swipe down deliberado". Con la zona muerta, tirar de verdad cuesta
 * DEAD_ZONE + umbral/DRAG_FACTOR de recorrido, y mirar la lista no cuesta nada.
 */
private val DEAD_ZONE = 32.dp
