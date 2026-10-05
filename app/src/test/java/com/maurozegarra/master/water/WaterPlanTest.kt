package com.maurozegarra.master.water

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** El agua del día (TD-190): la regla de los recordatorios, con sus casos. */
class WaterPlanTest {

    private val lima = ZoneId.of("America/Lima")
    private val dom = LocalDate.of(2026, 10, 4)
    private fun at(h: Int, m: Int, day: LocalDate = dom) = day.atTime(h, m).atZone(lima).toInstant().toEpochMilli()
    private fun hm(millis: Long) = java.time.Instant.ofEpochMilli(millis).atZone(lima).toLocalTime().toString()

    // Domingo en casa: corta a las 20:00 (se acuesta a las 22:00).
    private val cut = WaterPlan.cut(at(22, 0), dom, lima)
    private val casa = WaterPlan.slotsOn(WaterPlan.HOME, dom, lima)
    private val lun = LocalDate.of(2026, 10, 5)
    private val oficina = WaterPlan.slotsOn(WaterPlan.OFFICE, lun, lima)

    @Test
    fun `corta dos horas antes de acostarse, y sin alarma a las 19`() {
        assertEquals("20:00", hm(cut))
        assertEquals("19:00", hm(WaterPlan.cut(null, dom, lima)))
    }

    @Test
    fun `los dos horarios suman los 2 litros, y la alarma antes de las 6 dice cual`() {
        assertEquals(2000, WaterPlan.HOME.sumOf { it.ml })
        assertEquals(2000, WaterPlan.OFFICE.sumOf { it.ml })
        assertEquals(1200, WaterPlan.expected(at(12, 0), casa))
        assertEquals(WaterPlan.OFFICE, WaterPlan.planFor(java.time.LocalTime.of(5, 0)))
        assertEquals(WaterPlan.HOME, WaterPlan.planFor(java.time.LocalTime.of(7, 0)))
        assertEquals(WaterPlan.HOME, WaterPlan.planFor(null))
    }

    @Test
    fun `despierta a las 9 50, ya va en deficit, avisa cada media hora`() {
        // El horario pedia 7, 8 y 9: tomo uno a las 9:50 y le faltan dos.
        val start = at(9, 50)
        val logs = listOf(WaterLog(at(9, 50), 200))
        assertEquals("10:30", hm(WaterPlan.nextReminder(at(9, 51), logs, start, cut, casa)!!))
        val y = logs + WaterLog(at(10, 30), 200)
        assertEquals("11:00", hm(WaterPlan.nextReminder(at(10, 31), y, start, cut, casa)!!))
    }

    @Test
    fun `al dia, a la proxima hora del horario, y la botella cubre varias`() {
        val start = at(7, 0)
        val logs = (7..9).map { WaterLog(at(it, 2), 200) }
        assertEquals("10:00", hm(WaterPlan.nextReminder(at(9, 5), logs, start, cut, casa)!!))
        // La botella de 600 a las 10 cubre 10, 11 y 12: lo siguiente es a las 14, no a la 1.
        val conBotella = logs + WaterLog(at(10, 0), 600)
        assertEquals("14:00", hm(WaterPlan.nextReminder(at(10, 5), conBotella, start, cut, casa)!!))
    }

    @Test
    fun `a la 1 no avisa si va al dia, pero si con deficit`() {
        val start = at(7, 0)
        val alDia = (7..12).map { WaterLog(at(it, 0), 200) }
        assertEquals("14:00", hm(WaterPlan.nextReminder(at(12, 40), alDia, start, cut, casa)!!))
        val atrasado = alDia.take(4) + WaterLog(at(12, 30), 200)
        assertEquals("13:00", hm(WaterPlan.nextReminder(at(12, 40), atrasado, start, cut, casa)!!))
    }

    @Test
    fun `su caso del 4-oct, 1800 ml a las 17 49, avisa a las 18 30`() {
        val logs = listOf(WaterLog(at(10, 0), 1600), WaterLog(at(17, 49), 200))
        assertEquals("18:30", hm(WaterPlan.nextReminder(at(17, 56), logs, at(7, 0), at(19, 30), casa)!!))
    }

