package com.maurozegarra.master.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Qué le va a llegar incompleto a quien recibe (TD-143). */
class DeliveryCheckTest {

    private fun ex(id: String, name: String) = Exercise(id = id.hashCode().toLong(), exerciseId = id, name = name)

    private val training = Training(
        id = 1L,
        name = "NIKO 2 · Muay Thai",
        workouts = listOf(
            Workout(id = 1L, name = "Calentamiento", exercises = listOf(ex("ex_rope_jumping", "Rope Jumping"))),
            Workout(
                id = 2L,
                name = "Rounds",
                rotating = true,
                variants = listOf(
                    WorkoutVariant(id = 3L, name = "A", exercises = listOf(ex("ex_heavy_bag", "Heavy Bag"))),
                ),
            ),
        ),
    )

    private val conPasos = mapOf("ex_heavy_bag" to ExerciseMedia(listOf("Guardia arriba.")))

    @Test
    fun `un ejercicio sin instrucciones sale, con video o sin el`() {
        val huecos = DeliveryCheck.gaps(training, conPasos, publishedVideos = setOf("ex_rope_jumping", "ex_heavy_bag"))
        assertEquals(listOf("ex_rope_jumping"), huecos.map { it.exerciseId })
        assertTrue(huecos.single().noInstructions)
        assertTrue(!huecos.single().noVideo)
    }

    @Test
    fun `los ejercicios de las variantes tambien se miran`() {
        // Un workout rotativo esconde sus ejercicios dentro de las variantes; mirar solo
        // `exercises` dejaria fuera justo los rounds, que es casi todo el dia de Muay Thai.
        val huecos = DeliveryCheck.gaps(training, conPasos, publishedVideos = emptySet())
        assertEquals(setOf("ex_rope_jumping", "ex_heavy_bag"), huecos.map { it.exerciseId }.toSet())
        assertTrue(huecos.first().noInstructions)
    }

    @Test
    fun `lo que esta completo no aparece`() {
        val completo = conPasos + ("ex_rope_jumping" to ExerciseMedia(listOf("Codos pegados.")))
        val huecos = DeliveryCheck.gaps(
            training,
            completo,
            publishedVideos = setOf("ex_rope_jumping", "ex_heavy_bag"),
        )
        assertTrue(huecos.isEmpty())
    }

    @Test
    fun `un ejercicio repetido en el training sale una sola vez`() {
        val dosVeces = training.copy(
            workouts = training.workouts + Workout(
                id = 9L, name = "Otra vez", exercises = listOf(ex("ex_rope_jumping", "Rope Jumping")),
            ),
        )
        assertEquals(1, DeliveryCheck.gaps(dosVeces, conPasos, setOf("ex_heavy_bag", "ex_rope_jumping")).size)
    }
}
