package com.maurozegarra.master.ui.settings

import com.maurozegarra.master.SyncResult
import com.maurozegarra.master.i18n.I18n
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Lo que se cuenta tras sincronizar. Los cuatro finales se ven igual en pantalla, así que
 * el aviso es lo único que los distingue; confundir dos de ellos es el fallo que importa.
 */
class SyncMessageTest {

    private val t = I18n.EN

    @Test
    fun `a failure is never told as an empty assignment`() {
        assertEquals(t.syncFailed, syncMessage(SyncResult.Failed, t))
        assertEquals(t.syncNoProfile, syncMessage(SyncResult.NoProfile, t))
    }

    @Test
    fun `having nothing assigned is said even when that is the change`() {
        // Retirar el ultimo training cambia la lista Y deja al perfil sin nada: de las dos
        // cosas, la que hay que contar es que ya no le toca ninguno.
        assertEquals(t.syncNothingAssigned, syncMessage(SyncResult.Ok(assigned = 0, changed = true), t))
        assertEquals(t.syncNothingAssigned, syncMessage(SyncResult.Ok(assigned = 0, changed = false), t))
    }

    @Test
    fun `a sync that changed something is not confused with one that did not`() {
        assertEquals(t.syncUpdated, syncMessage(SyncResult.Ok(assigned = 2, changed = true), t))
        assertEquals(t.syncUpToDate, syncMessage(SyncResult.Ok(assigned = 2, changed = false), t))
    }
}
