package com.maurozegarra.master.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseMediaJsonTest {

    @Test
    fun `round trip keeps video and instructions`() {
        val media = mapOf(
            "ex_cat_cow" to ExerciseMedia(
                videoFile = "ex_cat_cow.mp4",
                instructions = listOf("Start on all fours", "Arch the back", "Round the back"),
            ),
            "ex_open_book" to ExerciseMedia(videoFile = "ex_open_book.mp4"),
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
    fun `entries without video or instructions are dropped`() {
        // Un ejercicio al que se le quito el video y no tiene pasos no debe ocupar sitio
        // en el mapa ni viajar en el respaldo.
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

    @Test
    fun `decode tolerates an entry with only instructions`() {
        val back = ExerciseMediaJson.decode("""{"ex_a":{"instructions":["one"]}}""")

        assertEquals(listOf("one"), back["ex_a"]?.instructions)
        assertEquals("", back["ex_a"]?.videoFile)
    }

    @Test
    fun `unknown exercise has no media`() {
        assertNull(ExerciseMediaJson.decode("{}")["ex_nope"])
    }
}
