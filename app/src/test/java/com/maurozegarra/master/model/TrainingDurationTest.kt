package com.maurozegarra.master.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cuánto dice la tarjeta que dura un training (TD-040). */
class TrainingDurationTest {

    private val training = Training(
        id = 7L,
        name = "LUMBAR",
        workouts = listOf(
            Workout(
                id = 1L,
                name = "W",
                exercises = listOf(
                    Exercise(id = 1L, exerciseId = "ex_walk", name = "Walk", workMode = WorkMode.TIME, workValue = 600),
                ),
            ),
        ),
    )

    private fun sesion(id: Long, min: Int, trainingId: Long = 7L, status: SessionStatus = SessionStatus.COMPLETED) =
        SessionLog(
            id = id,
            trainingId = trainingId,
            trainingName = "LUMBAR",
            completedAt = id,
            durationSec = min * 60,
            status = status,
        )

    @Test
    fun `sin historial usa el calculo del motor`() {
        // Un training recien creado no tiene nada mejor que su propia cola.
        assertEquals(10, TrainingDuration.minutes(training, emptyList()))
        assertFalse(TrainingDuration.fromHistory(training, emptyList()))
    }

    @Test
    fun `con historial manda lo que de verdad duro`() {
        // El motor dice 10 minutos y las sesiones dicen otra cosa: entre ejercicios pasan
        // cosas que la cola no sabe -cambiar discos, contestar el feedback- y en la rutina
        // lumbar eso eran treinta minutos de diferencia.
        val reales = listOf(sesion(1, 55), sesion(2, 67), sesion(3, 79), sesion(4, 83))

        assertEquals(73, TrainingDuration.minutes(training, reales))
        assertTrue(TrainingDuration.fromHistory(training, reales))
    }

    @Test
    fun `la mediana aguanta un dia raro`() {
        // Un dia se quedo hablando por telefono a mitad de sesion. La media diria 45; la
        // mediana sigue diciendo lo que dura la rutina.
        val reales = listOf(sesion(1, 30), sesion(2, 32), sesion(3, 34), sesion(4, 130))

        assertEquals(33, TrainingDuration.minutes(training, reales))
    }

    @Test
    fun `una sesion a medias no cuenta`() {
        // Dice cuanto se aguanto ese dia, no cuanto dura la rutina.
        val reales = listOf(sesion(1, 60), sesion(2, 8, status = SessionStatus.PARTIAL))

        assertEquals(60, TrainingDuration.minutes(training, reales))
    }

    @Test
    fun `las sesiones de otro training no se mezclan`() {
        assertEquals(10, TrainingDuration.minutes(training, listOf(sesion(1, 90, trainingId = 99L))))
    }

    @Test
    fun `solo se miran las ultimas, no todo el historial`() {
        // Seis sesiones de 20 y luego seis de 40: la rutina cambio, y la tarjeta tiene que
        // hablar de la de ahora.
        val viejas = (1..6).map { sesion(it.toLong(), 20) }
        val nuevas = (7..12).map { sesion(it.toLong(), 40) }

        assertEquals(40, TrainingDuration.minutes(training, viejas + nuevas))
    }
}
