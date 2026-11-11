package com.maurozegarra.master.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** El timer del tablero (TD-195): lo que se calcula sin Android. */
class TimerPlanTest {

    @Test
    fun `los chips de Ends at son los seis multiplos de 5 minutos, a un minuto o mas`() {
        val zona = java.time.ZoneId.of("America/Lima")
        fun a(h: Int, m: Int, s: Int = 0) =
            java.time.LocalDate.of(2026, 11, 10).atTime(h, m, s).atZone(zona).toInstant().toEpochMilli()
        assertEquals(
            listOf("23:25", "23:30", "23:35", "23:40", "23:45", "23:50"),
            TimerPlan.endsAtChips(a(23, 22)).map { TimerPlan.clock(it, zona) },
        )
        // A las 23:24:30 la de 23:25 queda a menos de un minuto: no entra.
        assertEquals("23:30", TimerPlan.clock(TimerPlan.endsAtChips(a(23, 24, 30)).first(), zona))
    }

    private val lima = ZoneId.of("America/Lima")
    private fun at(h: Int, m: Int, s: Int = 0) =
        LocalDate.of(2026, 10, 9).atTime(h, m, s).atZone(lima).toInstant().toEpochMilli()

    // TD-200 quito los chips de "Ends at" y el teclado, y sus tests con ellos: la hora va en la
    // pestaña Alarm y la duracion en la rueda (ver los tests de abajo).

    @Test
    fun `la duracion se escribe corta`() {
        assertEquals("1:30", TimerPlan.format(90_000))
        assertEquals("1:00:00", TimerPlan.format(3_600_000))
        assertEquals("5:00", TimerPlan.format(5 * 60_000L))
    }

    @Test
    fun `la pausa guarda lo que falta y lo devuelve al seguir`() {
        val run = TimerPlan.start("a", "nap", 30 * 60_000L, alarm = true, now = at(13, 30))
        assertEquals(at(14, 0), run.endAt)
        val pausa = TimerPlan.pause(run, at(13, 40))
        assertTrue(pausa.paused)
        assertEquals(20 * 60_000L, TimerPlan.left(pausa, at(13, 55)))
        val sigue = TimerPlan.resume(pausa, at(13, 50))
        assertFalse(sigue.paused)
        assertEquals(at(14, 10), sigue.endAt)
    }

    @Test
    fun `los recientes van arriba, sin repetir, y son cinco`() {
        var r = emptyList<RecentTimer>()
        (1..7).forEach { r = TimerPlan.addRecent(r, RecentTimer("", it * 60_000L, false)) }
        assertEquals(5, r.size)
        assertEquals(7 * 60_000L, r.first().durationMs)
        r = TimerPlan.addRecent(r, RecentTimer("", 4 * 60_000L, false))
        assertEquals(4 * 60_000L, r.first().durationMs)
        assertEquals(5, r.size)
        assertEquals(1, r.count { it.durationMs == 4 * 60_000L })
    }

    @Test
    fun `los presets de su reloj y sus nombres cortos`() {
        assertEquals(12, TimerPlan.PRESETS_SEC.size)
        assertEquals(listOf("30s", "1m", "1h"), listOf(30, 60, 3600).map(TimerPlan::presetLabel))
    }

    @Test
    fun `restart vuelve a la duracion completa, en pausa`() {
        val corriendo = TimerPlan.start("a", "tea", 240_000, false, at(9, 0))
        val r = TimerPlan.restart(corriendo)
        assertEquals(240_000L, r.pausedLeftMs)
        assertEquals(240_000L, TimerPlan.left(r, at(9, 3)))
        // Desde la pausa tambien, y al darle play cuenta los cuatro minutos enteros.
        val pausado = TimerPlan.pause(corriendo, at(9, 1))
        assertEquals(at(9, 7) + 240_000, TimerPlan.resume(TimerPlan.restart(pausado), at(9, 7)).endAt)
    }

    @Test
    fun `en la franja el ultimo que arranco va arriba`() {
        val runs = listOf(
            TimerPlan.start("a", "potty", 5 * 60_000L, false, at(9, 0)),
            TimerPlan.start("b", "tea", 240_000, false, at(9, 1)),
            TimerPlan.start("c", "nap", 1_800_000, true, at(9, 2)),
        )
        assertEquals(listOf("c", "b", "a"), TimerPlan.stripOrder(runs).map { it.id })
    }

    @Test
    fun `ida y vuelta por JSON, con la pausa`() {
        val runs = listOf(
            TimerPlan.start("a", "potty", 5 * 60_000L, false, at(9, 0)),
            TimerPlan.pause(TimerPlan.start("b", "nap", 1_800_000, true, at(13, 30)), at(13, 31)),
        )
        assertEquals(runs, TimerPlan.decodeRuns(TimerPlan.encodeRuns(runs)))
        val rec = listOf(RecentTimer("disconnect heater", 1_200_000, false))
        assertEquals(rec, TimerPlan.decodeRecents(TimerPlan.encodeRecents(rec)))
    }

