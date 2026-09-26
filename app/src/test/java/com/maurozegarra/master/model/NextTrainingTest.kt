package com.maurozegarra.master.model

import com.maurozegarra.master.data.MasterDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** El training que sigue (TD-167). */
class NextTrainingTest {

    private val lima = ZoneId.of("America/Lima")
    private val corto = Training(id = 1L, name = "LUMBAR (short)", scheduleDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.SUNDAY))
    private val completo = Training(id = 2L, name = "LUMBAR", scheduleDays = setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY))
    private val diaMalo = Training(id = 3L, name = "LUMBAR (bad day)")

    private fun sesion(trainingId: Long, dia: LocalDate, status: SessionStatus = SessionStatus.COMPLETED) =
        SessionLog(id = dia.toEpochDay(), trainingId = trainingId, trainingName = "x",
            completedAt = ZonedDateTime.of(dia.atTime(8, 0), lima).toInstant().toEpochMilli(), status = status)

    private val viernes = LocalDate.of(2026, 9, 25)

    @Test
    fun `sin entrenar todavia, el de hoy`() {
        // Viernes a las 5 de la mañana: arriba el completo.
        assertEquals(2L, NextTraining.of(listOf(corto, completo, diaMalo), emptyList(), viernes, lima))
    }

    @Test
    fun `ya entrenado hoy, el de manana`() {
        // Viernes en la noche, con la sesion hecha: el sabado tambien es el completo.
        assertEquals(2L, NextTraining.of(listOf(corto, completo), listOf(sesion(2L, viernes)), viernes, lima))
        // Sabado hecho: el domingo es el corto.
        val sabado = viernes.plusDays(1)
        assertEquals(1L, NextTraining.of(listOf(corto, completo), listOf(sesion(2L, sabado)), sabado, lima))
    }

    @Test
    fun `una sesion a medias no cuenta como entrenado`() {
        assertEquals(2L, NextTraining.of(listOf(corto, completo), listOf(sesion(2L, viernes, SessionStatus.PARTIAL)), viernes, lima))
    }

    @Test
    fun `el dia malo nunca va primero`() {
        assertNull(NextTraining.of(listOf(diaMalo), emptyList(), viernes, lima))
    }

    @Test
    fun `NIKO va por numero, despues del ultimo que completo`() {
        val niko = (1..6).map { Training(id = 100L + it, name = "NIKO $it", cycleDay = it) }
        assertEquals(101L, NextTraining.of(niko, emptyList(), viernes, lima))
        assertEquals(106L, NextTraining.of(niko, listOf(sesion(105L, viernes)), viernes, lima))
        // Despues del ultimo vuelve al primero.
        assertEquals(101L, NextTraining.of(niko, listOf(sesion(106L, viernes)), viernes, lima))
        // Si todavia no tiene asignado el que toca, el siguiente de los que hay.
        val sinSeis = niko.filter { it.cycleDay != 6 }
        assertEquals(101L, NextTraining.of(sinSeis, listOf(sesion(105L, viernes)), viernes, lima))
    }

    @Test
    fun `en el telefono del coach manda lo suyo`() {
        val niko = Training(id = 101L, name = "NIKO 1", cycleDay = 1)
        assertEquals(2L, NextTraining.of(listOf(niko, corto, completo), emptyList(), viernes, lima))
    }

    @Test
    fun `las rutinas del codigo traen su programa`() {
        assertEquals(setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY), MasterDefaults.lumbarTraining("en").scheduleDays)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.SUNDAY), MasterDefaults.lumbarShortTraining("en").scheduleDays)
        assertEquals(emptySet<DayOfWeek>(), MasterDefaults.lumbarBadDayTraining("en").scheduleDays)
        assertEquals((1..6).toList(), MasterDefaults.nikoTrainings("es").map { it.cycleDay })
    }

    @Test
    fun `el programa viaja con el training`() {
        val t = completo.copy(cycleDay = 3)
        val vuelta = TrainingJson.decode(TrainingJson.encode(listOf(t))).single()
        assertEquals(t.scheduleDays, vuelta.scheduleDays)
        assertEquals(3, vuelta.cycleDay)
    }
}
