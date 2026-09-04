package com.maurozegarra.master.data

import android.content.Context
import android.util.Log
import com.maurozegarra.master.net.Http
import com.maurozegarra.master.net.Supabase
import org.json.JSONObject

/**
 * La sesión de quien reparte rutinas.
 *
 * Solo el entrenador inicia sesión. Los demás no tienen cuenta: eligen su nombre de una
 * lista y eso únicamente decide qué se descarga. Leer es público; **escribir exige esta
 * sesión**, y quien la comprueba es el servidor con sus políticas, no el app.
 *
 * Los tokens viven en sus propias preferencias, `master_auth`, y no en las del app. Así
 * se pueden excluir del respaldo automático sin arrastrar con ellas los trainings: un
 * refresh token copiado a otro teléfono es permiso de escritura sobre todo el mundo.
 *
 * Todo lo de aquí va a la red. Llamarlo desde un hilo de IO.
 */
class AuthStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("master_auth", Context.MODE_PRIVATE)

    /** Si este dispositivo tiene sesión de entrenador. No garantiza que siga siendo válida. */
    val isCoach: Boolean get() = prefs.getString(KEY_REFRESH, null)?.isNotBlank() == true

    /** Correo con el que se entró, para poder enseñarlo sin volver a la red. */
    val email: String get() = prefs.getString(KEY_EMAIL, "").orEmpty()

    /**
     * Entra con correo y contraseña. Devuelve **null si entró**, o el motivo si no.
     *
     * Se devuelve el mensaje del servidor porque los dos fallos posibles piden acciones
     * distintas —contraseña mal escrita frente a cuenta que no existe— y un "no se pudo"
     * genérico los haría indistinguibles.
     */
    fun signIn(email: String, password: String): String? {
        val body = JSONObject()
            .put("email", email.trim())
            .put("password", password)
            .toString()
        val res = runCatching {
            Http.request("POST", "${Supabase.AUTH}token?grant_type=password", Supabase.headers(), body)
        }.getOrElse {
            Log.w(TAG, "no se pudo entrar", it)
            return NO_CONNECTION
        }
        if (!res.ok) return errorOf(res.body)
        if (!save(res.body)) return NO_CONNECTION
        prefs.edit().putString(KEY_EMAIL, email.trim()).apply()
        return null
    }

    /** Cierra la sesión en este dispositivo. Lo asignado sigue asignado: esto solo quita el permiso. */
    fun signOut() {
        prefs.edit().clear().apply()
    }

    /**
     * Un access token válido, renovándolo si está por vencer. Null si no hay sesión o si
     * ya no vale.
     *
     * Se renueva un minuto ANTES de la hora de caducidad: pedirlo justo en el límite deja
     * la ventana en la que el token viaja ya vencido y la escritura se rechaza sin motivo
     * aparente.
     */
    fun accessToken(): String? {
        val refresh = prefs.getString(KEY_REFRESH, null)?.takeIf { it.isNotBlank() } ?: return null
        val token = prefs.getString(KEY_ACCESS, null)
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        if (!token.isNullOrBlank() && System.currentTimeMillis() < expiresAt - SKEW_MS) return token

        val body = JSONObject().put("refresh_token", refresh).toString()
        val res = runCatching {
            Http.request("POST", "${Supabase.AUTH}token?grant_type=refresh_token", Supabase.headers(), body)
        }.getOrElse {
            Log.w(TAG, "no se pudo renovar la sesion", it)
            // Sin red no se puede saber si la sesión sigue viva. Se conserva: borrarla
            // obligaría a volver a entrar cada vez que se abre el app fuera de cobertura.
            return null
        }
        if (!res.ok) {
            // El servidor SÍ contestó, y dijo que no. El refresh token ya no sirve, así
            // que guardarlo solo produciría el mismo rechazo en cada intento.
            Log.w(TAG, "sesion rechazada (${res.code}), se cierra")
            signOut()
            return null
        }
        return if (save(res.body)) prefs.getString(KEY_ACCESS, null) else null
    }

    /** Guarda la pareja de tokens. False si la respuesta no traía lo imprescindible. */
    private fun save(json: String): Boolean {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return false
        val access = o.optString("access_token").takeIf { it.isNotBlank() } ?: return false
        val refresh = o.optString("refresh_token").takeIf { it.isNotBlank() } ?: return false
        // expires_in es una duración en segundos; se guarda ya convertida a instante para
        // no tener que acordarse de cuándo llegó.
        val expiresIn = o.optLong("expires_in", 3600L)
        prefs.edit()
            .putString(KEY_ACCESS, access)
            .putString(KEY_REFRESH, refresh)
            .putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + expiresIn * 1000L)
            .apply()
        return true
    }

    private companion object {
        const val TAG = "AuthStore"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_EXPIRES_AT = "expires_at"
        const val KEY_EMAIL = "email"
        const val SKEW_MS = 60_000L
        const val NO_CONNECTION = "No connection"
    }
}

/**
 * Motivo legible de un rechazo de GoTrue, que lo pone en `error_description` o en `msg`
 * según el error. Si no trae ninguno, el cuerpo entero es mejor que un texto inventado.
 */
internal fun errorOf(body: String): String {
    val o = runCatching { JSONObject(body) }.getOrNull() ?: return body.ifBlank { "Sign in failed" }
    val msg = listOf("error_description", "msg", "message", "error")
        .firstNotNullOfOrNull { key -> o.optString(key).takeIf { it.isNotBlank() } }
    return msg ?: body
}
