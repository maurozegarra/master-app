package com.maurozegarra.master.morning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class HolidaysTest {

    private val lima = ZoneId.of("America/Lima")

    @Test
    fun `la pascua y la semana santa salen del calendario`() {
        assertEquals(LocalDate.of(2026, 4, 5), Holidays.easter(2026))
        assertEquals(LocalDate.of(2027, 3, 28), Holidays.easter(2027))
        val p = Holidays.peru(2026)
        assertEquals("Holy Thursday", p[LocalDate.of(2026, 4, 2)])
        assertEquals("Good Friday", p[LocalDate.of(2026, 4, 3)])
        assertEquals("Battle of Angamos", p[LocalDate.of(2026, 10, 8)])
        assertEquals(16, p.size)
    }

    @Test
    fun `el jueves 8 feriado suena a las 7 como un sabado, no a las 5`() {
        // Lo que lo pidio: el jueves es presencial (5:00), pero el 8-oct es feriado.
        val horario = MorningSchedule.default(true).copy(holidays = Holidays.of(2026, emptySet(), emptySet()))
        val miercolesNoche = ZonedDateTime.of(2026, 10, 7, 22, 0, 0, 0, lima)
        assertEquals(ZonedDateTime.of(2026, 10, 8, 7, 0, 0, 0, lima), horario.next(miercolesNoche))
        // El jueves siguiente, sin feriado, vuelve a las 5.
        val miercoles14 = ZonedDateTime.of(2026, 10, 14, 22, 0, 0, 0, lima)
        assertEquals(ZonedDateTime.of(2026, 10, 15, 5, 0, 0, 0, lima), horario.next(miercoles14))
    }

    @Test
    fun `quitar un feriado nacional y agregar uno suyo`() {
        val dia = LocalDate.of(2026, 10, 8)
        val propio = LocalDate.of(2026, 10, 9)
        val vigentes = Holidays.of(2026, added = setOf(propio), removed = setOf(dia))
        assertFalse(dia in vigentes)
        assertTrue(propio in vigentes)
        // Se ven los del año siguiente: en diciembre ya esta el 1 de enero.
        assertTrue(LocalDate.of(2027, 1, 1) in vigentes)
    }
}
