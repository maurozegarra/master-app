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
    fun `corta hora y media antes de acostarse, y sin alarma a las 19`() {
        // Eran 2 h; desde el 8-oct, 1 h 30, para que quepa el vaso de las 20:00 presencial.
        assertEquals("20:30", hm(cut))
        assertEquals("19:00", hm(WaterPlan.cut(null, dom, lima)))
    }

    @Test
    fun `los dos horarios suman los 2 litros, y la alarma antes de las 6 dice cual`() {
        assertEquals(2000, WaterPlan.HOME.sumOf { it.ml })
        // El presencial suma 2,100: la botella de la tarde es de 500 y los 100 que faltan se
        // piden en casa a las 20:00 con un vaso; si ya cumplio, la meta corta antes (8-oct).
        assertEquals(2100, WaterPlan.OFFICE.sumOf { it.ml })
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
    fun `su caso del 5-oct, 500 ml a las 2 02 cubren las 2, las 3 y algo de las 4, avisa a las 5`() {
        // Dia presencial real del 5-oct. La botella va por horas: lo que falta (100 ml) no se
        // pide cada media hora. Desde el 8-oct el horario lo pide en casa, a las 20:00, que es
        // cuando llega (antes caia a las 17:00, a la hora siguiente).
        fun lu(h: Int, m: Int) = lun.atTime(h, m).atZone(lima).toInstant().toEpochMilli()
        val logs = listOf(
            WaterLog(lu(5, 9), 200, Drinks.CREATINE), WaterLog(lu(6, 50), 200, Drinks.MACA),
            WaterLog(lu(9, 9), 600), WaterLog(lu(12, 39), 200, Drinks.SOUP),
            WaterLog(lu(13, 15), 200, Drinks.LEMONADE), WaterLog(lu(14, 2), 500),
        )
        assertEquals("20:00", hm(WaterPlan.nextReminder(lu(14, 5), logs, lu(5, 0), lu(21, 30), oficina)!!))
    }

    @Test
    fun `pasado el horario con menos de un vaso, avisa a la hora siguiente y no a la media hora`() {
        // Dia en casa: 1,900 a las 17:10. A las 17:15 faltan 100, menos de un vaso: no insiste
        // a las 17:30 ni a las 17:40, pide el vaso de las 18:00, como si el horario siguiera.
        val logs = listOf(WaterLog(at(10, 0), 1700), WaterLog(at(17, 10), 200))
        assertEquals("18:00", hm(WaterPlan.nextReminder(at(17, 15), logs, at(7, 0), cut, casa)!!))
    }

    @Test
    fun `su caso del 8-oct, tras un vaso a las 10 54 el aviso no es a las 11 sino a las 11 30`() {
        // Feriado, horario de casa. Los avisos caen en :00 o :30 y nunca a menos de 15 min del
        // ultimo trago: a las 11:00 no le entra otro vaso. Antes salia a las 11:24.
        val logs = listOf(
            WaterLog(at(7, 36), 200, Drinks.CREATINE), WaterLog(at(8, 38), 200),
            WaterLog(at(9, 39), 200, Drinks.COFFEE), WaterLog(at(10, 54), 200),
        )
        assertEquals("11:30", hm(WaterPlan.nextReminder(at(10, 56), logs, at(7, 0), cut, casa)!!))
    }

    @Test
    fun `quince minutos tras el ultimo trago, ni uno menos`() {
        val antes = listOf(WaterLog(at(7, 5), 200, Drinks.CREATINE), WaterLog(at(8, 5), 200), WaterLog(at(9, 5), 200))
        // A las 9:39 el de las 10:00 queda: 21 minutos.
        val a939 = antes.take(2) + WaterLog(at(9, 39), 200)
        assertEquals("10:00", hm(WaterPlan.nextReminder(at(9, 40), a939, at(7, 0), cut, casa)!!))
        // A las 10:45, justo 15: queda el de las 11:00. A las 10:46 pasa a las 11:30.
        assertEquals("11:00", hm(WaterPlan.nextReminder(at(10, 50), antes + WaterLog(at(10, 45), 200), at(7, 0), cut, casa)!!))
        assertEquals("11:30", hm(WaterPlan.nextReminder(at(10, 50), antes + WaterLog(at(10, 46), 200), at(7, 0), cut, casa)!!))
    }

    @Test
    fun `dia presencial, los 100 ml que faltan se piden en casa a las 20 y no si ya cumplio`() {
        fun lu(h: Int, m: Int) = lun.atTime(h, m).atZone(lima).toInstant().toEpochMilli()
        val dia = listOf(
            WaterLog(lu(5, 0), 200, Drinks.CREATINE), WaterLog(lu(6, 30), 200, Drinks.MACA),
            WaterLog(lu(9, 0), 600, Drinks.SPARKLING), WaterLog(lu(12, 30), 200, Drinks.SOUP),
            WaterLog(lu(13, 0), 200, Drinks.LEMONADE), WaterLog(lu(14, 0), 500, Drinks.BOTTLE),
        )
        // Corte: cama a las 21:30 menos 1 h 30 = 20:00, y el aviso de las 20:00 entra justo.
        assertEquals("20:00", hm(WaterPlan.nextReminder(lu(16, 30), dia, lu(5, 0), lu(20, 0), oficina)!!))
        assertNull(WaterPlan.nextReminder(lu(16, 30), dia + WaterLog(lu(16, 20), 100), lu(5, 0), lu(20, 0), oficina))
    }

    @Test
    fun `la botella se dosifica por tercios y el boton sabe cual toca`() {
        fun lu(h: Int, m: Int) = lun.atTime(h, m).atZone(lima).toInstant().toEpochMilli()
        val gas = listOf(WaterLog(lu(9, 5), 600, Drinks.SPARKLING))
        assertEquals(WaterPlan.Dose(Drinks.SPARKLING, 1, lu(10, 0)), WaterPlan.doseAt(lu(9, 30), gas, lima))
        assertEquals(2, WaterPlan.doseAt(lu(10, 10), gas, lima)?.part)
        assertEquals(3, WaterPlan.doseAt(lu(11, 59), gas, lima)?.part)
        assertNull(WaterPlan.doseAt(lu(12, 0), gas, lima))
        // Anotada a las 12:55 cuenta desde la 1:00: su primera hora no dura 5 minutos.
        val tarde = listOf(WaterLog(lu(12, 55), 500, Drinks.BOTTLE))
        assertEquals(WaterPlan.Dose(Drinks.BOTTLE, 1, lu(14, 0)), WaterPlan.doseAt(lu(12, 57), tarde, lima))
        assertEquals(1, WaterPlan.doseAt(lu(13, 30), tarde, lima)?.part)
        assertEquals(3, WaterPlan.doseAt(lu(15, 30), tarde, lima)?.part)
        assertEquals(Drinks.SPARKLING to 600, WaterPlan.bottleFor(lu(9, 0), lima))
        assertEquals(Drinks.BOTTLE to 500, WaterPlan.bottleFor(lu(14, 0), lima))
    }

    @Test
    fun `cada hora anota su bebida y la primera del dia es creatina`() {
        fun lu(h: Int, m: Int) = lun.atTime(h, m).atZone(lima).toInstant().toEpochMilli()
        val historia = listOf(WaterLog(at(6, 40), 200, Drinks.COFFEE))
        val nada = emptyList<WaterLog>()
        // La primera, a la hora que sea: las 5, o un domingo a las 11.
        assertEquals(Drinks.CREATINE, WaterPlan.drinkFor(lu(5, 2), nada, WaterPlan.OFFICE, lun, lima, historia))
        assertEquals(Drinks.CREATINE, WaterPlan.drinkFor(at(11, 0), nada, WaterPlan.HOME, dom, lima, historia))
        val yaTomo = listOf(WaterLog(lu(5, 2), 200, Drinks.CREATINE))
        // El desayuno repite el ultimo: ese dia fue cafe.
        assertEquals(Drinks.COFFEE, WaterPlan.drinkFor(lu(6, 40), yaTomo, WaterPlan.OFFICE, lun, lima, historia))
        assertEquals(Drinks.SOUP, WaterPlan.drinkFor(lu(12, 35), yaTomo, WaterPlan.OFFICE, lun, lima, historia))
        assertEquals(Drinks.LEMONADE, WaterPlan.drinkFor(lu(13, 5), yaTomo, WaterPlan.OFFICE, lun, lima, historia))
        // Entre las 9 y las 11 se va tomando la botella con gas; de 14 a 16, la sin gas.
        assertEquals(Drinks.SPARKLING, WaterPlan.drinkFor(lu(10, 50), yaTomo, WaterPlan.OFFICE, lun, lima, historia))
        assertEquals(Drinks.BOTTLE, WaterPlan.drinkFor(lu(15, 20), yaTomo, WaterPlan.OFFICE, lun, lima, historia))
        assertEquals(Drinks.WATER, WaterPlan.drinkFor(lu(20, 5), yaTomo, WaterPlan.OFFICE, lun, lima, historia))
        // Sin desayunos anotados todavia, maca.
        assertEquals(Drinks.MACA, WaterPlan.drinkFor(lu(6, 30), yaTomo, WaterPlan.OFFICE, lun, lima, nada))
    }

    @Test
    fun `no recuerda con la meta cumplida, sin dia empezado, ni despues de cortar`() {
        assertNull(WaterPlan.nextReminder(at(15, 0), listOf(WaterLog(at(14, 0), 2000)), at(7, 0), cut, casa))
        assertNull(WaterPlan.nextReminder(at(6, 0), emptyList(), null, cut, casa))
        // El corte es a las 20:30; a las 20:45 ya no hay nada.
        assertNull(WaterPlan.nextReminder(at(20, 45), listOf(WaterLog(at(10, 0), 200)), at(7, 0), cut, casa))
    }

    @Test
    fun `cerca del corte, el aviso de las y media cabe justo en el corte`() {
        // Desde el 8-oct el corte vale como ultimo momento para avisar (es el de las 20:00 de
        // los dias presenciales). A las 20:00 tomo y le faltan: 20:30, que es el corte.
        val logs = listOf(WaterLog(at(10, 0), 1600), WaterLog(at(20, 0), 200))
        assertEquals("20:30", hm(WaterPlan.nextReminder(at(20, 5), logs, at(7, 0), cut, casa)!!))
    }

    @Test
    fun `dos tomas en el mismo minuto no chocan`() {
        // El 8-oct dos tomas de las 12:47 tenian el mismo instante y el app se caia al abrir.
        val una = WaterLog(at(12, 47), 600, Drinks.SPARKLING)
        val otra = WaterLog(at(12, 47), 500, Drinks.BOTTLE)
        val dos = WaterPlan.add(listOf(una), otra, dom, lima)
        assertEquals(2, dos.map { it.at }.toSet().size)
        assertEquals(listOf(600, 500), dos.map { it.ml })
        // A la vista sigue siendo 12:47: solo se corre un milisegundo.
        assertEquals(at(12, 47) + 1, dos[1].at)
        // Lo ya guardado se repara al leerlo.
        assertEquals(2, WaterPlan.unique(listOf(una, otra)).map { it.at }.toSet().size)
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
