package com.maurozegarra.master.data

import android.content.Context
import android.util.Log
import com.maurozegarra.master.model.AssignedTrainingsJson
import com.maurozegarra.master.model.Profile
import com.maurozegarra.master.model.ProfileDirectoryJson
import com.maurozegarra.master.model.Training
import com.maurozegarra.master.net.Downloader

/**
 * Quién usa este dispositivo y qué trainings le tocan.
 *
 * No hay cuentas ni contraseñas: son personas conocidas, y la identidad solo decide qué
 * archivo se descarga. Los trainings se publican como JSON estático, igual que el
 * manifiesto de vídeos de TD-062 y con la misma maquinaria.
 *
 * Sincronización **solo de bajada**: el dispositivo recibe, nunca envía. Es mucho más
 * simple que un sync bidireccional y cubre el caso real, que es repartir rutinas.
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

    /** Perfiles publicados. Lista vacía si no hay red o el directorio no es válido. */
    fun directory(): List<Profile> =
        runCatching { ProfileDirectoryJson.decode(Downloader.fetchText(DIRECTORY_URL)) }
            .onFailure { Log.w(TAG, "no se pudo leer el directorio de perfiles", it) }
            .getOrNull()
            .orEmpty()

    /**
     * Trainings asignados al perfil, o **null si no se pudo saber**.
     *
     * Null y lista vacía significan cosas opuestas: vacía retira las asignaciones del
     * dispositivo, así que un fallo de red jamás puede parecerse a eso.
     */
    fun assignedTrainings(profileId: String): List<Training>? =
        runCatching { AssignedTrainingsJson.decode(Downloader.fetchText(assignmentUrl(profileId))) }
            .onFailure { Log.w(TAG, "no se pudo leer la asignacion de $profileId", it) }
            .getOrNull()

    private fun assignmentUrl(profileId: String) = "${BASE_URL}users/$profileId.json"

    private companion object {
        const val TAG = "AssignmentRepository"
        const val KEY_PROFILE = "profile_id"
        const val KEY_PROFILE_NAME = "profile_name"

        // Mismo repo y misma mecanica que videos.json (TD-062). Un solo sitio: el dia que
        // esto pase por un backend con auth, cambia aqui.
        const val BASE_URL = "https://raw.githubusercontent.com/maurozegarra/master-app/main/"
        const val DIRECTORY_URL = "${BASE_URL}users.json"
    }
}
