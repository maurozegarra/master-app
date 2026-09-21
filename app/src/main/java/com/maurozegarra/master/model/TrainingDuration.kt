package com.maurozegarra.master.model

/**
 * Cuánto dura un training, para enseñarlo en su tarjeta (TD-040).
 *
 * **Manda lo que pasó, no lo que el motor calcula.** La cola del player suma trabajo,
 * descansos y preparaciones, pero no sabe nada de lo que ocurre entre ejercicios: cambiar
 * discos, ir a por la mancuerna, contestar el feedback del peso, la pregunta del dolor. En
 * la rutina lumbar eso son treinta minutos de diferencia —el motor estima unos 43 y las
 * cuatro sesiones reales fueron 55, 67, 79 y 83—, así que enseñar el cálculo sería poner un
 * número que miente.
 *
 * Por eso se usa la **mediana de lo ya entrenado**, y el cálculo del motor queda solo para
 * un training que nadie ha corrido todavía, donde no hay nada mejor.
 */
object TrainingDuration {

    /** Cuántas sesiones pasadas se miran. Las suficientes para una mediana, no tantas como
     *  para que una rutina que cambió hace un mes siga mandando sobre la de ahora. */
    const val LOOK_BACK = 5

    /**
     * Minutos que enseñar, redondeados hacia arriba. 0 si el training no tiene contenido.
     *
     * Solo cuentan las sesiones **completas**: una a medias dice cuánto se aguantó ese día,
     * no cuánto dura la rutina, y meterla en la mediana la hundiría.
     */
    fun minutes(training: Training, sessions: List<SessionLog>): Int {
        val reales = sessions
            .filter { it.trainingId == training.id && it.status == SessionStatus.COMPLETED && it.durationSec > 0 }
            .sortedByDescending { it.completedAt }
            .take(LOOK_BACK)
            .map { it.durationSec }

        val segundos = if (reales.isNotEmpty()) mediana(reales) else training.estimatedSec()
        return (segundos + 59) / 60
    }

    /** true si el número sale de lo entrenado y no del cálculo: la tarjeta no lo distingue,
     *  pero el historial y las pruebas sí quieren saberlo. */
    fun fromHistory(training: Training, sessions: List<SessionLog>): Boolean =
        sessions.any { it.trainingId == training.id && it.status == SessionStatus.COMPLETED && it.durationSec > 0 }

    private fun mediana(valores: List<Int>): Int {
        val orden = valores.sorted()
        val mitad = orden.size / 2
        return if (orden.size % 2 == 1) orden[mitad] else (orden[mitad - 1] + orden[mitad]) / 2
    }
}
