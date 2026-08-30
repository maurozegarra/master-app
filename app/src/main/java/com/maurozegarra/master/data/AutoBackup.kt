package com.maurozegarra.master.data

import java.time.LocalDate

/**
 * Snapshot automático de los datos del usuario en almacenamiento compartido.
 *
 * El export manual (TD-009) solo salva si el usuario se acordó de correrlo. Esto lo
 * hace solo: escribe el mismo JSON de respaldo en `Documents/MASTER/` vía [SharedFiles],
 * donde los archivos **sobreviven a la desinstalación** —a diferencia de
 * SharedPreferences y de `Android/data/`—, así que reinstalar deja de ser destructivo.
 *
 * Un archivo por día, con la fecha en el nombre, conservando los últimos [KEEP]. Que
 * sean varios y no uno solo es deliberado: si un día se guardan datos vacíos o
 * corruptos, los días anteriores siguen ahí.
 */
class AutoBackup(private val files: SharedFiles) {

    /** true si el snapshot se escribió. false si la plataforma no lo permite o falló. */
    fun write(json: String, day: LocalDate = LocalDate.now()): Boolean {
        if (!files.writeDocument(fileName(day), json.toByteArray())) return false
        prune()
        return true
    }

    /** Borra los snapshots más viejos: el nombre lleva fecha ISO, que ordena solo. */
    private fun prune() {
        files.listDocuments(PREFIX)
            .sortedByDescending { it.first }
            .drop(KEEP)
            .forEach { (_, id) -> files.deleteDocumentById(id) }
    }

    private fun fileName(day: LocalDate) = "$PREFIX$day.json"

    private companion object {
        const val PREFIX = "master-autobackup-"
        const val KEEP = 7
    }
}
