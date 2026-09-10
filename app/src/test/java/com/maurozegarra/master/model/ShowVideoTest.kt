package com.maurozegarra.master.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Si en un training se ve el video de un ejercicio es de ESA instancia, no del movimiento.
 *
 * Guardarlo por movimiento era el fallo de la v1.0.190: apagarlo en un duplicado lo
 * apagaba tambien en el training del que salio, que es justo lo que duplicar promete que
 * no pasa.
 */
class ShowVideoTest {

    private fun exercise(id: Long, showVideo: Boolean = true) =
        Exercise(id = id, exerciseId = "ex_cat_cow", name = "Cat-Cow", showVideo = showVideo)

    private fun training(vararg exercises: Exercise) = Training(
        id = 1,
        uid = "uid-1",
        name = "COLUMNA",
        workouts = listOf(
            Workout(id = 10, name = "Base", exercises = exercises.toList()),
            Workout(
                id = 11,
                name = "Rotativo",
                rotating = true,
                variants = listOf(WorkoutVariant(id = 20, name = "A", exercises = exercises.toList())),
            ),
        ),
    )

    // ---------- Serializacion ----------

    @Test
    fun `showVideo survives the round trip`() {
        val json = TrainingJson.encode(listOf(training(exercise(1, showVideo = false))))
        val back = TrainingJson.decode(json).single()

        assertFalse(back.workouts[0].exercises[0].showVideo)
        assertFalse(back.workouts[1].variants[0].exercises[0].showVideo)
    }

    /**
     * Lo que protege a todo lo ya guardado: ni los respaldos viejos ni los payloads ya
     * publicados traen el campo, y leerlos como false apagaria de golpe todos los videos.
     */
    @Test
    fun `an exercise saved before the field existed still shows its video`() {
        val json = TrainingJson.encode(listOf(training(exercise(1))))
        val stripped = json.replace("\"showVideo\":true", "\"unrelated\":true")

        val back = TrainingJson.decode(stripped).single()

        assertTrue(back.workouts[0].exercises[0].showVideo)
    }

    // ---------- Publicar ----------

    /**
     * Apagar un video es preferencia de quien lo mira. Publicarla se la impondria a los
     * demas sin salida: un training asignado no se edita, asi que quien lo recibiera no
     * podria volver a encenderlo.
     */
    @Test
    fun `publishing turns every video back on, variants included`() {
        val published = training(exercise(1, showVideo = false), exercise(2, showVideo = false))
            .forPublishing()

        assertTrue(published.workouts[0].exercises.all { it.showVideo })
        assertTrue(published.workouts[1].variants[0].exercises.all { it.showVideo })
    }

    @Test
    fun `publishing still clears the assigned badge`() {
        val published = training(exercise(1)).copy(assigned = true).forPublishing()

        assertFalse(published.assigned)
    }

    /** Lo demas del training no se toca al publicar. */
    @Test
    fun `publishing changes nothing else`() {
        val original = training(exercise(1, showVideo = false))
        val published = original.forPublishing()

        assertEquals(original.uid, published.uid)
        assertEquals(original.name, published.name)
        assertEquals(original.workouts.map { it.id }, published.workouts.map { it.id })
        assertEquals(
            original.workouts[0].exercises.map { it.copy(showVideo = true) },
            published.workouts[0].exercises,
        )
    }

    // ---------- Hasta el player ----------

    @Test
    fun `the steps of an exercise carry its showVideo`() {
        val steps = StepEngine.buildSteps(training(exercise(1, showVideo = false)))

        assertTrue(steps.isNotEmpty())
        assertTrue(steps.none { it.showVideo })
    }
}