    @Test
    fun `dia presencial, la botella de las 9 y la segunda de las 2`() {
        fun lu(h: Int, m: Int) = lun.atTime(h, m).atZone(lima).toInstant().toEpochMilli()
        val c = lu(19, 30)
        val desayuno = listOf(WaterLog(lu(5, 0), 200, Drinks.CREATINE), WaterLog(lu(6, 30), 200, Drinks.MACA))
        assertEquals("09:00", hm(WaterPlan.nextReminder(lu(7, 0), desayuno, lu(5, 0), c, oficina)!!))
        val almuerzo = desayuno + WaterLog(lu(9, 0), 600) + WaterLog(lu(12, 30), 200, Drinks.SOUP) + WaterLog(lu(12, 35), 200, Drinks.LEMONADE)
        assertEquals("14:00", hm(WaterPlan.nextReminder(lu(13, 0), almuerzo, lu(5, 0), c, oficina)!!))
    }

    @Test
    fun `no recuerda con la meta cumplida, sin dia empezado, ni despues de cortar`() {
        assertNull(WaterPlan.nextReminder(at(15, 0), listOf(WaterLog(at(14, 0), 2000)), at(7, 0), cut, casa))
        assertNull(WaterPlan.nextReminder(at(6, 0), emptyList(), null, cut, casa))
        assertNull(WaterPlan.nextReminder(at(20, 30), listOf(WaterLog(at(10, 0), 200)), at(7, 0), cut, casa))
    }

    @Test
    fun `si ya no cabe y falta la meta, un ultimo aviso media hora antes del corte`() {
        // A las 19:00 recien tomo y le faltan 200: el de las 19:30 ya seria el corte (20:00 - 30).
        val logs = listOf(WaterLog(at(10, 0), 1600), WaterLog(at(19, 0), 200))
        assertEquals("19:30", hm(WaterPlan.nextReminder(at(19, 5), logs, at(7, 0), cut, casa)!!))
    }

    @Test
    fun `media hora en punto`() {
        assertEquals("10:30", hm(WaterPlan.halfHourUp(at(10, 20))))
        assertEquals("10:30", hm(WaterPlan.halfHourUp(at(10, 30))))
        assertEquals("11:00", hm(WaterPlan.halfHourUp(at(10, 31))))
    }

    @Test
    fun `el dia empieza al contestar la alarma, y sin ella con el primer vaso`() {
        val logs = listOf(WaterLog(at(10, 30), 200))
        assertEquals(at(7, 0), WaterPlan.start(at(7, 0), logs))
        assertEquals(at(10, 30), WaterPlan.start(null, logs))
        assertNull(WaterPlan.start(null, emptyList()))
    }

    @Test
    fun `la hora de acostarse sale de la alarma de la manana siguiente`() {
        val lunes5 = ZonedDateTime.of(2026, 10, 5, 5, 0, 0, 0, lima)
        assertEquals(at(21, 30), WaterPlan.bedFromNextRing(lunes5, dom, 450))
        // Una alarma que no es la de manana no dice nada de esta noche.
        assertNull(WaterPlan.bedFromNextRing(lunes5.plusDays(2), dom, 450))
    }

    @Test
    fun `el ritmo se cuenta en vasos, redondeado`() {
        assertEquals(8, WaterPlan.glasses(1668))
        assertEquals(1, WaterPlan.glasses(250))
        assertEquals(3, WaterPlan.glasses(600))
    }

    @Test
    fun `ida y vuelta por JSON`() {
        val logs = listOf(WaterLog(at(9, 50), 200), WaterLog(at(13, 0), 600, Drinks.COFFEE), WaterLog(at(14, 0), 200, Drinks.MACA), WaterLog(at(15, 0), 200, Drinks.LEMONADE))
        assertEquals(logs, WaterPlan.decode(WaterPlan.encode(logs)))
        // Sin tipo (lo guardado antes de los tipos) es agua.
        assertEquals(Drinks.WATER, WaterPlan.decode("""[{"at":1,"ml":200}]""").single().type)
    }

    @Test
    fun `las tomas del 4-oct de su app de antes, con su bebida y todas cuentan`() {
        val dia = WaterPlan.seed4Oct(lima)
        assertEquals(8, dia.size)
        assertEquals(1600, WaterPlan.total(dia))
        assertEquals(listOf(Drinks.CREATINE, Drinks.WATER, Drinks.WATER, Drinks.QUINOA, Drinks.WATER, Drinks.SOUP, Drinks.SODA, Drinks.WATER), dia.map { it.type })
        assertEquals("08:06", hm(dia.first().at))
    }
}
