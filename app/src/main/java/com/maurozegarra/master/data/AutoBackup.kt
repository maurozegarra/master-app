package com.maurozegarra.master.data

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Snapshot automático de los datos del usuario en almacenamiento compartido.
 *
 * El export manual (TD-009) solo salva si el usuario se acordó de correrlo. Esto lo
 * hace solo: escribe el mismo JSON de respaldo en `Documents/MASTER/` vía [SharedFiles],
 * donde los archivos **sobreviven a la desinstalación** —a diferencia de
 * SharedPreferences y de `Android/data/`—, así que reinstalar deja de ser destructivo.
 *
 * Hay dos series, y estar separadas es el punto:
 *
 * - **Rodante** ([write]): el estado actual, un archivo por minuto. Se conserva el más
 *   nuevo de cada uno de los últimos [KEEP_DAYS] días más los [KEEP_RECENT] últimos, así
 *   que hay grano fino para lo recién ocurrido y una semana de cobertura hacia atrás.
 * - **Pre-import** ([writeBeforeImport]): el estado justo antes de la única operación
 *   que borra todo a propósito. Se poda aparte para que un día movido de snapshots
 *   rodantes no pueda desalojarla.
 *
 * La versión anterior guardaba **un archivo por día**, y el 30-ago-2026 eso dejó al
 * usuario sin datos: tras importar un respaldo de prueba, correr un training reescribió
 * el archivo del día con el estado nuevo y el anterior desapareció. Siete días de
 * historia no sirven cuando dos eventos destructivos caen el mismo día.
 */
class AutoBackup(private val files: SharedFiles) {

    /** true si el snapshot se escribió. false si la plataforma no lo permite o falló. */
    fun write(json: String, at: LocalDateTime = LocalDateTime.now()): Boolean {
        if (!files.writeDocument(PREFIX + STAMP.format(at) + ".json", json.toByteArray())) return false
        prune(PREFIX, KEEP_RECENT, KEEP_DAYS)
        return true
    }

    /**
     * Snapshot previo a un import, que reemplaza todos los datos. Lleva segundos en el
     * nombre: dos imports seguidos son plausibles cuando el usuario está probando
     * respaldos, y son justo los dos estados que no puede perder.
     */
    fun writeBeforeImport(json: String, at: LocalDateTime = LocalDateTime.now()): Boolean {
        if (!files.writeDocument(PREIMPORT_PREFIX + STAMP_SEC.format(at) + ".json", json.toByteArray())) return false
        prune(PREIMPORT_PREFIX, KEEP_PREIMPORT, keepDays = 0)
        return true
    }

    private fun prune(prefix: String, keepRecent: Int, keepDays: Int) {
        val docs = files.listDocuments(prefix)
        val expired = SnapshotRetention.expired(docs.map { it.first }, prefix, keepRecent, keepDays).toSet()
        docs.filter { it.first in expired }.forEach { files.deleteDocumentById(it.second) }
    }

    private companion object {
        const val PREFIX = "master-autobackup-"
        const val PREIMPORT_PREFIX = "master-preimport-"
        const val KEEP_RECENT = 8
        const val KEEP_DAYS = 7
        const val KEEP_PREIMPORT = 5

        // Fecha ISO por delante para que el nombre ordene solo, y guiones en la hora
        // porque los dos puntos no valen en un nombre de archivo.
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm")
        val STAMP_SEC: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss")
    }
}

/**
 * Qué snapshots sobran. Puro a propósito: decide qué se **borra**, así que conviene
 * poder probarlo desde la suite JVM en vez de descubrirlo con los datos del usuario.
 */
internal object SnapshotRetention {

    /**
     * Nombres a borrar de entre [names]. Se conservan los [keepRecent] más nuevos y,
     * además, el más nuevo de cada uno de los [keepDays] días más recientes.
     *
     * Las dos reglas cubren cosas distintas: los recientes permiten deshacer algo que
     * acaba de pasar, y el uno-por-día evita que una tarde intensa se lleve por delante
     * toda la historia anterior.
     */
    fun expired(names: List<String>, prefix: String, keepRecent: Int, keepDays: Int): List<String> {
        val newestFirst = names.sortedDescending()
        val keep = HashSet<String>(newestFirst.take(keepRecent))
        newestFirst.groupBy { dayOf(it, prefix) }.values.take(keepDays).forEach { keep += it.first() }
        return newestFirst.filterNot { it in keep }
    }

    /**
     * `master-autobackup-2026-08-30T14-35.json` -> `2026-08-30`. Los nombres del formato
     * viejo, sin hora, dan el mismo día: siguen ordenando y podándose con el resto.
     */
    private fun dayOf(name: String, prefix: String): String = name.removePrefix(prefix).take(10)
}
