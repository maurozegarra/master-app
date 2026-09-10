package com.maurozegarra.master.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Editar el training con la corrida en marcha.
 *
 * Lo que se protege aqui es una sola cosa: que rehacer la cola no haga repetir trabajo ya
 * hecho ni lo borre del historial.
 */
class LiveEditTest {

    private fun jump(sets: Int, sec: Int) = Exercise(
        id = 1,
        exerciseId = "ex_rope_jumping",
        name = "Rope Jumping",
        sets = sets,
        workMode = WorkMode.TIME,
        workValue = sec,
        restSec = 0,
    )

    private fun push() = Exercise(
        id = 2,
        exerciseId = "ex_push_up",
        name = "Push-up",
        sets = 1,
        workMode = WorkMode.TIME,
        workValue = 20,
        restSec = 0,
    )

    private fun training(vararg exercises: Exercise) = Training(
        id = 1,
        uid = "uid-1",
        name = "Cardio",
        workouts = listOf(Workout(id = 10, name = "Base", exercises = exercises.toList())),
    )

    private fun works(steps: List<PlayerStep>) = steps.filter { it.kind == StepKind.WORK }

    // ---------- Reubicar ----------

    /** El caso del encargo: 16x30s, vas por la 15, lo dejas en 17 series de 40s. */
    @Test
    fun `more sets resumes on the same set, not at the start`() {
        val before = StepEngine.buildSteps(training(jump(16, 30), push()))
        val current = works(before)[14]

        val after = StepEngine.buildSteps(training(jump(17, 40), push()))
        val at = StepEngine.relocate(current, after)

        assertEquals(14, at)
        assertEquals(14, after[at].setIndex)
        assertEquals("ex_rope_jumping", after[at].ownerExerciseId)
    }

    /** Si esa serie ya no existe se sigue hacia ADELANTE, nunca hacia atras. */
    @Test
    fun `a set that no longer exists moves on to what comes next`() {
        val before = StepEngine.buildSteps(training(jump(16, 30), push()))
        val current = works(before)[14]

        val after = StepEngine.buildSteps(training(jump(3, 30), push()))
        val at = StepEngine.relocate(current, after)

        assertEquals("ex_push_up", after[at].ownerExerciseId)
    }

    /** Quitar el ejercicio entero tampoco puede devolver una posicion anterior. */
    @Test
    fun `removing the exercise lands on the next one`() {
        val before = StepEngine.buildSteps(training(jump(4, 30), push()))
        val current = works(before)[2]

        val after = StepEngine.buildSteps(training(push()))
        val at = StepEngine.relocate(current, after)

        assertEquals("ex_push_up", after[at].ownerExerciseId)
    }

    /**
     * Dos veces el mismo ejercicio del catalogo en un workout: reubicar tiene que caer en
     * la segunda aparicion, no en la primera, o se repetiria todo el bloque.
     */
    @Test
    fun `the same exercise twice in a workout resolves to the right one`() {
        val second = jump(4, 30).copy(id = 3)
        val before = StepEngine.buildSteps(training(jump(4, 30), second))
        val current = works(before).last { it.exerciseIndex == 1 }

        val after = StepEngine.buildSteps(training(jump(4, 30), second.copy(sets = 6)))
        val at = StepEngine.relocate(current, after)

        assertEquals(1, after[at].exerciseIndex)
        assertEquals(3, after[at].setIndex)
    }

    /** El descanso de una serie va antes que el trabajo de la siguiente, no despues. */
    @Test
    fun `rest of a set comes before the work of the next one`() {
        val withRest = jump(4, 30).copy(restSec = 15)
        val steps = StepEngine.buildSteps(training(withRest))
        val rest0 = steps.first { it.kind == StepKind.REST && it.setIndex == 0 }

        val at = StepEngine.relocate(rest0, steps)

        assertEquals(StepKind.REST, steps[at].kind)
        assertEquals(0, steps[at].setIndex)
    }

    @Test
    fun `exerciseIndex is the position inside its own workout`() {
        val steps = StepEngine.buildSteps(
            Training(
                id = 1, uid = "u", name = "T",
                workouts = listOf(
                    Workout(id = 10, exercises = listOf(jump(1, 10), push())),
                    Workout(id = 11, exercises = listOf(push())),
                ),
            ),
        )

        assertEquals(listOf(0, 1, 0), works(steps).map { it.exerciseIndex })
        assertEquals(listOf(0, 0, 1), works(steps).map { it.workoutIndex })
    }

    // ---------- El historial no pierde lo hecho ----------

    /**
     * Bajar las series por debajo de las ya hechas NO puede borrarlas: se hicieron.
     * Antes se recorria solo `0 until totalSets` y las de mas desaparecian.
     */
    @Test
    fun `cutting the sets short keeps what was already done`() {
        val rec = SessionRecorder()
        val long = works(StepEngine.buildSteps(training(jump(5, 30))))
        long.take(4).forEach { rec.onWorkStepCompleted(it) }

        // El usuario baja a 2 series y rehace la ultima con la cola nueva.
        val short = works(StepEngine.buildSteps(training(jump(2, 30))))
        rec.onWorkStepCompleted(short[1])

        val sets = rec.build().single().sets
        assertEquals(4, sets.size)
    }

    /** Y las viejas conservan su duracion cuando las nuevas llegan con otra. */
    @Test
    fun `sets already done keep the seconds they were done with`() {
        val rec = SessionRecorder()
        works(StepEngine.buildSteps(training(jump(16, 30)))).take(15).forEach { rec.onWorkStepCompleted(it) }
        works(StepEngine.buildSteps(training(jump(17, 40)))).drop(15).forEach { rec.onWorkStepCompleted(it) }

        val sets = rec.build().single().sets

        assertEquals(17, sets.size)
        assertTrue(sets.take(15).all { it.durationSec == 30 })
        assertTrue(sets.drop(15).all { it.durationSec == 40 })
    }
}
