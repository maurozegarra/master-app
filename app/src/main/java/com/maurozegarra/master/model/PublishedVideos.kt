package com.maurozegarra.master.model

import org.json.JSONArray

/**
 * Un vídeo publicado: nombre del archivo en la caché, revisión, tamaño esperado y de dónde
 * se baja.
 *
 * [bytes] permite detectar una descarga cortada. [url] es entera porque el alojamiento lo
 * decide quien publica, no el app.
 */
data class VideoEntry(val file: String, val rev: Int, val bytes: Long, val url: String)

/**
 * Los vídeos publicados, leídos de la tabla `exercise_media` (TD-141).
 *
 * **Antes esto salía de `videos.json`**, un archivo en la raíz del repo que se editaba a
 * mano y se leía de `raw.githubusercontent.com`. Publicar un vídeo exigía PC, el CLI de
 * `gh` y un commit; desde que se puede publicar desde el teléfono (TD-140) ese camino solo
 * servía para tener dos sitios donde mirar cuando algo no aparecía. El release `videos` de
 * GitHub se queda como respaldo frío de lo que hubo.
 *
 * Es puro y separado del repositorio para poder probarlo desde la suite JVM: una fila rota
 * no puede dejar sin vídeo a los demás ejercicios.
 */
object PublishedVideos {

    /**
     * Las filas del servidor, por `exerciseId`. Una fila sin revisión es un ejercicio que
     * tiene instrucciones pero todavía no tiene vídeo, que es el caso de casi todos.
     */
    fun parse(json: String, baseUrl: String): Map<String, VideoEntry> = runCatching {
        val rows = JSONArray(json)
        buildMap {
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                val id = row.optString("exercise_id", "")
                val rev = row.optInt("video_rev", 0)
                if (id.isBlank() || rev <= 0) continue
                val file = "$id.$rev.mp4"
                put(id, VideoEntry(file = file, rev = rev, bytes = row.optLong("video_bytes", 0L), url = baseUrl + file))
            }
        }
    }.getOrDefault(emptyMap())
}
