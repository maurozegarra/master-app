package com.maurozegarra.master.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Las partes puras de lo que escribe el entrenador: el id de un perfil y el motivo de un rechazo. */
class CoachWritesTest {

    @Test
    fun `the id comes from the name, readable`() {
        assertEquals("niko", profileIdFrom("Niko"))
        assertEquals("niko-zegarra", profileIdFrom("Niko Zegarra"))
        assertEquals("mauro", profileIdFrom("  MAURO  "))
    }

    /** Los acentos no pueden acabar en el id: viajaria codificado en cada URL. */
    @Test
    fun `accents and punctuation are folded away`() {
        assertEquals("jose-maria", profileIdFrom("José María"))
        assertEquals("ana", profileIdFrom("Ana!"))
        assertEquals("a-b", profileIdFrom("a / b"))
    }

    /** Un nombre sin letras utilizables no puede quedarse sin id ni tumbar la creacion. */
    @Test
    fun `a name with nothing usable still gets an id`() {
        val id = profileIdFrom("🙂")

        assertTrue(id.isNotBlank())
        assertTrue(id.none { it.isWhitespace() })
    }

    @Test
    fun `the reason of a rejection is the one the server gave`() {
        assertEquals(
            "Invalid login credentials",
            errorOf("""{ "error": "invalid_grant", "error_description": "Invalid login credentials" }"""),
        )
        assertEquals("Email not confirmed", errorOf("""{ "msg": "Email not confirmed" }"""))
    }

    /** Sin cuerpo interpretable se enseña lo que llegó: inventar un motivo despistaría más. */
    @Test
    fun `an unreadable body is shown as it came`() {
        assertEquals("<html>502</html>", errorOf("<html>502</html>"))
        assertEquals("Sign in failed", errorOf(""))
    }
}
