package com.maurozegarra.master.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log

/**
 * Archivos del app en almacenamiento compartido, vía MediaStore. Es el único sitio cuyos
 * archivos **sobreviven a una desinstalación**, a diferencia de SharedPreferences y de
 * `Android/data/`.
 *
 * Guarda **solo los snapshots de respaldo**, en `Documents/MASTER/`. Aquí funciona
 * porque el usuario los recupera con el selector de archivos del sistema, eligiéndolos a
 * mano: no dependen de que MediaStore siga atribuyéndoselos al app.
 *
 * Los vídeos vivieron aquí y se sacaron a la caché privada (`VideoCache`). En
 * almacenamiento compartido una consulta solo devuelve los archivos de medios que creó el
 * propio app —ni siquiera con `READ_MEDIA_VIDEO` se ven los de otro propietario—, y
 * MediaProvider renombra en vez de sobrescribir cuando el nombre ya está ocupado. Las dos
 * cosas juntas hacían desaparecer vídeos recién guardados.
 *
 * Requiere API 29 (Q); por debajo, escribir fuera del sandbox exige
 * WRITE_EXTERNAL_STORAGE, que no se pide.
 */
class SharedFiles(context: Context) {

    private val appCtx = context.applicationContext

    val isAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    // ---------- Documentos: snapshots de respaldo ----------

    /** Escribe un JSON en `Documents/MASTER/`, reemplazándolo si ya existe. */
    fun writeDocument(name: String, bytes: ByteArray): Boolean =
        write(filesCollection(), DOCS_PATH, name, "application/json") { it.write(bytes) } != null

    /** Nombres e ids de los documentos de `Documents/MASTER/` que empiezan por [prefix]. */
    fun listDocuments(prefix: String): List<Pair<String, Long>> =
        list(filesCollection(), DOCS_PATH, prefix)

    fun deleteDocumentById(id: Long): Boolean = deleteById(filesCollection(), id)

    // ---------- Mecánica común ----------

    /** Devuelve el nombre real del archivo escrito, o null si no se pudo. */
    private fun write(
        collection: Uri,
        relPath: String,
        name: String,
        mime: String,
        block: (java.io.OutputStream) -> Unit,
    ): String? {
        if (!isAvailable) return null
        return try {
            val uri = find(collection, relPath, name) ?: appCtx.contentResolver.insert(
                collection,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
                },
            )
            if (uri == null) {
                Log.w(TAG, "insert devolvió null para $relPath$name")
                return null
            }
            // "wt" trunca: sin eso, un contenido más corto que el anterior dejaría cola
            // del archivo viejo y el resultado quedaría corrupto.
            val stream = appCtx.contentResolver.openOutputStream(uri, "wt")
            if (stream == null) {
                Log.w(TAG, "sin OutputStream para $uri")
                return null
            }
            stream.use(block)
            displayNameOf(uri) ?: name
        } catch (e: Exception) {
            // Un fallo al guardar en compartido nunca debe tumbar el app, pero tampoco
            // puede desaparecer: sin este log el error solo se ve como un botón que no
            // cambia, y eso costó una sesión entera de diagnóstico.
            Log.w(TAG, "no se pudo escribir $relPath$name", e)
            null
        }
    }

    private fun displayNameOf(uri: Uri): String? = runCatching {
        appCtx.contentResolver
            .query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()

    private fun find(collection: Uri, relPath: String, name: String): Uri? = runCatching {
        appCtx.contentResolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
            arrayOf(name, "$relPath%"),
            null,
        )?.use { c ->
            if (c.moveToFirst()) ContentUris.withAppendedId(collection, c.getLong(0)) else null
        }
    }.onFailure {
        Log.w(TAG, "no se pudo consultar $relPath$name", it)
    }.getOrNull()

    private fun list(collection: Uri, relPath: String, prefix: String): List<Pair<String, Long>> {
        if (!isAvailable) return emptyList()
        val out = mutableListOf<Pair<String, Long>>()
        runCatching {
            appCtx.contentResolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
                "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
                arrayOf("$prefix%", "$relPath%"),
                null,
            )?.use { c ->
                while (c.moveToNext()) out.add(c.getString(1) to c.getLong(0))
            }
        }
        return out
    }

    private fun deleteById(collection: Uri, id: Long): Boolean = runCatching {
        appCtx.contentResolver.delete(ContentUris.withAppendedId(collection, id), null, null) > 0
    }.getOrDefault(false)

    private fun filesCollection() = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)


    private companion object {
        const val TAG = "SharedFiles"
        val DOCS_PATH = "${Environment.DIRECTORY_DOCUMENTS}/MASTER/"
    }
}
