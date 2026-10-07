package com.maurozegarra.master.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerClockTest {

    @Test
    fun `bajo un minuto, solo los segundos`() {
        // TD-170: era un ajuste ("Leading zeros in clock"); él eligió "30" y el ajuste se quitó.
        assertEquals("30", formatPlayerClock(30_000))
        assertEquals("5", formatPlayerClock(4_200))
        assertEquals("1:30", formatPlayerClock(90_000))
        assertEquals("1:05:00", formatPlayerClock(3_900_000))
    }
}
