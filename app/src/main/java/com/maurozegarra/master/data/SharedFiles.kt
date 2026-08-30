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
 * Hay dos áreas separadas, y la separación no es estética: MediaProvider **valida el
 * directorio raíz contra la colección** y rechaza la inserción con
 * `IllegalArgumentException: Primary directory X not allowed for content://media/...`.
 *
 * - **Snapshots JSON → `Documents/MASTER/`**, colección `Files`, que admite `Documents` y
 *   `Download`. Un JSON no es un archivo de medios.
 * - **Vídeos → `Movies/MASTER/`**, colección `Video`, que solo admite `DCIM`, `Movies` y
 *   `Pictures`. Guardarlos bajo `Documents/` fue el error inicial: quedaban indexados
 *   pero invisibles, porque bajo scoped storage una consulta a `Files` devuelve
 *   únicamente los archivos de medios que creó el propio app.
 *
 * Leer vídeos exige `READ_MEDIA_VIDEO` incluso para los que escribió este mismo app:
 * tras una reinstalación el sistema deja de atribuírselos.
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

    // ---------- Vídeos de ejercicio ----------

    /**
     * Copia un vídeo a `Movies/MASTER/`. Devuelve el nombre **con el que quedó
     * realmente**, o null si falló.
     *
     * No devuelve el nombre pedido: si ya existe un archivo así en el directorio —por
     * ejemplo uno de otro propietario, que este app ni siquiera puede ver—, MediaProvider
     * no sobrescribe, renombra a `nombre (1).mp4`. Guardar el nombre pedido dejaría el
     * vídeo escrito pero imposible de encontrar después.
     */
    fun writeVideo(name: String, source: () -> java.io.InputStream?): String? =
        write(videoCollection(), VIDEOS_PATH, name, "video/mp4") { out ->
            source()?.use { it.copyTo(out) } ?: throw IllegalStateException("sin origen")
        }

    /** Uri reproducible del vídeo, o null si no está o falta el permiso de lectura. */
    fun findVideo(name: String): Uri? =
        if (isAvailable) find(videoCollection(), VIDEOS_PATH, name) else null

    fun deleteVideo(name: String): Boolean {
        val uri = findVideo(name) ?: return false
        return runCatching { appCtx.contentResolver.delete(uri, null, null) > 0 }.getOrDefault(false)
    }

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

    private fun videoCollection() = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    private companion object {
        const val TAG = "SharedFiles"
        val DOCS_PATH = "${Environment.DIRECTORY_DOCUMENTS}/MASTER/"
        val VIDEOS_PATH = "${Environment.DIRECTORY_MOVIES}/MASTER/"
    }
}
