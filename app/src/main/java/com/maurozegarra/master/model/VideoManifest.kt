package com.maurozegarra.master.model

import org.json.JSONObject

/** Un vídeo publicado: nombre del archivo, revisión y tamaño esperado. */
data class VideoEntry(val file: String, val rev: Int, val bytes: Long)

/**
 * Catálogo de vídeos disponibles para descargar, por `exerciseId`.
 *
 * [baseUrl] va separado del nombre del archivo a propósito: **mudar el alojamiento es
 * cambiar esa línea del JSON**, no tocar el app. Hoy los vídeos viven en GitHub Releases,
 * junto al APK; el día que sean muchos usuarios se mudan a un almacenamiento pensado para
 * medios sin publicar una versión nueva.
 */
data class VideoManifest(val baseUrl: String, val videos: Map<String, VideoEntry>) {

    fun entry(exerciseId: String): VideoEntry? = videos[exerciseId]

    /** Url completa del vídeo, o null si ese ejercicio no tiene ninguno publicado. */
    fun urlFor(exerciseId: String): String? =
        videos[exerciseId]?.let { "${baseUrl.trimEnd('/')}/${it.file}" }
}

/**
 * Lectura del manifiesto. Pura, para poder probarla desde la suite JVM.
 *
 * No hay `encode`: el manifiesto se guarda tal cual llega, como texto, y se vuelve a
 * leer con [decode]. Un serializador que solo existiera para la caché sería código que
 * mantener sin que nadie lo lea.
 */
object VideoManifestJson {

    const val FORMAT = 1

    /**
     * Devuelve null si el documento no es un manifiesto válido.
     *
     * Una entrada rota se **descarta sola** en vez de invalidar el manifiesto entero, al
     * revés que en `BackupJson`: allí un archivo a medias reemplazaría los datos del
     * usuario, y aquí lo único que pasa es que un ejercicio se queda sin vídeo.
     */
    fun decode(json: String): VideoManifest? {
        val root = try { JSONObject(json) } catch (_: Exception) { return null }
        if (root.optInt("format", 0) !in 1..FORMAT) return null

        val baseUrl = root.optString("baseUrl").takeIf { it.isNotBlank() } ?: return null
        val videos = root.optJSONObject("videos") ?: return null

        val out = mutableMapOf<String, VideoEntry>()
        videos.keys().forEach { id ->
            val o = videos.optJSONObject(id) ?: return@forEach
            val file = o.optString("file").takeIf { it.isNotBlank() } ?: return@forEach
            out[id] = VideoEntry(
                file = file,
                // Sin rev, revisión 1: un manifiesto escrito a mano no debería obligar a
                // poner el campo hasta que haga falta reemplazar un vídeo.
                rev = o.optInt("rev", 1),
                // Sin bytes no se valida el tamaño; se pierde la detección de descargas
                // cortadas, no la descarga.
                bytes = o.optLong("bytes", 0L),
            )
        }
        return VideoManifest(baseUrl = baseUrl, videos = out)
    }
}
