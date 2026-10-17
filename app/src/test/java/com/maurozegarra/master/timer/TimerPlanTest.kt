package com.maurozegarra.master.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** El timer del tablero (TD-195): lo que se calcula sin Android. */
class TimerPlanTest {

    private val lima = ZoneId.of("America/Lima")
    private fun at(h: Int, m: Int, s: Int = 0) =
        LocalDate.of(2026, 10, 9).atTime(h, m, s).atZone(lima).toInstant().toEpochMilli()

    @Test
    fun `los chips de Ends at son los seis multiplos de 5 minutos, a un minuto o mas`() {
        assertEquals(
            listOf("23:25", "23:30", "23:35", "23:40", "23:45", "23:50"),
            TimerPlan.endsAtChips(at(23, 22)).map { TimerPlan.clock(it, lima) },
        )
        // A las 23:24:30 la de 23:25 queda a menos de un minuto: no entra.
        assertEquals("23:30", TimerPlan.clock(TimerPlan.endsAtChips(at(23, 24, 30)).first(), lima))
    }

    @Test
    fun `el teclado entra por la derecha`() {
        assertEquals(90_000L, TimerPlan.digitsToMs("130"))
        assertEquals(3_600_000L, TimerPlan.digitsToMs("10000"))
        assertEquals(0L, TimerPlan.digitsToMs(""))
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
}
