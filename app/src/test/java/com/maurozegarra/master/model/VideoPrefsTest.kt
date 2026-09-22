package com.maurozegarra.master.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** El video apagado es del telefono, no del training (TD-154). */
class VideoPrefsTest {

    private fun ex(id: Long, exerciseId: String, show: Boolean = true) =
        Exercise(id = id, exerciseId = exerciseId, name = exerciseId, showVideo = show)

    private val lumbar = Training(
        id = 1L, uid = "uid-lumbar", name = "LUMBAR",
        workouts = listOf(
            Workout(id = 1L, name = "Warm", exercises = listOf(ex(1, "ex_walk"))),
            Workout(id = 2L, name = "McGill", exercises = listOf(ex(2, "ex_curl_up"))),
            Workout(id = 3L, name = "Cool", exercises = listOf(ex(3, "ex_walk"))),
        ),
    )

    private fun visto(t: Training, hidden: Set<String>, exerciseId: String) =
        VideoPrefs.apply(t, hidden).workouts.flatMap { it.exercises }.filter { it.exerciseId == exerciseId }.map { it.showVideo }

    @Test
    fun `apagado sobrevive a una revision que trae todo encendido`() {
        // El 22-sep: apagaba un video y la revision siguiente lo volvia a encender.
        val apagado = VideoPrefs.set(emptySet(), lumbar, "ex_curl_up", show = false)
        val revision = lumbar.copy(workouts = lumbar.workouts.map { w -> w.copy(exercises = w.exercises.map { it.copy(id = it.id + 100) }) })
        assertEquals(listOf(false), visto(revision, apagado, "ex_curl_up"))
    }

    @Test
    fun `sirve en un training asignado, que nadie puede editar`() {
        // NIKO no tenia boton: su training llega asignado y no se toca.
        val suyo = lumbar.copy(uid = "uid-niko", assigned = true)
        val apagado = VideoPrefs.set(emptySet(), suyo, "ex_walk", show = false)
        assertEquals(listOf(false, false), visto(suyo, apagado, "ex_walk"))
        // Y no se cuela en otro training que use el mismo movimiento.
        assertEquals(listOf(true, true), visto(lumbar, apagado, "ex_walk"))
    }

    @Test
    fun `lo apagado en el editor pasa al telefono`() {
        val borrador = lumbar.copy(
            workouts = lumbar.workouts.map { w -> w.copy(exercises = w.exercises.map { if (it.id == 3L) it.copy(showVideo = false) else it }) },
        )
        val hidden = VideoPrefs.fromTraining(emptySet(), borrador)
        assertTrue(VideoPrefs.key("uid-lumbar", "ex_walk") in hidden)
        assertFalse(VideoPrefs.key("uid-lumbar", "ex_curl_up") in hidden)
    }

    @Test
    fun `lo que ya estaba apagado se conserva al instalar la version nueva`() {
        val conApagado = lumbar.copy(workouts = listOf(Workout(id = 9L, name = "W", exercises = listOf(ex(9, "ex_curl_up", show = false)))))
        assertEquals(setOf(VideoPrefs.key("uid-lumbar", "ex_curl_up")), VideoPrefs.migrate(listOf(conApagado)))
    }
}
