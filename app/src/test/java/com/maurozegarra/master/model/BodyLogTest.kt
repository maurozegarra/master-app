package com.maurozegarra.master.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** Los pesajes de los sábados (TD-169). */
class BodyLogTest {

    private val sab = BodyEntry("2026-10-03", weightKg = 84.6, waistCm = 94.0, skeletalKg = 33.5, heightCm = 181.0)

    @Test
    fun `cintura entre estatura con dos decimales, como la serie de los documentos`() {
        assertEquals(0.52, BodyLog.waistToHeight(sab, 181.0)!!, 0.0)
        assertEquals(0.44, BodyLog.waistToHeight(BodyEntry("2026-10-03", waistCm = 67.5), 155.0)!!, 0.0)
        // Sin cinta o sin estatura no hay indice: no se inventa.
        assertNull(BodyLog.waistToHeight(BodyEntry("2026-09-12", weightKg = 83.5), 181.0))
        assertNull(BodyLog.waistToHeight(sab, null))
    }

    @Test
    fun `la estatura es la ultima anotada`() {
        val lista = listOf(BodyEntry("2026-09-12", heightCm = 180.0), BodyEntry("2026-09-19"), BodyEntry("2026-09-26", heightCm = 181.0), BodyEntry("2026-10-03"))
        assertEquals(181.0, BodyLog.heightOf(lista)!!, 0.0)
    }

    @Test
    fun `anotar otra vez el mismo dia corrige, no duplica`() {
        val lista = BodyLog.upsert(listOf(sab), sab.copy(waistCm = 93.5))
        assertEquals(1, lista.size)
        assertEquals(93.5, lista.single().waistCm!!, 0.0)
    }

    @Test
    fun `lo que baja manda por dia, y lo sembrado que no esta en el servidor se queda`() {
        val sembrados = listOf(
            AthleteBody("niko", BodyEntry("2026-09-26", weightKg = 49.5)),
            AthleteBody("niko", BodyEntry("2026-10-03", weightKg = 49.7)),
        )
        val bajados = listOf(AthleteBody("niko", BodyEntry("2026-10-03", weightKg = 49.8)), AthleteBody("niko", BodyEntry("2026-10-10", weightKg = 50.0)))
        val unidos = BodyLog.of(BodyLog.merge(sembrados, bajados), "niko")
        assertEquals(listOf("2026-09-26", "2026-10-03", "2026-10-10"), unidos.map { it.date })
        assertEquals(49.8, unidos[1].weightKg!!, 0.0)
    }

    @Test
    fun `el aviso sale el sabado desde las 8, sin pesaje y una sola vez`() {
        val sabado8 = LocalDateTime.of(2026, 10, 10, 8, 5)
        assertTrue(BodyLog.needsReminder(listOf(sab), sabado8, remindedOn = null))
        // Antes de las 8 no: se esta durmiendo.
        assertFalse(BodyLog.needsReminder(emptyList(), sabado8.withHour(7), null))
        // Con el pesaje de hoy ya anotado, no.
        assertFalse(BodyLog.needsReminder(listOf(BodyEntry("2026-10-10", weightKg = 84.0)), sabado8, null))
        // Ya recordado hoy, no otra vez a los quince minutos.
        assertFalse(BodyLog.needsReminder(emptyList(), sabado8.plusMinutes(15), "2026-10-10"))
        // Otro dia de la semana, nunca.
        assertFalse(BodyLog.needsReminder(emptyList(), sabado8.plusDays(1), null))
    }

    @Test
    fun `sube lo que cambio desde la ultima subida`() {
        val otro = BodyEntry("2026-09-26", weightKg = 84.7)
        val ledger = mapOf(otro.date to BodyLog.payloadOf(otro).hashCode())
        assertEquals(listOf(sab), BodyLog.pending(listOf(otro, sab), ledger))
        // Corregir uno ya subido lo vuelve a subir.
        assertEquals(1, BodyLog.pending(listOf(otro.copy(weightKg = 84.8)), ledger).size)
        assertEquals(setOf("2026-09-26"), BodyLog.toDelete(listOf("2026-09-26", "2026-10-03"), ledger))
    }

    @Test
    fun `ida y vuelta por JSON, propios y de atletas`() {
        val lista = listOf(BodyEntry("2026-09-12", weightKg = 83.5, heightCm = 181.0), sab)
        assertEquals(lista, BodyLog.decode(BodyLog.encode(lista)))
        val atletas = listOf(AthleteBody("niko", BodyEntry("2026-10-03", 49.7, 67.5, 18.3, 155.0)))
        assertEquals(atletas, BodyLog.decodeAthletes(BodyLog.encodeAthletes(atletas)))
        // Una fecha rota no entra.
        assertEquals(emptyList<BodyEntry>(), BodyLog.decode("""[{"date":"ayer","weightKg":80}]"""))
    }

    @Test
    fun `las filas de PostgREST se leen con su perfil`() {
        val rows = """[{"profile_id":"niko","payload":{"date":"2026-10-10","weightKg":50.0,"waistCm":67}}]"""
        val leidas = BodyLog.parseRows(rows)!!
        assertEquals("niko", leidas.single().profileId)
        assertEquals(67.0, leidas.single().entry.waistCm!!, 0.0)
    }

    @Test
    fun `los pesajes viajan en el respaldo, y uno viejo sin ellos se sigue importando`() {
        val data = BackupData(
            trainings = emptyList(), customExercises = emptyList(), sessions = emptyList(),
            body = BodyLog.encode(listOf(sab)),
            athleteBody = BodyLog.encodeAthletes(listOf(AthleteBody("niko", BodyEntry("2026-10-03", weightKg = 49.7)))),
        )
        val back = BackupJson.decode(BackupJson.encode(data, 0L))!!
        assertEquals(listOf(sab), BodyLog.decode(back.body))
        assertEquals("niko", BodyLog.decodeAthletes(back.athleteBody).single().profileId)
        val viejo = BackupJson.decode("""{"format":4,"trainings":[]}""")!!
        assertEquals("[]", viejo.body)
    }

    @Test
    fun `la serie sembrada es la de los documentos`() {
        val mia = BodyLog.SEED.getValue("mauro")
        assertEquals(listOf(83.5, 84.4, 84.7, 84.6), mia.map { it.weightKg })
        assertEquals(0.52, BodyLog.waistToHeight(mia.last(), BodyLog.heightOf(mia))!!, 0.0)
        val niko = BodyLog.SEED.getValue("niko")
        assertEquals(listOf(18.0, 18.4, 18.2, 18.3), niko.map { it.skeletalKg })
    }
}
