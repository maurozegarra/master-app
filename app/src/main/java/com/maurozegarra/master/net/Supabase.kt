package com.maurozegarra.master.net

/**
 * Dónde vive el directorio de perfiles y los trainings asignados.
 *
 * La clave es la **publicable**, y va dentro del APK a propósito: está diseñada para eso.
 * Lo que impide que alguien escriba con ella no es esconderla, sino las políticas del
 * servidor, que solo dan lectura a quien no ha iniciado sesión. Comprobado: un intento de
 * escritura con esta clave responde `401 · new row violates row-level security policy`.
 *
 * La clave que sí es secreta —`service_role`— no está aquí ni puede estarlo: se salta esas
 * políticas.
 */
object Supabase {

    const val URL = "https://oabmpajclddgcrvwjvsj.supabase.co"

    /** Clave publicable. Solo lee. */
    const val ANON_KEY = "sb_publishable_nT4Stw_Pq4IJ4tU4cdA6VQ_fU82xFAN"

    const val REST = "$URL/rest/v1/"

    /**
     * Cabeceras de PostgREST. Sin sesión se manda la clave publicable también como
     * `Authorization`, que es como la API identifica al rol anónimo.
     */
    fun headers(accessToken: String? = null): Map<String, String> = mapOf(
        "apikey" to ANON_KEY,
        "Authorization" to "Bearer ${accessToken ?: ANON_KEY}",
    )
}
