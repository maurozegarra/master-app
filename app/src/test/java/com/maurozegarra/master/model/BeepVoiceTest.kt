package com.maurozegarra.master.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** La voz de los pitidos del teléfono (TD-171). */
class BeepVoiceTest {

    private val samsung: (String) -> Boolean = { true }
    private val otroTelefono: (String) -> Boolean = { false }

    @Test
    fun `las del app se ofrecen en cualquier telefono`() {
        val ids = BeepVoices.available(otroTelefono).map { it.id }
        assertEquals(listOf("classic", "high", "low", "double"), ids)
    }

    @Test
    fun `las de Samsung solo donde esta el archivo`() {
        assertTrue(BeepVoices.available(samsung).any { it.id == "samsung_dial_retro" })
        assertTrue(BeepVoices.available(otroTelefono).none { it.id.startsWith("samsung") })
    }

    @Test
    fun `una voz de Samsung en otro telefono cae a Classic`() {
        // El usuario la pidio con "fallback a tu propuesta": el pitido tiene que sonar siempre.
        assertEquals(BeepVoices.CLASSIC, BeepVoices.resolve("samsung_dial_retro", otroTelefono))
        assertEquals("samsung_dial_retro", BeepVoices.resolve("samsung_dial_retro", samsung).id)
    }

    @Test
    fun `un id que no existe cae a Classic`() {
        assertEquals(BeepVoices.CLASSIC, BeepVoices.resolve("lo-que-sea", samsung))
        assertEquals("low", BeepVoices.resolve("low", otroTelefono).id)
    }
}
