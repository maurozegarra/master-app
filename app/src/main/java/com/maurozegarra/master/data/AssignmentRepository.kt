package com.maurozegarra.master.data

import android.content.Context
import android.util.Log
import com.maurozegarra.master.model.AssignedTrainingsJson
import com.maurozegarra.master.model.Profile
import com.maurozegarra.master.model.ProfileDirectoryJson
import com.maurozegarra.master.model.Training
import com.maurozegarra.master.net.Http
import com.maurozegarra.master.net.Supabase
import java.io.IOException
import java.net.URLEncoder

/**
 * Quién usa este dispositivo y qué trainings le tocan.
 *
 * Los demás no tienen cuenta: son personas conocidas, eligen su nombre de una lista y eso
 * solo decide qué se descarga. Quien reparte las rutinas sí inicia sesión, porque escribir
 * exige permiso; leer no.
 *
 * Sincronización **solo de bajada**: el dispositivo recibe, nunca envía. Es mucho más
 * simple que un sync bidireccional y cubre el caso real, que es repartir rutinas.
 *
 * Antes esto leía JSON estático publicado a mano en GitHub. Se cambió porque asignar desde
 * el teléfono exige escribir, y a un archivo de GitHub no se escribe sin una credencial
 * que no puede vivir dentro de un APK.
 */
class AssignmentRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("master", Context.MODE_PRIVATE)

    /** Perfil elegido en este dispositivo, o null si aún no se ha elegido ninguno. */
    var profileId: String?
        get() = prefs.getString(KEY_PROFILE, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().putString(KEY_PROFILE, value.orEmpty()).apply()
        }

    /** Nombre del perfil elegido, para poder enseñarlo sin volver a la red. */
    var profileName: String
        get() = prefs.getString(KEY_PROFILE_NAME, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_PROFILE_NAME, value).apply()
        }

    /** Perfiles registrados. Lista vacía si no hay red o la respuesta no es válida. */
    fun directory(): List<Profile> =
        runCatching { ProfileDirectoryJson.decode(get("profiles?select=id,name&order=name")) }
            .onFailure { Log.w(TAG, "no se pudo leer el directorio de perfiles", it) }
            .getOrNull()
            .orEmpty()

    /**
     * Trainings asignados al perfil, o **null si no se pudo saber**.
     *
     * Null y lista vacía significan cosas opuestas: vacía retira las asignaciones del
     * dispositivo, así que un fallo de red jamás puede parecerse a eso. De ahí que [get]
     * lance ante cualquier código que no sea 2xx en vez de devolver el cuerpo del error,
     * que se decodificaría como cero trainings.
     */
    fun assignedTrainings(profileId: String): List<Training>? =
        runCatching { AssignedTrainingsJson.decode(get(assignmentQuery(profileId))) }
            .onFailure { Log.w(TAG, "no se pudo leer la asignacion de $profileId", it) }
            .getOrNull()

    /** Los trainings de alguien en una sola llamada, incrustando el de cada asignación. */
    private fun assignmentQuery(profileId: String): String {
        val id = URLEncoder.encode(profileId, "UTF-8")
        return "assignments?profile_id=eq.$id&select=trainings(payload)"
    }

    private fun get(path: String): String {
        val res = Http.request("GET", "${Supabase.REST}$path", Supabase.headers())
        if (!res.ok) throw IOException("HTTP ${res.code}: ${res.body}")
        return res.body
    }

    private companion object {
        const val TAG = "AssignmentRepository"
        const val KEY_PROFILE = "profile_id"
        const val KEY_PROFILE_NAME = "profile_name"
    }
}
