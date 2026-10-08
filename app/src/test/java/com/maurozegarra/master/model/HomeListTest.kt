package com.maurozegarra.master.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class HomeListTest {

    private val lima = ZoneId.of("America/Lima")
    private val jueves = LocalDate.of(2026, 10, 8)
    private fun at(d: LocalDate, h: Int) = d.atTime(h, 0).atZone(lima).toInstant().toEpochMilli()

    private val corto = Training(id = 1L, uid = "c", name = "LUMBAR (short)", scheduleDays = setOf(DayOfWeek.FRIDAY))
    private val completo = Training(id = 2L, uid = "l", name = "LUMBAR", scheduleDays = setOf(DayOfWeek.THURSDAY))
    private val malo = Training(id = 3L, uid = "b", name = "LUMBAR (bad day)")
    private val niko = (1..6).map { Training(id = 100L + it, uid = "n$it", name = "NIKO $it", cycleDay = it) }
    private val viejo = Training(id = 44L, uid = "b9d84943-364f-4da8-92d0-a93f5b555ed0", name = "MASTER")
    private val todos = listOf(completo, corto, malo) + niko + viejo

    private fun hecha(tr: String, cuando: Long) =
        SessionLog(id = cuando, trainingId = 999L, trainingName = tr, completedAt = cuando, status = SessionStatus.COMPLETED)

    @Test
    fun `arriba solo el que sigue, y ya entreno el de hoy`() {
        // El jueves feriado hizo el completo: arriba va el corto del viernes, solo.
        val hoy = listOf(hecha("LUMBAR", at(jueves, 9)).copy(trainingId = 2L))
        assertEquals(corto, HomeList.main(todos, hoy, jueves, lima))
    }

    @Test
    fun `abajo, primero el de NIKO de hoy, despues el resto de NIKO y los demas`() {
        // El miercoles hizo NIKO 3: el jueves le toca NIKO 4.
        val suyas = listOf(hecha("NIKO 3", at(jueves.minusDays(1), 9)))
        val abajo = HomeList.rest(todos, emptyList(), suyas, jueves, lima)
        assertEquals("NIKO 4", abajo.first().name)
        assertEquals(listOf("NIKO 1", "NIKO 2", "NIKO 3", "NIKO 5", "NIKO 6"), abajo.drop(1).take(5).map { it.name })
        // Si ya entreno hoy, el de hoy es el que hizo.
        val hoy = suyas + hecha("NIKO 4", at(jueves, 9))
        assertEquals("NIKO 4", HomeList.rest(todos, emptyList(), hoy, jueves, lima).first().name)
    }

    @Test
    fun `los viejos no se ven en ningun lado`() {
        val abajo = HomeList.rest(todos, emptyList(), emptyList(), jueves, lima)
        assertFalse(abajo.any { it.name == "MASTER" })
        assertFalse(HomeList.shown(todos).any { it.name == "MASTER" })
    }
}