    @Test
    fun `la alarma suena hoy si la hora no paso, si no manana`() {
        val ahora = at(11, 15)
        val hoy = TimerPlan.nextAt(13 * 60 + 30, ahora, lima)
        assertEquals(at(13, 30), hoy)
        assertFalse(TimerPlan.isTomorrow(hoy, ahora, lima))
        assertEquals("2 h 15 min", TimerPlan.untilLabel(hoy - ahora))
        // Las 7:00 ya pasaron: mañana.
        val manana = TimerPlan.nextAt(7 * 60, ahora, lima)
        assertTrue(TimerPlan.isTomorrow(manana, ahora, lima))
        assertEquals("19 h 45 min", TimerPlan.untilLabel(manana - ahora))
        // La misma hora que ahora tambien es mañana: sonaria en este instante.
        assertTrue(TimerPlan.isTomorrow(TimerPlan.nextAt(11 * 60 + 15, ahora, lima), ahora, lima))
    }

    @Test
    fun `lo que falta se redondea al minuto de arriba`() {
        assertEquals("1 min", TimerPlan.untilLabel(20_000))
        assertEquals("45 min", TimerPlan.untilLabel(44 * 60_000L + 1))
        assertEquals("1 h", TimerPlan.untilLabel(60 * 60_000L))
    }

    @Test
    fun `las ruedas dan la vuelta, como las del reloj`() {
        assertEquals(99, TimerPlan.wrap(-1, 0..TimerPlan.TIMER_HOURS_MAX))
        assertEquals(0, TimerPlan.wrap(60, 0..59))
        assertEquals(12, TimerPlan.wrap(0, 1..12))
        assertEquals(1, TimerPlan.wrap(13, 1..12))
    }

    @Test
    fun `doce horas y AM PM`() {
        assertEquals(0, TimerPlan.to24(12, pm = false))
        assertEquals(12, TimerPlan.to24(12, pm = true))
        assertEquals(13, TimerPlan.to24(1, pm = true))
        assertEquals(12, TimerPlan.hour12(0))
        assertEquals(1, TimerPlan.hour12(13))
        assertEquals("1:30 PM", TimerPlan.clock12(13 * 60 + 30))
        assertEquals("12:05 AM", TimerPlan.clock12(5))
        assertEquals("10:30 PM", TimerPlan.clock12(at(22, 30), lima))
    }

    @Test
    fun `la alarma propone media hora mas tarde, en 5 minutos`() {
        assertEquals(11 * 60 + 50, TimerPlan.defaultAlarmMinute(at(11, 17), lima))
        assertEquals(12 * 60, TimerPlan.defaultAlarmMinute(at(11, 30), lima))
        // Pasada la medianoche vuelve a empezar el dia.
        assertEquals(5, TimerPlan.defaultAlarmMinute(at(23, 33), lima))
    }

    @Test
    fun `las alarmas recientes se guardan por hora`() {
        val siesta = RecentAlarm("Nap", 13 * 60 + 30)
        val lista = TimerPlan.addAlarmRecent(listOf(RecentAlarm("", 14 * 60), siesta), siesta)
        assertEquals(siesta, lista.first())
        assertEquals(2, lista.size)
        assertEquals(lista, TimerPlan.decodeAlarmRecents(TimerPlan.encodeAlarmRecents(lista)))
        assertEquals(15 * 60_000L + 30_000L, TimerPlan.durationMs(0, 15, 30))
    }

    @Test
    fun `teclear un numero en la rueda, como Alarm Clock Xtreme`() {
        val min = 0..59
        // "4" y "5": 45, y pasa a la siguiente columna.
        val cuatro = TimerPlan.typeDigit("", '4', min)
        assertEquals(TimerPlan.Typed("4", 4, false), cuatro)
        assertEquals(TimerPlan.Typed("45", 45, true), TimerPlan.typeDigit(cuatro.buffer, '5', min))
        // Un 7 en los minutos todavía puede ser 07: espera. Con un 5 detrás, 75 no existe: 5.
        assertEquals(TimerPlan.Typed("5", 5, false), TimerPlan.typeDigit("7", '5', min))
        // En las horas de 1 a 12, un 7 no tiene segundo dígito: avanza solo.
        val horas = 1..12
        assertEquals(TimerPlan.Typed("7", 7, true), TimerPlan.typeDigit("", '7', horas))
        // Un 1 puede ser 1, 10, 11 o 12: espera.
        assertEquals(TimerPlan.Typed("1", 1, false), TimerPlan.typeDigit("", '1', horas))
        assertEquals(TimerPlan.Typed("12", 12, true), TimerPlan.typeDigit("1", '2', horas))
        // Un 0 en las horas no es una hora todavía; con un 5 detrás, las 5.
        assertEquals(TimerPlan.Typed("0", null, false), TimerPlan.typeDigit("", '0', horas))
        assertEquals(TimerPlan.Typed("05", 5, true), TimerPlan.typeDigit("0", '5', horas))
    }
}
