package com.maurozegarra.master.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Que sube y como vuelve (TD-126). */
class SessionSyncTest {

    private val asignado = Training(id = 10L, uid = "uid-niko-2", name = "NIKO 2 · Muay Thai", assigned = true)
    private val propio = Training(id = 20L, uid = "uid-propio", name = "Lo suyo", assigned = false)

    private fun sesion(id: Long, trainingId: Long, painAfter: Int? = null) = SessionLog(
        id = id, trainingId = trainingId, trainingName = "x", completedAt = 1_000L, painAfter = painAfter,
    )

    @Test
    fun `solo suben las sesiones de trainings asignados`() {
        // Decision del usuario: lo que un atleta entrena por su cuenta no es asunto del coach.
        val pendientes = SessionSync.pending(
            sessions = listOf(sesion(1, 10), sesion(2, 20), sesion(3, 99)),
            trainings = listOf(asignado, propio),
            ledger = emptyMap(),
        )
        assertEquals(listOf(1L), pendientes.map { it.session.id })
        assertEquals("uid-niko-2", pendientes.single().trainingUid)
    }

    @Test
    fun `lo ya subido no vuelve a subir, salvo que cambie`() {
        val s = sesion(1, 10)
        val primera = SessionSync.pending(listOf(s), listOf(asignado), emptyMap()).single()
        val ledger = mapOf(1L to primera.fingerprint)
        assertTrue(SessionSync.pending(listOf(s), listOf(asignado), ledger).isEmpty())

        // Contesta el dolor despues de guardar: la sesion cambio y tiene que volver a subir.
        val completada = s.copy(painAfter = 1)
        assertEquals(1, SessionSync.pending(listOf(completada), listOf(asignado), ledger).size)
    }

    @Test
    fun `borrar en el telefono borra en el servidor lo que subio, y solo eso`() {
        // TD-149. El 21-sep el coach le hizo a NIKO una demostracion en su telefono y la
        // borro, pero ya habia subido: el asistente la leyo como entrenamiento de ella. Lo
        // que nunca subio -un training suyo, no asignado- no tiene nada que borrar alla.
        val subida = SessionSync.pending(listOf(sesion(1, 10)), listOf(asignado), emptyMap()).single()
        val ledger = mapOf(1L to subida.fingerprint)

        assertEquals(setOf(1L), SessionSync.toDelete(listOf(1L, 2L), ledger))
        assertTrue(SessionSync.toDelete(listOf(2L), ledger).isEmpty())
    }

    @Test
    fun `una sesion borrada que vuelve con un respaldo sube otra vez`() {
        // Borrar la saca del ledger (WorkoutStore.queueSessionDeletes): sin huella, la
        // sesion reimportada vuelve a contar como pendiente y no se queda solo en el telefono.
        val s = sesion(1, 10)
        val sinHuella = emptyMap<Long, Int>()
        assertEquals(listOf(1L), SessionSync.pending(listOf(s), listOf(asignado), sinHuella).map { it.session.id })
    }

    @Test
    fun `las filas del servidor se leen como sesiones de cada atleta`() {
        val payload = SessionSync.payloadOf(sesion(7, 10, painAfter = 2))
        val filas = """[{"profile_id":"niko","payload":$payload}]"""
        val leidas = SessionSync.parseRows(filas)!!
        assertEquals("niko", leidas.single().profileId)
        assertEquals(7L, leidas.single().session.id)
        assertEquals(2, leidas.single().session.painAfter)
    }

    @Test
    fun `una respuesta rota no se confunde con una lista vacia`() {
        assertNull(SessionSync.parseRows("esto no es json"))
    }

    @Test
    fun `las sesiones de los atletas sobreviven al respaldo`() {
        val atletas = listOf(AthleteSession("niko", sesion(7, 10, painAfter = 2)))
        val data = BackupData(trainings = emptyList(), customExercises = emptyList(), sessions = emptyList(), athleteSessions = atletas)
        val vuelta = BackupJson.decode(BackupJson.encode(data, exportedAt = 0L))!!
        assertEquals(atletas, vuelta.athleteSessions)
    }

    @Test
    fun `un respaldo de antes del formato 3 se lee sin sesiones de atletas`() {
        val viejo = """{"format":2,"trainings":[],"customExercises":[],"sessions":[]}"""
        assertEquals(emptyList<AthleteSession>(), BackupJson.decode(viejo)!!.athleteSessions)
    }
}
