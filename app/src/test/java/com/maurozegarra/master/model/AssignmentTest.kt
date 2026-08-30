package com.maurozegarra.master.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssignmentTest {

    private var next = 1000L
    private fun newId(): Long = next++

    private fun own(id: Long, uid: String, name: String) =
        Training(id = id, uid = uid, name = name, assigned = false)

    private fun assigned(id: Long, uid: String, name: String) =
        Training(id = id, uid = uid, name = name, assigned = true)

    // ---------- Directorio de perfiles ----------

    @Test
    fun `reads the published profiles`() {
        val json = """
        { "format": 1, "users": [ { "id": "mauro", "name": "Mauro" }, { "id": "niko", "name": "Niko" } ] }
        """

        assertEquals(
            listOf(Profile("mauro", "Mauro"), Profile("niko", "Niko")),
            ProfileDirectoryJson.decode(json),
        )
    }

    @Test
    fun `a profile without name falls back to its id`() {
        val json = """{ "format": 1, "users": [ { "id": "niko" } ] }"""

        assertEquals(listOf(Profile("niko", "niko")), ProfileDirectoryJson.decode(json))
    }

    @Test
    fun `rejects a broken directory`() {
        assertNull(ProfileDirectoryJson.decode("no soy json"))
        assertNull(ProfileDirectoryJson.decode("""{ "format": 99, "users": [] }"""))
        assertNull(ProfileDirectoryJson.decode("""{ "format": 1 }"""))
    }

    // ---------- Trainings asignados ----------

    @Test
    fun `reads the assigned trainings`() {
        val json = """
        { "format": 1, "trainings": [ { "id": 1, "uid": "abc", "name": "MASTER" } ] }
        """

        val out = AssignedTrainingsJson.decode(json)!!

        assertEquals(listOf("abc"), out.map { it.uid })
    }

    /** No es lo mismo "no se pudo leer" que "ya no te toca ninguno": lo segundo borra. */
    @Test
    fun `a broken document is null, not an empty assignment`() {
        assertNull(AssignedTrainingsJson.decode("no soy json"))
        assertNull(AssignedTrainingsJson.decode("""{ "format": 1 }"""))
        assertNull(AssignedTrainingsJson.decode("""{ "format": 1, "trainings": [ "roto" ] }"""))
    }

    @Test
    fun `an empty assignment is valid and means none`() {
        assertEquals(emptyList<Training>(), AssignedTrainingsJson.decode("""{ "format": 1, "trainings": [] }"""))
    }

    // ---------- Fusion ----------

    @Test
    fun `an assigned training is added and marked as such`() {
        val out = mergeAssigned(emptyList(), listOf(Training(id = 1, uid = "a", name = "MASTER")), ::newId)

        assertEquals(1, out.size)
        assertEquals("MASTER", out[0].name)
        assertTrue(out[0].assigned)
    }

    /** Lo que alguien creo por su cuenta no puede costarle recibir una asignacion. */
    @Test
    fun `own trainings are never touched`() {
        val mine = own(1, "mio", "NIKO")

        val out = mergeAssigned(listOf(mine), listOf(Training(id = 9, uid = "a", name = "MASTER")), ::newId)

        assertEquals(mine, out[0])
        assertEquals(2, out.size)
    }

    /**
     * SessionLog.trainingId apunta al id local: cambiarlo en cada sincronizacion
     * desengancharia el historial del training.
     */
    @Test
    fun `an updated assignment keeps its local id`() {
        val current = assigned(id = 77, uid = "a", name = "MASTER")

        val out = mergeAssigned(listOf(current), listOf(Training(id = 5, uid = "a", name = "MASTER v2")), ::newId)

        assertEquals(77L, out.single().id)
        assertEquals("MASTER v2", out.single().name)
    }

    @Test
    fun `an assignment that disappears is removed`() {
        val current = assigned(id = 77, uid = "a", name = "MASTER")

        val out = mergeAssigned(listOf(current), emptyList(), ::newId)

        assertTrue(out.isEmpty())
    }

    /** Solo se retira lo que llego asignado; un training propio se queda pase lo que pase. */
    @Test
    fun `an empty assignment does not touch own trainings`() {
        val mine = own(1, "mio", "NIKO")

        assertEquals(listOf(mine), mergeAssigned(listOf(mine), emptyList(), ::newId))
    }

    @Test
    fun `new assignments go at the end, keeping the existing order`() {
        val mine = own(1, "mio", "NIKO")
        val current = assigned(2, "a", "MASTER")

        val out = mergeAssigned(
            listOf(mine, current),
            listOf(Training(id = 9, uid = "a", name = "MASTER"), Training(id = 8, uid = "b", name = "NUEVO")),
            ::newId,
        )

        assertEquals(listOf("NIKO", "MASTER", "NUEVO"), out.map { it.name })
    }

    @Test
    fun `an incoming training without uid is ignored`() {
        val out = mergeAssigned(emptyList(), listOf(Training(id = 1, uid = "", name = "SIN UID")), ::newId)

        assertTrue(out.isEmpty())
    }

    @Test
    fun `syncing twice with the same data changes nothing`() {
        val incoming = listOf(Training(id = 9, uid = "a", name = "MASTER"))

        val once = mergeAssigned(emptyList(), incoming, ::newId)
        val twice = mergeAssigned(once, incoming, ::newId)

        assertEquals(once, twice)
    }

    /** Un training propio con el mismo uid que uno asignado no se convierte en asignado. */
    @Test
    fun `an own training is not captured by an assignment with its uid`() {
        val mine = own(1, "a", "MIO")

        val out = mergeAssigned(listOf(mine), listOf(Training(id = 9, uid = "a", name = "ASIGNADO")), ::newId)

        assertEquals(mine, out[0])
        assertEquals("ASIGNADO", out[1].name)
        assertTrue(out[1].assigned)
    }
}
