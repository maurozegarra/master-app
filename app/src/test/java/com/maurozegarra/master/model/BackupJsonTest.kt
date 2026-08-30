package com.maurozegarra.master.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El respaldo es la unica red de seguridad del usuario: si el ida y vuelta pierde algo,
 * lo pierde para siempre. De ahi que se testee campo por campo y no solo el conteo.
 */
class BackupJsonTest {

    private fun sampleData(): BackupData {
        val ex = Exercise(
            id = 1,
            exerciseId = "squat",
            name = "Squat",
            note = "keep the back straight",
            prepareSec = 5,
            sets = 3,
            workMode = WorkMode.REPS,
            workValue = 12,
            restSec = 45,
            cooldownSec = 10,
            weightType = WeightType.BARBELL,
            barWeight = 20.0,
            setList = listOf(WorkSet(reps = 10, weight = 40.0), WorkSet(reps = 8, weight = 45.0)),
        )
        val rotating = Workout(
            id = 20,
            name = "Cardio",
            rotating = true,
            rotationIndex = 1,
            variants = listOf(
                WorkoutVariant(id = 30, name = "Running", exercises = listOf(ex.copy(id = 31))),
                WorkoutVariant(id = 32, name = "Rowing", exercises = emptyList()),
            ),
        )
        val simple = Workout(id = 21, name = "Lower", exercises = listOf(ex))
        return BackupData(
            trainings = listOf(
                Training(id = 10, name = "Hybrid", workouts = listOf(simple, rotating),
                    createdAt = 111, updatedAt = 222),
                Training(id = 11, name = "Empty", workouts = emptyList()),
            ),
            customExercises = listOf(ExerciseDef(id = "custom-1", name = "Sled push", custom = true)),
            sessions = listOf(
                SessionLog(id = 50, trainingId = 10, trainingName = "Hybrid", completedAt = 9_000,
                    durationSec = 1800),
            ),
        )
    }

    @Test
    fun `round trip keeps trainings workouts and variants`() {
        val data = sampleData()
        val back = BackupJson.decode(BackupJson.encode(data, exportedAt = 1_000))

        assertNotNull(back)
        assertEquals(data.trainings, back!!.trainings)
    }

    @Test
    fun `round trip keeps custom exercises and sessions`() {
        val data = sampleData()
        val back = BackupJson.decode(BackupJson.encode(data, exportedAt = 1_000))!!

        assertEquals(data.customExercises, back.customExercises)
        assertEquals(1, back.sessions.size)
        assertEquals(10L, back.sessions[0].trainingId)
        assertEquals(9_000L, back.sessions[0].completedAt)
    }

    @Test
    fun `empty backup round trips`() {
        val empty = BackupData(emptyList(), emptyList(), emptyList())
        val back = BackupJson.decode(BackupJson.encode(empty, exportedAt = 0))

        assertNotNull(back)
        assertEquals(0, back!!.trainings.size)
        assertEquals(0, back.sessions.size)
    }

    @Test
    fun `decode rejects garbage`() {
        assertNull(BackupJson.decode(""))
        assertNull(BackupJson.decode("not json"))
        assertNull(BackupJson.decode("[]"))
    }

    @Test
    fun `decode rejects a file without format`() {
        assertNull(BackupJson.decode("""{"trainings":[]}"""))
    }

    @Test
    fun `decode rejects a newer format version`() {
        val future = BackupJson.FORMAT + 1
        assertNull(BackupJson.decode("""{"format":$future,"trainings":[]}"""))
    }

    @Test
    fun `decode rejects a file without trainings`() {
        assertNull(BackupJson.decode("""{"format":1,"sessions":[]}"""))
    }

    @Test
    fun `decode rejects corrupt trainings instead of importing them as empty`() {
        // Un training sin id no se puede parsear. Devolver lista vacia seria peor que
        // fallar: reemplazaria los datos del usuario por nada.
        assertNull(BackupJson.decode("""{"format":1,"trainings":[{"name":"broken"}]}"""))
    }

    @Test
    fun `round trip keeps exercise media`() {
        val data = sampleData().copy(
            exerciseMedia = mapOf(
                "ex_cat_cow" to ExerciseMedia("ex_cat_cow.mp4", listOf("Start on all fours")),
            ),
        )
        val back = BackupJson.decode(BackupJson.encode(data, exportedAt = 1_000))!!

        assertEquals(data.exerciseMedia, back.exerciseMedia)
    }

    @Test
    fun `a format 1 backup still imports, just without media`() {
        // La version de formato existe justo para esto: los respaldos exportados antes de
        // TD-058 tienen que seguir entrando sin perder trainings ni historial.
        val v1 = """
            {
              "format": 1,
              "exportedAt": 1,
              "trainings": [{"id": 10, "name": "Hybrid", "workouts": []}],
              "customExercises": [],
              "sessions": []
            }
        """.trimIndent()

        val back = BackupJson.decode(v1)

        assertNotNull(back)
        assertEquals(1, back!!.trainings.size)
        assertEquals("Hybrid", back.trainings[0].name)
        assertTrue(back.exerciseMedia.isEmpty())
    }

    @Test
    fun `decode tolerates a backup without optional sections`() {
        val back = BackupJson.decode("""{"format":1,"trainings":[]}""")

        assertNotNull(back)
        assertEquals(0, back!!.customExercises.size)
        assertEquals(0, back.sessions.size)
    }
}
