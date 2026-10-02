package com.maurozegarra.master.model

import com.maurozegarra.master.data.MasterDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** El detalle del historial dice al menos lo que dice la previa (TD-181). */
class SetRunsTest {

    @Test
    fun `doce aguantes iguales son una fila`() {
        // El 1-oct, el curl-up eran doce filas de "10 s".
        val sets = List(12) { SetRecord(durationSec = 10, restSec = if (it == 5 || it == 9) 30 else 3) }
        val r = SetRuns.of(sets)
        assertEquals(1, r.size)
        assertEquals(0, r[0].first)
        assertEquals(11, r[0].last)
    }

    @Test
    fun `una serie distinta corta el tramo y se sigue viendo`() {
        val bien = SetRecord(reps = 10, weightKg = 10.0, feedbackDeltaKg = 0.0)
        val sets = listOf(bien.copy(weightKg = 7.5, feedbackDeltaKg = 2.5), bien, bien)
        assertEquals(listOf(0 to 0, 1 to 2), SetRuns.of(sets).map { it.first to it.last })
        // Otra respuesta tambien corta: es justo lo que el historial tiene que ensenar.
        val pesada = listOf(bien, bien.copy(feedbackDeltaKg = -2.5))
        assertEquals(2, SetRuns.of(pesada).size)
    }

    @Test
    fun `los descansos entre series, como en la previa`() {
        val sets = List(12) { SetRecord(durationSec = 10, restSec = if (it == 5 || it == 9) 30 else 3) }
        assertEquals(listOf(3, 30), SetRuns.rests(sets))
        assertEquals("rest 3 s–30 s", TrainingPreview.rest(SetRuns.rests(sets)))
        // Una sesion de antes no tiene descansos, y no se inventan.
        assertNull(TrainingPreview.rest(SetRuns.rests(List(3) { SetRecord(durationSec = 10) })))
    }

    @Test
    fun `el recorder guarda el descanso que paso de verdad`() {
        // El curl-up del LUMBAR, como lo arma el player: se descansa entero salvo uno, que se
        // salta a los 10 s.
        val pasos = StepEngine.buildSteps(MasterDefaults.lumbarTraining("en"))
            .filter { it.ownerExerciseId == "ex_curl_up" }
        val r = SessionRecorder()
        pasos.forEach { p ->
            when (p.kind) {
                StepKind.WORK -> r.onWorkStepCompleted(p)
                StepKind.REST -> r.onRestEnded(p, if (p.setIndex == 5) 10 else p.durationSec)
                else -> {}
            }
        }
        val sets = r.build().single().sets
        assertEquals(10, sets[5].restSec)
        assertEquals(listOf(3, 10, 30), SetRuns.rests(sets))
        // Y en el historial sigue siendo una fila: el descanso no corta el tramo.
        assertEquals(1, SetRuns.of(sets).size)
    }

    @Test
    fun `alternando, el primer lado descansa lo mismo que el ultimo`() {
        // El carry: izquierda y derecha seguidas, y el descanso al cerrar la serie (TD-156).
        val pasos = StepEngine.buildSteps(MasterDefaults.lumbarShortTraining("en"))
            .filter { it.ownerExerciseId == "ex_suitcase_carry" }
        val r = SessionRecorder()
        pasos.forEach { p ->
            when (p.kind) {
                StepKind.WORK -> r.onWorkStepCompleted(p)
                StepKind.REST -> r.onRestEnded(p, p.durationSec)
                else -> {}
            }
        }
        val porLado = r.build().associateBy { it.side }
        assertEquals(SetRuns.rests(porLado.getValue("Right").sets), SetRuns.rests(porLado.getValue("Left").sets))
        assertEquals(listOf(60), SetRuns.rests(porLado.getValue("Left").sets))
    }

    @Test
    fun `el descanso viaja en el respaldo`() {
        val s = SessionLog(
            id = 1L, trainingId = 1L, trainingName = "T", completedAt = 1L,
            exercises = listOf(ExerciseRecord("x", "X", workoutName = "W", workoutIndex = 0, setsCompleted = 1, totalSets = 1, timeBased = true, sets = listOf(SetRecord(durationSec = 10, restSec = 3)))),
        )
        val leido = SessionJson.decode(SessionJson.encode(listOf(s))).single()
        assertEquals(3, leido.exercises.single().sets.single().restSec)
    }
}
