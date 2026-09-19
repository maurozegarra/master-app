package com.maurozegarra.master.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Qué se vuelve a publicar solo (TD-132). */
class PublishSyncTest {

    private val repartido = Training(id = 1L, uid = "uid-niko-2", name = "NIKO 2 · Muay Thai")
    private val propio = Training(id = 2L, uid = "uid-propio", name = "LUMBAR")
    private val recibido = Training(id = 3L, uid = "uid-ajeno", name = "Lo que le mandaron", assigned = true)

    @Test
    fun `un training repartido que cambia se vuelve a publicar`() {
        val ledger = mapOf("uid-niko-2" to PublishSync.fingerprintOf(repartido))
        assertTrue(PublishSync.toRepublish(listOf(repartido), ledger, emptySet()).isEmpty())

        val cambiado = repartido.copy(name = "NIKO 2 · Muay Thai y core")
        val pendiente = PublishSync.toRepublish(listOf(cambiado), ledger, emptySet()).single()
        assertEquals("uid-niko-2", pendiente.first.uid)
        assertEquals(PublishSync.fingerprintOf(cambiado), pendiente.second)
    }

    @Test
    fun `lo que alguien ya tiene pero nunca dejo huella entra igual`() {
        // NIKO 1 y NIKO 2 se asignaron antes de que existiera el ledger. Sin esto, un cambio
        // suyo no viajaria hasta reasignarlos a mano.
        val pendientes = PublishSync.toRepublish(listOf(repartido), emptyMap(), setOf("uid-niko-2"))
        assertEquals(listOf("uid-niko-2"), pendientes.map { it.first.uid })
    }

    @Test
    fun `un training propio que nadie tiene no viaja`() {
        assertTrue(PublishSync.toRepublish(listOf(propio), emptyMap(), emptySet()).isEmpty())
    }

    @Test
    fun `lo que este telefono recibio no se republica`() {
        // Seria devolverle al dueno una copia de lo suyo.
        assertTrue(PublishSync.toRepublish(listOf(recibido), emptyMap(), setOf("uid-ajeno")).isEmpty())
    }

    @Test
    fun `los uid repartidos se leen de las filas del servidor`() {
        val filas = """[{"training_uid":"a"},{"training_uid":"b"},{"training_uid":"a"}]"""
        assertEquals(setOf("a", "b"), AssignmentRowsJson.trainingUids(filas))
        assertNull(AssignmentRowsJson.trainingUids("no es json"))
    }
}
