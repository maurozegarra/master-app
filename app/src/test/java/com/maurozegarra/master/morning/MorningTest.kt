package com.maurozegarra.master.morning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** La alarma de la mañana: cuándo suena y qué guarda (TD-151). */
class MorningTest {

    private val lima = ZoneId.of("America/Lima")

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int) = ZonedDateTime.of(y, m, d, h, min, 0, 0, lima)

    @Test
    fun `por defecto, 5 los dias presenciales y 7 los demas`() {
        // Lo que dijo el usuario el 22-sep: lunes, miercoles y jueves a las 5.
        val s = MorningSchedule.DEFAULT
        assertEquals(LocalTime.of(5, 0), s.at(DayOfWeek.MONDAY))
        assertEquals(LocalTime.of(7, 0), s.at(DayOfWeek.TUESDAY))
        assertEquals(LocalTime.of(5, 0), s.at(DayOfWeek.WEDNESDAY))
        assertEquals(LocalTime.of(5, 0), s.at(DayOfWeek.THURSDAY))
        assertEquals(LocalTime.of(7, 0), s.at(DayOfWeek.SUNDAY))
    }

    @Test
    fun `la proxima es la de manana si la de hoy ya paso`() {
        // Martes 22-sep a las 20:36 -> miercoles 23 a las 5.
        assertEquals(at(2026, 9, 23, 5, 0), MorningSchedule.DEFAULT.next(at(2026, 9, 22, 20, 36)))
        // Martes a las 6:59 -> hoy a las 7.
        assertEquals(at(2026, 9, 22, 7, 0), MorningSchedule.DEFAULT.next(at(2026, 9, 22, 6, 59)))
    }

    @Test
    fun `justo a la hora no vuelve a sonar hoy`() {
        // Al sonar se reprograma: si "a las 7" contara, sonaria en bucle.
        assertEquals(at(2026, 9, 23, 5, 0), MorningSchedule.DEFAULT.next(at(2026, 9, 22, 7, 0)))
    }

    @Test
    fun `un dia apagado se salta`() {
        val sinMiercoles = MorningSchedule.DEFAULT.with(DayOfWeek.WEDNESDAY, null)
        assertEquals(at(2026, 9, 24, 5, 0), sinMiercoles.next(at(2026, 9, 22, 20, 0)))
    }

    @Test
    fun `sin ningun dia no hay proxima`() {
        val nada = MorningSchedule(DayOfWeek.entries.associateWith { null })
        assertNull(nada.next(at(2026, 9, 22, 20, 0)))
    }

    @Test
    fun `los minutos hasta aflojar los calcula el app`() {
        val e = MorningEntry("2026-09-23", painOnWaking = 2, answeredAt = 0L, easedAt = 12 * 60_000L + 20_000L)
        assertEquals(12, e.fadeMinutes)
        assertNull(e.copy(easedAt = null).fadeMinutes)
    }

    @Test
    fun `una manana por dia, y la de la sesion es la de su dia`() {
        val hoy = LocalDate.of(2026, 9, 23)
        var log = MorningLog.upsert(emptyList(), MorningEntry("2026-09-23", painOnWaking = 3), hoy)
        log = MorningLog.upsert(log, MorningEntry("2026-09-23", painOnWaking = 2), hoy)
        assertEquals(listOf(2), log.map { it.painOnWaking })

        val sesion = at(2026, 9, 23, 5, 40).toInstant().toEpochMilli()
        assertEquals(2, MorningLog.forDay(log, sesion, lima)?.painOnWaking)
        assertNull(MorningLog.forDay(log, at(2026, 9, 24, 5, 40).toInstant().toEpochMilli(), lima))
    }

    @Test
    fun `lo muy viejo se poda`() {
        val hoy = LocalDate.of(2026, 9, 23)
        val log = MorningLog.upsert(listOf(MorningEntry("2025-01-01", 4)), MorningEntry("2026-09-23", 2), hoy)
        assertEquals(listOf("2026-09-23"), log.map { it.date })
    }

    @Test
    fun `las mananas van y vuelven en el respaldo`() {
        // Es por donde el coach las lee, igual que las sesiones.
        val log = listOf(MorningEntry("2026-09-23", painOnWaking = 2, answeredAt = 1L, easedAt = 600_001L))
        val json = MorningStore.encode(log)
        assertEquals(log, MorningStore.decode(json))

        val data = com.maurozegarra.master.model.BackupData(
            trainings = emptyList(), customExercises = emptyList(), sessions = emptyList(), morning = json,
        )
        val vuelta = com.maurozegarra.master.model.BackupJson.decode(com.maurozegarra.master.model.BackupJson.encode(data, exportedAt = 0L))!!
        assertEquals(log, MorningStore.decode(vuelta.morning))
    }

    @Test
    fun `saltar manana no toca el horario`() {
        // Martes a las 20:00 con el miercoles saltado: la siguiente es el jueves a las 5.
        val martes = at(2026, 9, 22, 20, 0)
        assertEquals(at(2026, 9, 24, 5, 0), MorningSchedule.DEFAULT.next(martes, skip = LocalDate.of(2026, 9, 23)))
        assertEquals(at(2026, 9, 23, 5, 0), MorningSchedule.DEFAULT.next(martes))
    }

    @Test
    fun `una respuesta de noche no es una manana`() {
        // La prueba de la alarma del 22-sep a las 21:42 decia "dolor 0" en una manana que fue 2.
        val prueba = MorningEntry("2026-09-22", painOnWaking = 0, answeredAt = at(2026, 9, 22, 21, 42).toInstant().toEpochMilli())
        val real = MorningEntry("2026-09-23", painOnWaking = 1, answeredAt = at(2026, 9, 23, 5, 0).toInstant().toEpochMilli())
        assertEquals(listOf(real), MorningLog.mornings(listOf(prueba, real), lima))
        assertEquals(mapOf(LocalDate.of(2026, 9, 23) to 1), MorningLog.painByDate(listOf(prueba, real), lima))
    }

    @Test
    fun `una prueba de noche no pisa la manana de verdad`() {
        // El 24-sep a las 23:00, probando la alarma, se perdio la manana real de ese dia.
        val real = MorningEntry("2026-09-24", painOnWaking = 1, answeredAt = at(2026, 9, 24, 5, 0).toInstant().toEpochMilli())
        assertEquals(false, MorningLog.mayRecord(real, at(2026, 9, 24, 23, 0).toInstant().toEpochMilli(), lima))
        assertEquals(true, MorningLog.mayRecord(real, at(2026, 9, 24, 5, 3).toInstant().toEpochMilli(), lima))
        assertEquals(true, MorningLog.mayRecord(null, at(2026, 9, 24, 23, 0).toInstant().toEpochMilli(), lima))
    }

    @Test
    fun `los dias sin alarma salen de las sesiones, y lo real no se toca`() {
        val sesion = { d: Int, dolor: Int?, min: Int? -> Triple(at(2026, 9, d, 7, 0).toInstant().toEpochMilli(), dolor, min) }
        val real = MorningEntry("2026-09-23", painOnWaking = 1, answeredAt = at(2026, 9, 23, 5, 0).toInstant().toEpochMilli(), easedAt = at(2026, 9, 23, 5, 15).toInstant().toEpochMilli())
        val prueba = MorningEntry("2026-09-24", painOnWaking = 0, answeredAt = at(2026, 9, 24, 23, 0).toInstant().toEpochMilli())
        val log = MorningLog.fromSessions(
            listOf(real, prueba),
            listOf(sesion(20, 2, 15), sesion(23, 3, 40), sesion(24, 1, 24), sesion(25, null, null)),
            lima,
        )
        // El 20 aparece, el 23 sigue siendo el de la alarma, y el 24 vuelve de la sesion.
        assertEquals(listOf("2026-09-20", "2026-09-23", "2026-09-24"), log.map { it.date })
        assertEquals(listOf(2, 1, 1), log.map { it.painOnWaking })
        assertEquals(listOf(15, 15, 24), log.map { it.fadeMinutes })
    }
}
