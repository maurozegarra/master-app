package com.maurozegarra.master.ui.settings

import com.maurozegarra.master.SyncResult
import com.maurozegarra.master.i18n.Strings

/**
 * Lo que se le dice al usuario según cómo acabó una sincronización.
 *
 * Los cuatro finales se ven exactamente igual —la pantalla no cambia— y significan cosas
 * distintas: callar convertiría "no hay conexión" en "no te toca nada".
 *
 * Que no le toque ninguno manda sobre que algo haya cambiado, y ese orden importa: la
 * sincronización que retira el último training asignado cambia la lista **y** deja al
 * perfil sin nada, y de las dos cosas la que hay que contar es la segunda.
 */
fun syncMessage(result: SyncResult, t: Strings): String = when (result) {
    SyncResult.NoProfile -> t.syncNoProfile
    SyncResult.Failed -> t.syncFailed
    is SyncResult.Ok -> when {
        result.assigned == 0 -> t.syncNothingAssigned
        result.changed -> t.syncUpdated
        else -> t.syncUpToDate
    }
}
