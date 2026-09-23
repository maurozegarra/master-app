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
}
