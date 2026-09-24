package com.maurozegarra.master.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Un bloque con ejercicios por lados no sale "Partial" estando hecho (TD-155). */
class WorkoutCompleteTest {

    private val mcgill = Training(
        id = 1L, name = "LUMBAR (short)",
        workouts = listOf(
            Workout(
                id = 1L, name = "McGill Big 3",
                exercises = listOf(
                    Exercise(id = 1L, exerciseId = "ex_curl_up", name = "Curl-up", sets = 6, workMode = WorkMode.TIME, workValue = 10),
                    Exercise(id = 2L, exerciseId = "ex_side_plank", name = "Side Plank", sets = 6, workMode = WorkMode.TIME, workValue = 10, sides = listOf("Left", "Right")),
                    Exercise(id = 3L, exerciseId = "ex_bird_dog", name = "Bird Dog", sets = 6, workMode = WorkMode.TIME, workValue = 10),
                ),
            ),
        ),
    )

    @Test
    fun `la plancha por lados cuenta como dos`() {
        // Tres ejercicios, cuatro registros: el curl-up, la plancha izquierda, la derecha y
        // el bird dog. El 23-sep el total decia 3 y McGill salia "Partial" con todo hecho.
        assertEquals(mapOf(0 to 4), SessionRecorder.exercisesPerWorkout(StepEngine.buildSteps(mcgill)))
    }

    @Test
    fun `una sesion hecha entera sale completa, con el total bien contado`() {
        val r = SessionRecorder()
        val pasos = StepEngine.buildSteps(mcgill)
        r.setTotalExercisesByWorkout(SessionRecorder.exercisesPerWorkout(pasos))
        pasos.filter { it.kind == StepKind.WORK }.forEach { r.onWorkStepCompleted(it) }
        assertTrue(SessionRecorder.workoutComplete(r.build()))
    }

    private fun rec(id: String, side: String = "", total: Int, done: Int = 6) = ExerciseRecord(
        exerciseId = id, name = id, workoutName = "McGill Big 3", workoutIndex = 0, side = side,
        setsCompleted = done, totalSets = 6, sets = emptyList(), timeBased = true, totalExercisesInWorkout = total,
    )

    @Test
    fun `las sesiones guardadas con el total mal contado tambien salen completas`() {
        // Las del 22 y el 23-sep se guardaron con 3: no se reescriben, se leen bien.
        val guardadas = listOf(rec("ex_curl_up", total = 3), rec("ex_side_plank", "Left", 3), rec("ex_side_plank", "Right", 3), rec("ex_bird_dog", total = 3))
        assertTrue(SessionRecorder.workoutComplete(guardadas))
    }

    @Test
    fun `lo que falta o quedo a medias sigue siendo Partial`() {
        // Falta el bird dog.
        assertFalse(SessionRecorder.workoutComplete(listOf(rec("ex_curl_up", total = 4), rec("ex_side_plank", "Left", 4), rec("ex_side_plank", "Right", 4))))
        // La plancha derecha se corto en la serie 4.
        assertFalse(SessionRecorder.workoutComplete(listOf(rec("ex_curl_up", total = 4), rec("ex_side_plank", "Left", 4), rec("ex_side_plank", "Right", 4, done = 3), rec("ex_bird_dog", total = 4))))
    }
}
