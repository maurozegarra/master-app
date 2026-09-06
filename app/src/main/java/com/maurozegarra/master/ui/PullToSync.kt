package com.maurozegarra.master.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
 * y por eso no ocupa un milímetro de pantalla hasta que alguien tira.
 */
class PullToSyncState internal constructor(private val thresholdPx: Float) {

    /** Cuánto se ha tirado, ya amortiguado, en píxeles. */
    var offset by mutableStateOf(0f)
        private set

    /** Si se soltó pasado el umbral y toca ir a la red. Lo apaga [finish]. */
    var refreshing by mutableStateOf(false)
        private set

    val progress: Float get() = (offset / thresholdPx).coerceIn(0f, 1f)

    fun finish() {
        refreshing = false
        offset = 0f
    }

    val connection = object : NestedScrollConnection {

        /**
         * Al empujar hacia arriba se recoge primero el tirón. Sin esto la lista empezaría
         * a desplazarse con el indicador todavía desplegado encima.
         */
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (refreshing || available.y >= 0f || offset <= 0f) return Offset.Zero
            val used = minOf(-available.y, offset)
            offset -= used
            return Offset(0f, -used)
        }

        /**
         * Solo llega lo que la lista NO pudo consumir, o sea el sobrante cuando ya está
         * arriba del todo. De ahí que tirar en mitad de la lista no haga nada.
         */
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (refreshing || available.y <= 0f) return Offset.Zero
            offset = (offset + available.y * DRAG_FACTOR).coerceAtMost(thresholdPx * MAX_STRETCH)
            return Offset(0f, available.y)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (refreshing) return Velocity.Zero
            if (offset >= thresholdPx) refreshing = true else offset = 0f
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
    val threshold = with(LocalDensity.current) { THRESHOLD.toPx() }
    return remember(threshold) { PullToSyncState(threshold) }
}

/** El indicador. No existe mientras nadie tira. */
@Composable
fun PullToSyncIndicator(state: PullToSyncState, accent: Color, modifier: Modifier = Modifier) {
    if (state.offset <= 0f) return
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
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = accent,
            strokeWidth = 2.dp,
        )
    }
}

private val THRESHOLD = 72.dp
