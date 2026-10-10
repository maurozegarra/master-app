package com.maurozegarra.master.model

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow

/**
 * Lo que el tablero de la pantalla principal calcula sin Android (TD-194), para probarlo.
 * El dibujo está en ui/master/HomeDashboard.kt.
 */
object Dashboard {

    /**
     * El latido de los dos puntos del reloj, de 0 a 1, según los milisegundos dentro del
     * segundo: un golpe fuerte que sube rápido y baja más lento, y otro más chico enseguida
     * ("pum-pum"). Lo pidió así el 9-oct: que de verdad parezca que laten, no que se prendan y
     * se apaguen. Las dos campanas son las del prototipo que aprobó.
     */
    fun beat(ms: Int): Float {
        val lub = exp(-((ms - 70) / 55.0).pow(2))
        val dub = 0.6 * exp(-((ms - 260) / 70.0).pow(2))
        return max(lub, dub).toFloat()
    }

    /**
     * Cuánto se corre el reloj, en dp, según el minuto: unos píxeles cada minuto, en un ciclo
     * de 15, para que una pantalla AMOLED que se queda prendida no lo deje marcado. Lo bastante
     * poco para que no se note.
     */
    fun burnInShift(minuteOfHour: Int): Pair<Int, Int> {
        val m = minuteOfHour % 15
        return (m % 5 - 2) to (m / 5 - 1)
    }

    /** El agua del día en la tarjeta: litros con un decimal desde un litro, ml antes. */
    fun water(ml: Int): String = if (ml >= 1000) "%.1f L".format(java.util.Locale.US, ml / 1000.0) else "$ml ml"

    /** El barrido que vacía la regla al cerrar el minuto: 1 al empezar, 0 a los 0.6 s. */
    fun wipe(secondsWithFraction: Double): Float =
        if (secondsWithFraction >= WIPE_SEC) 0f else (1 - secondsWithFraction / WIPE_SEC).toFloat()

    /** La estela del cometa: cuántos segundos tarda en apagarse una marca ya recorrida. */
    const val TRAIL_SEC = 8.0
    private const val WIPE_SEC = 0.6
}
