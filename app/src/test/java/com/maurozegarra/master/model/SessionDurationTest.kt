package com.maurozegarra.master.model

import com.maurozegarra.master.util.formatMinutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** TD-214: la tarjeta del historial cerrada enseña cuánto duró, y el dolor de la mañana solo si hubo. */
class SessionDurationTest {

    private fun s(
        durationSec: Int = 0,
        startedAt: Long = 0L,
        completedAt: Long = 10_000_000L,
        painOnWaking: Int? = null,
        painFadeMin: Int? = null,
        painAfter: Int? = null,
    ) = SessionLog(
        id = 1, trainingId = 1, trainingName = "LUMBAR", completedAt = completedAt, startedAt = startedAt,
        durationSec = durationSec, painOnWaking = painOnWaking, painFadeMin = painFadeMin, painAfter = painAfter,
    )

    @Test
    fun `la duracion sale de lo medido, cortando el minuto`() {
        // El completo del 14-nov: 99 min.
        assertEquals(99, s(durationSec = 5951).durationMinutes())
        // Cortado y no redondeado: tiene que cuadrar con las horas de inicio y fin.
        assertEquals(99, s(durationSec = 5999).durationMinutes())
        assertEquals(1, s(durationSec = 10).durationMinutes())
    }

    @Test
    fun `sin duracion medida se calcula del inicio y el fin`() {
        assertEquals(45, s(startedAt = 1_000_000L, completedAt = 1_000_000L + 45 * 60_000L).durationMinutes())
    }

    @Test
    fun `sin duracion ni inicio no se inventa`() {
        assertNull(s().durationMinutes())
    }

    @Test
    fun `los minutos se leen como en el Now Bar, sin segundos`() {
        assertEquals("45 m", formatMinutes(45))
        assertEquals("1 h 39 m", formatMinutes(99))
        assertEquals("2 h", formatMinutes(120))
    }

    @Test
    fun `el dolor de la manana en cero no se ensena, pero se sigue guardando`() {
        val cero = s(painOnWaking = 0, painFadeMin = 0)
        assertNull(cero.wakingPainShown())
        assertFalse(cero.showsFeedback())
        // Un cero es un dato: la sesion lo tiene, aunque la tarjeta no lo repita.
        assertTrue(cero.hasFeedback())
    }

    @Test
    fun `con dolor al despertar si se ensena`() {
        val uno = s(painOnWaking = 1, painFadeMin = 15)
        assertEquals(1, uno.wakingPainShown())
        assertTrue(uno.showsFeedback())
    }

    @Test
    fun `lo demas contestado se sigue ensenando, cero incluido`() {
        assertTrue(s(painOnWaking = 0, painAfter = 0).showsFeedback())
    }
}
