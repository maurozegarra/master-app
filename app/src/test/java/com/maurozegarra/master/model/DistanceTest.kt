package com.maurozegarra.master.model

import com.maurozegarra.master.data.MasterDefaults
import com.maurozegarra.master.i18n.I18n
import com.maurozegarra.master.ui.master.setSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los carries se miden en metros (TD-095). */
class DistanceTest {

    @Test
    fun `el carry se registra en metros, por mano`() {
        // Era "2 reps", un viaje por mano, y el historial decia "2 x 17.5 kg".
        val carry = MasterDefaults.lumbarShortTraining("en").workouts.flatMap { it.exercises }
            .single { it.exerciseId == "ex_suitcase_carry" }
        assertEquals(WorkMode.DISTANCE, carry.workMode)
        assertEquals(listOf("Left", "Right"), carry.sides)
        assertTrue(carry.isWeighted)

        val pasos = StepEngine.buildSteps(Training(id = 1L, name = "T", workouts = listOf(Workout(id = 1L, name = "Carry", exercises = listOf(carry)))))
            .filter { it.kind == StepKind.WORK }
        assertTrue(pasos.all { it.distance && it.reps == 36 && it.manual })

        val r = SessionRecorder()
        pasos.forEach { r.onWorkStepCompleted(it) }
        val registro = r.build()
        assertEquals(listOf("Left", "Right"), registro.map { it.side })
        val serie = registro.first().sets.last()
        assertEquals(36, serie.distanceM)
        assertEquals(0, serie.reps)
        assertEquals("36 m × 20 kg", setSummary(serie, timeBased = false, I18n.EN))
    }

    @Test
    fun `los metros sobreviven al guardado, y lo viejo sigue en reps`() {
        val s = SessionLog(
            id = 1L, trainingId = 1L, trainingName = "T", completedAt = 1L,
            exercises = listOf(ExerciseRecord(exerciseId = "x", name = "x", workoutName = "w", workoutIndex = 0, setsCompleted = 1, totalSets = 1, sets = listOf(SetRecord(distanceM = 36, weightKg = 20.0)), timeBased = false)),
        )
        assertEquals(36, SessionJson.decode(SessionJson.encode(listOf(s))).single().exercises.single().sets.single().distanceM)
        // Lo guardado antes de TD-095 no trae el campo: sigue siendo "2 reps".
        assertNull(SetRecord(reps = 2, weightKg = 17.5).distanceM)
        assertEquals("2 × 17.5 kg", setSummary(SetRecord(reps = 2, weightKg = 17.5), timeBased = false, I18n.EN))
    }

    @Test
    fun `el paseo del granjero de NIKO tambien, sin lados`() {
        val granjero = MasterDefaults.nikoTrainings("es").flatMap { it.workouts }.flatMap { it.exercises }
            .single { it.exerciseId == "ex_farmers_walk" }
        assertEquals(WorkMode.DISTANCE, granjero.workMode)
        assertEquals(36, granjero.workValue)
        assertTrue(granjero.sides.isEmpty())
    }
}
