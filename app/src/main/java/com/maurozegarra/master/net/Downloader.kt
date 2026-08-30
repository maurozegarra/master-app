package com.maurozegarra.master.net

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Descarga un archivo a disco con progreso.
 *
 * Se escribe en `<destino>.part` y se renombra solo al terminar bien. Sin eso, cortar la
 * red a mitad deja un archivo truncado que en disco parece bueno: el reproductor lo
 * abriría y fallaría, y el instalador tomaría por válido un APK incompleto.
 *
 * `HttpURLConnection` basta para esto, así que el app no arrastra una librería de red.
 */
object Downloader {

    /**
     * Descarga [url] en [target], creando los directorios que falten. Lanza [IOException]
     * si algo va mal; el `.part` se borra siempre antes de propagar.
     *
     * [expectedBytes] mayor que cero valida el tamaño final: un servidor puede cortar la
     * respuesta sin devolver error, y entonces lo único que delata el archivo incompleto
     * es que mide menos de la cuenta. También da progreso cuando la respuesta no trae
     * `Content-Length`.
     */
    fun download(
        url: String,
        target: File,
        expectedBytes: Long = 0L,
        onProgress: (Float) -> Unit = {},
    ) {
        target.parentFile?.mkdirs()
        val part = File(target.parentFile, "${target.name}.part")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code al descargar $url")

            val total = if (expectedBytes > 0L) expectedBytes else conn.contentLengthLong
            var written = 0L
            conn.inputStream.use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        written += read
                        if (total > 0L) onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            if (expectedBytes > 0L && written != expectedBytes) {
                throw IOException("$url: se esperaban $expectedBytes bytes y llegaron $written")
            }

            target.delete()
            if (!part.renameTo(target)) throw IOException("no se pudo mover ${part.name} a ${target.name}")
            onProgress(1f)
        } catch (e: Exception) {
            part.delete()
            throw e
        } finally {
            conn.disconnect()
        }
    }
}
