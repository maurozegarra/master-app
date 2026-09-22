package com.maurozegarra.master.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Qué se esconde de la lista y qué sobrevive a una revisión (TD-138). */
class ArchiveTest {

    private val manana = Training(id = 1L, uid = "uid-lumbar", name = "LUMBAR")
    private val diaMalo = Training(id = 2L, uid = "uid-bad-day", name = "LUMBAR (bad day)")
    private val prueba = Training(id = 3L, uid = "uid-prueba", name = "On Your Marks")

    private val todos = listOf(manana, diaMalo, prueba)
    private val archivados = setOf("uid-bad-day", "uid-prueba")

    @Test
    fun `la lista ensena solo lo que no esta archivado`() {
        assertEquals(listOf("LUMBAR"), Archive.visible(todos, archivados).map { it.name })
        assertEquals(
            listOf("LUMBAR (bad day)", "On Your Marks"),
            Archive.archived(todos, archivados).map { it.name },
        )
    }

    @Test
    fun `lo archivado sigue archivado tras una revision de la rutina`() {
        // Subir LUMBAR_REVISION reemplaza el training entero -otro id, otro contenido- y solo
        // conserva el uid. Por eso la marca va por uid: si fuera por id, o viviera dentro del
        // training, el dia malo reapareceria en la lista con cada revision.
        val trasRevision = diaMalo.copy(id = 99L, name = "LUMBAR (bad day) v7")
        assertTrue(Archive.isArchived(trasRevision, archivados))
    }

    @Test
    fun `un training sin uid nunca se da por archivado`() {
        assertFalse(Archive.isArchived(Training(id = 4L, uid = "", name = "Sin uid"), setOf("")))
    }

    @Test
    fun `reordenar lo visible se traduce a la posicion real`() {
        // Se ven el 1 y el 4; el 2 y el 3 estan archivados en medio. Mover el segundo visible
        // al primero tiene que mover el indice 3, no el 1.
        val lista = listOf(
            manana,
            diaMalo,
            prueba,
            Training(id = 4L, uid = "uid-niko", name = "NIKO 2"),
        )
        assertEquals(listOf(0, 3), Archive.visibleIndices(lista, archivados))
    }

    @Test
    fun `ordenar lo archivado no mueve lo visible`() {
        // TD-150: el 21-sep pidio poder ordenar tambien los archivados. En la lista real van
        // intercalados con los visibles; mover uno archivado no puede empujar a ninguno de
        // los que se ven.
        val lista = listOf(
            Training(id = 1L, uid = "uid-lumbar", name = "LUMBAR"),
            Training(id = 2L, uid = "uid-bad-day", name = "LUMBAR (bad day)"),
            Training(id = 3L, uid = "uid-short", name = "LUMBAR (short)"),
            Training(id = 4L, uid = "uid-prueba", name = "On Your Marks"),
        )
        val archivados = setOf("uid-bad-day", "uid-prueba")
        val reales = Archive.archivedIndices(lista, archivados)
        assertEquals(listOf(1, 3), reales)

        // El segundo archivado pasa a primero.
        val movida = Archive.move(lista, reales[1], reales[0])

        assertEquals(listOf("On Your Marks", "LUMBAR (bad day)"), Archive.archived(movida, archivados).map { it.name })
        assertEquals(listOf("LUMBAR", "LUMBAR (short)"), Archive.visible(movida, archivados).map { it.name })
    }
}
