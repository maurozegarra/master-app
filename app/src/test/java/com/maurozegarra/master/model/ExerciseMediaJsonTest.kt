package com.maurozegarra.master.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseMediaJsonTest {

    @Test
    fun `round trip keeps the instructions of every exercise`() {
        val media = mapOf(
            "ex_cat_cow" to ExerciseMedia(
                instructions = listOf("Start on all fours", "Arch the back", "Round the back"),
            ),
            "ex_open_book" to ExerciseMedia(instructions = listOf("Lie on your side")),
        )

        val back = ExerciseMediaJson.decode(ExerciseMediaJson.encode(media))

        assertEquals(media, back)
    }

    @Test
    fun `instructions keep their order`() {
        val steps = (1..6).map { "step $it" }
        val media = mapOf("ex_squat" to ExerciseMedia(instructions = steps))

        val back = ExerciseMediaJson.decode(ExerciseMediaJson.encode(media))

        assertEquals(steps, back["ex_squat"]?.instructions)
    }

    @Test
    fun `entries without instructions are dropped`() {
        // Un ejercicio sin pasos no debe ocupar sitio en el mapa ni viajar en el respaldo.
        val media = mapOf("ex_empty" to ExerciseMedia())

        assertEquals("{}", ExerciseMediaJson.encode(media))
        assertTrue(ExerciseMediaJson.decode(ExerciseMediaJson.encode(media)).isEmpty())
    }

    @Test
    fun `decode tolerates garbage`() {
        assertTrue(ExerciseMediaJson.decode("").isEmpty())
        assertTrue(ExerciseMediaJson.decode("not json").isEmpty())
        assertTrue(ExerciseMediaJson.decode("[]").isEmpty())
    }

    /** Los respaldos viejos traen un campo video que ya no significa nada. */
    @Test
    fun `decode ignores the legacy video field`() {
        val back = ExerciseMediaJson.decode("""{"ex_a":{"video":"ex_a.mp4","instructions":["one"]}}""")

        assertEquals(mapOf("ex_a" to ExerciseMedia(listOf("one"))), back)
    }

    /** Un respaldo viejo cuyo unico contenido era el video se queda sin nada que aportar. */
    @Test
    fun `an old entry with only a video is dropped`() {
        assertTrue(ExerciseMediaJson.decode("""{"ex_a":{"video":"ex_a.mp4"}}""").isEmpty())
    }

    @Test
    fun `unknown exercise has no media`() {
        assertNull(ExerciseMediaJson.decode("{}")["ex_nope"])
    }
}
