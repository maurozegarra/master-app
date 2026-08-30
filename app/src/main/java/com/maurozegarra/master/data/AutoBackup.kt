package com.maurozegarra.master.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.time.LocalDate

/**
 * Snapshot automático de los datos del usuario en almacenamiento compartido.
 *
 * El export manual (TD-009) solo salva si el usuario se acordó de correrlo. Esto lo
 * hace solo: escribe el mismo JSON de respaldo en `Documents/MASTER/` vía MediaStore,
 * y esos archivos **sobreviven a la desinstalación** —a diferencia de SharedPreferences
 * y de `Android/data/`—, así que reinstalar deja de ser destructivo.
 *
 * Un archivo por día, con la fecha en el nombre, conservando los últimos [KEEP]. Que
 * sean varios y no uno solo es deliberado: si un día se guardan datos vacíos o
 * corruptos, los días anteriores siguen ahí.
 *
 * Requiere API 29 (Q): antes de eso escribir fuera del sandbox exige el permiso
 * WRITE_EXTERNAL_STORAGE, y no vale pedirle un permiso intrusivo al usuario para algo
 * que ocurre en segundo plano. En API 26-28 el snapshot simplemente no corre y queda
 * el export manual.
 */
class AutoBackup(context: Context) {

    private val appCtx = context.applicationContext

    /** true si el snapshot se escribió. false si la plataforma no lo permite o falló. */
    fun write(json: String, day: LocalDate = LocalDate.now()): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val resolver = appCtx.contentResolver
        val name = fileName(day)
        return try {
            val existing = findByName(name)
            val uri = existing ?: resolver.insert(
                collection(),
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, MIME)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, REL_PATH)
                },
            ) ?: return false
            // "wt" trunca: sin eso, un snapshot más corto que el anterior dejaría cola
            // del JSON viejo y el archivo quedaría corrupto.
            resolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) } ?: return false
            prune()
            true
        } catch (_: Exception) {
            // Un fallo al respaldar nunca debe tumbar la app ni bloquear un guardado.
            false
        }
    }

    private fun collection() = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    private fun findByName(name: String) = appCtx.contentResolver.query(
        collection(),
        arrayOf(MediaStore.MediaColumns._ID),
        "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
        arrayOf(name, "$REL_PATH%"),
        null,
    )?.use { c ->
        if (c.moveToFirst()) {
            android.content.ContentUris.withAppendedId(collection(), c.getLong(0))
        } else {
            null
        }
    }

    /** Borra los snapshots más viejos: el nombre lleva fecha ISO, que ordena solo. */
    private fun prune() {
        val resolver = appCtx.contentResolver
        val names = mutableListOf<Pair<String, Long>>()
        resolver.query(
            collection(),
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
            arrayOf("$PREFIX%", "$REL_PATH%"),
            null,
        )?.use { c ->
            while (c.moveToNext()) names.add(c.getString(1) to c.getLong(0))
        }
        names.sortedByDescending { it.first }
            .drop(KEEP)
            .forEach { (_, id) ->
                runCatching {
                    resolver.delete(android.content.ContentUris.withAppendedId(collection(), id), null, null)
                }
            }
    }

    private fun fileName(day: LocalDate) = "$PREFIX$day.json"

    private companion object {
        const val PREFIX = "master-autobackup-"
        const val MIME = "application/json"
        val REL_PATH = "${Environment.DIRECTORY_DOCUMENTS}/MASTER/"
        const val KEEP = 7
    }
}
