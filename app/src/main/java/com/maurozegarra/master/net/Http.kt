package com.maurozegarra.master.net

import java.net.HttpURLConnection
import java.net.URL

/** Respuesta cruda: el cuerpo llega sin interpretar, y [code] decide si vale. */
data class HttpResponse(val code: Int, val body: String) {
    val ok: Boolean get() = code in 200..299
}

/**
 * Una petición HTTP con método, cabeceras y cuerpo.
 *
 * `HttpURLConnection` basta, así que el app sigue sin arrastrar una librería de red pese
 * a hablar ahora con una API REST. [Downloader] tiene el otro caso —bajar bytes a disco
 * con progreso— y se apoya en esto para los documentos de texto.
 *
 * Ante un error se devuelve la respuesta igual, con su código y el cuerpo del error: una
 * API REST explica en el cuerpo qué rechazó, y perder eso deja los fallos sin diagnóstico.
 */
object Http {

    /**
     * Sube un archivo tal cual, sin envolverlo en nada (TD-140).
     *
     * `setFixedLengthStreamingMode` es lo que hace que no se cargue el mp4 entero en
     * memoria: son megas, y un telefono con el app en segundo plano no tiene por que
     * aguantarlos. El timeout es largo a proposito: subir 3 MB por datos moviles no es lo
     * mismo que pedir una fila.
     */
    fun upload(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        file: java.io.File,
        contentType: String,
        timeoutMs: Int = 120_000,
    ): HttpResponse {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            useCaches = false
            doOutput = true
            setFixedLengthStreamingMode(file.length())
            setRequestProperty("Content-Type", contentType)
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        return try {
            conn.outputStream.use { out -> file.inputStream().use { it.copyTo(out) } }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            HttpResponse(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            conn.disconnect()
        }
    }

    fun request(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        timeoutMs: Int = 10_000,
    ): HttpResponse {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            useCaches = false
            setRequestProperty("Cache-Control", "no-cache")
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        return try {
            if (body != null) conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            HttpResponse(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            conn.disconnect()
        }
    }
}
