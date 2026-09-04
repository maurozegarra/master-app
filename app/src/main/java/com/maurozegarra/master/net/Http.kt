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
