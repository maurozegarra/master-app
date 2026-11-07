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
    // ---------- TD-207: el pesaje anterior, al anotar ----------

    @Test
    fun `el anterior es el ultimo con ese dato antes de la fecha`() {
        val serie = listOf(
            BodyEntry("2026-10-17", weightKg = 84.0, waistCm = 94.0),
            BodyEntry("2026-10-24", weightKg = 83.6),
            BodyEntry("2026-10-31", weightKg = 83.2, waistCm = 93.0),
            BodyEntry("2026-11-07", weightKg = 83.5, waistCm = 93.5),
        )
        // Anotando hoy: contra la semana pasada.
        assertEquals("2026-10-31" to 83.2, BodyLog.previous(serie, "2026-11-07") { it.weightKg })
        // Corrigiendo uno viejo: contra el suyo anterior, no contra el ultimo ni consigo mismo.
        assertEquals("2026-10-24" to 83.6, BodyLog.previous(serie, "2026-10-31") { it.weightKg })
        // Sin cinta el 24: salta a la anterior que si la tiene.
        assertEquals("2026-10-17" to 94.0, BodyLog.previous(serie, "2026-10-31") { it.waistCm })
        // El primero no tiene con que compararse.
        assertNull(BodyLog.previous(serie, "2026-10-17") { it.weightKg })
    }

    @Test
    fun `la diferencia lleva un decimal, y sin cambio no hay nada que enseñar`() {
        // Como la balanza de Xiaomi.
        assertEquals(0.3, BodyLog.delta(83.5, 83.2)!!, 0.0)
        assertEquals(-0.5, BodyLog.delta(93.0, 93.5)!!, 0.0)
        assertEquals(1.0, BodyLog.delta(84.0, 83.0)!!, 0.0)
        assertNull(BodyLog.delta(83.2, 83.2))
        // Lo que se pierde al redondear a un decimal tampoco es cambio.
        assertNull(BodyLog.delta(83.24, 83.2))
    }

    // ---------- TD-205: el coach anota por el atleta, y el atleta lo ve ----------

    private fun huella(vararg es: BodyEntry) = es.associate { it.date to BodyLog.payloadOf(it).hashCode() }

    @Test
    fun `por dia manda el servidor, y lo que el coach corrigio llega al atleta`() {
        val subido = BodyEntry("2026-10-17", weightKg = 50.0)
        val corregido = BodyEntry("2026-10-17", weightKg = 50.2)
        val r = BodyLog.mergeOwn(listOf(subido), listOf(corregido), huella(subido), emptySet())
        assertEquals(listOf(corregido), r.entries)
        // Entra al ledger con su huella: no se vuelve a subir.
        assertTrue(BodyLog.pending(r.entries, r.ledger).isEmpty())
    }

    @Test
    fun `un cambio de aqui que no subio no lo pisa el servidor`() {
        val subido = BodyEntry("2026-10-17", weightKg = 50.0)
        val mio = BodyEntry("2026-10-17", weightKg = 49.8)
        val r = BodyLog.mergeOwn(listOf(mio), listOf(subido), huella(subido), emptySet())
        assertEquals(listOf(mio), r.entries)
        assertEquals(listOf(mio), BodyLog.pending(r.entries, r.ledger))
    }

    @Test
    fun `lo que el coach anoto aparece, y lo que borro se va`() {
        val subido = BodyEntry("2026-10-17", weightKg = 50.0)
        val delCoach = BodyEntry("2026-10-31", weightKg = 50.2, waistCm = 67.0, skeletalKg = 18.5)
        val r = BodyLog.mergeOwn(listOf(subido), listOf(delCoach), huella(subido), emptySet())
        // El del 17 ya habia subido y no esta alla: lo borro el coach.
        assertEquals(listOf(delCoach), r.entries)
        assertEquals(huella(delCoach), r.ledger)
    }

    @Test
    fun `uno nuevo que nunca subio se queda aunque el servidor no lo tenga`() {
        val nuevo = BodyEntry("2026-10-31", weightKg = 50.2)
        val r = BodyLog.mergeOwn(listOf(nuevo), emptyList(), emptyMap(), emptySet())
        assertEquals(listOf(nuevo), r.entries)
    }

    @Test
    fun `un borrado de aqui en cola no vuelve desde el servidor`() {
        val viejo = BodyEntry("2026-10-17", weightKg = 50.0)
        val r = BodyLog.mergeOwn(emptyList(), listOf(viejo), emptyMap(), setOf("2026-10-17"))
        assertTrue(r.entries.isEmpty())
    }

    @Test
    fun `el coach no pierde lo que anoto y no subio al bajar`() {
        val antes = AthleteBody("niko", BodyEntry("2026-10-31", weightKg = 50.0))
        val mio = AthleteBody("niko", BodyEntry("2026-10-31", weightKg = 50.2))
        val otro = AthleteBody("niko", BodyEntry("2026-10-24", weightKg = 49.9))
        val pendiente = setOf(BodyLog.key("niko", "2026-10-31"))
        assertEquals(listOf(otro, mio), BodyLog.mergeKeepingPending(listOf(mio), listOf(antes, otro), pendiente))
        // Sin pendiente, manda lo que bajo, como siempre.
        assertEquals(listOf(otro, antes), BodyLog.mergeKeepingPending(listOf(mio), listOf(antes, otro), emptySet()))
    }

    @Test
    fun `la llave guarda perfil y dia`() {
        val k = BodyLog.key("niko", "2026-10-31")
        assertEquals("niko", BodyLog.profileOfKey(k))
        assertEquals("2026-10-31", BodyLog.dateOfKey(k))
    }

    @Test
    fun `filas de my_body`() {
        val rows = """[{"date":"2026-10-31","payload":{"date":"2026-10-31","weightKg":50.2,"waistCm":67}}]"""
        assertEquals(listOf(BodyEntry("2026-10-31", weightKg = 50.2, waistCm = 67.0)), BodyLog.parseOwnRows(rows))
        assertNull(BodyLog.parseOwnRows("""{"message":"no existe"}"""))
    }
}
