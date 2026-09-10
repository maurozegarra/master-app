package com.maurozegarra.master.data

import android.content.Context
import android.util.Log
import com.maurozegarra.master.model.AssignmentRowsJson
import com.maurozegarra.master.model.AssignedTrainingsJson
import com.maurozegarra.master.model.Profile
import com.maurozegarra.master.model.ProfileDirectoryJson
import com.maurozegarra.master.model.Training
import com.maurozegarra.master.model.TrainingJson
import com.maurozegarra.master.model.forPublishing
import com.maurozegarra.master.net.Http
import com.maurozegarra.master.net.HttpResponse
import com.maurozegarra.master.net.Supabase
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/**
 * Quién usa este dispositivo, qué trainings le tocan y —solo para el entrenador— quién
 * recibe qué.
 *
 * Los demás no tienen cuenta: son personas conocidas, eligen su nombre de una lista y eso
 * solo decide qué se descarga. Quien reparte las rutinas sí inicia sesión, porque escribir
 * exige permiso; leer no.
 *
 * Para el dispositivo que recibe la sincronización es **solo de bajada**: recibe, nunca
 * envía. Lo único que sube es lo que el entrenador reparte a propósito.
 *
 * Antes esto leía JSON estático publicado a mano en GitHub. Se cambió porque asignar desde
 * el teléfono exige escribir, y a un archivo de GitHub no se escribe sin una credencial
 * que no puede vivir dentro de un APK.
 */
class AssignmentRepository(context: Context, private val auth: AuthStore) {

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

    // ---------- Lectura (pública) ----------

    /** Perfiles registrados, o **null si no se pudo leer**, que no es lo mismo que ninguno. */
    fun directory(): List<Profile>? =
        runCatching { ProfileDirectoryJson.decode(get("profiles?select=id,name&order=name")) }
            .onFailure { Log.w(TAG, "no se pudo leer el directorio de perfiles", it) }
            .getOrNull()

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

    /** Quién tiene ya este training. Null si no se pudo leer. */
    fun profilesWith(trainingUid: String): List<String>? =
        runCatching {
            AssignmentRowsJson.profileIds(get("assignments?training_uid=eq.${enc(trainingUid)}&select=profile_id"))
        }
            .onFailure { Log.w(TAG, "no se pudo leer quien tiene $trainingUid", it) }
            .getOrNull()

    /** Cuántos trainings tiene asignados alguien. Null si no se pudo leer. */
    fun assignmentCount(profileId: String): Int? =
        runCatching { AssignmentRowsJson.count(get(assignmentQuery(profileId, select = "training_uid"))) }
            .onFailure { Log.w(TAG, "no se pudo contar lo asignado a $profileId", it) }
            .getOrNull()

    // ---------- Escritura (solo entrenador) ----------

    /**
     * Crea un perfil. Devuelve **null si se creó**, o el motivo si no.
     *
     * El id sale del nombre y no de un contador: es legible en la base de datos y hace que
     * un perfil recreado con el mismo nombre siga siendo el mismo para los teléfonos que
     * ya lo tenían elegido.
     */
    fun createProfile(name: String): String? {
        val clean = name.trim()
        if (clean.isBlank()) return "Name cannot be empty"
        val body = JSONObject().put("id", profileIdFrom(clean)).put("name", clean).toString()
        val res = write("POST", "profiles", body) ?: return NO_CONNECTION
        // 23505 es la clave duplicada de Postgres. Sin traducirlo, el usuario vería un
        // código en vez de saber que ya existe alguien con ese nombre.
        if (res.code == 409 || res.body.contains("23505")) return "There is already a profile named \"$clean\""
        return if (res.ok) null else reasonOf(res)
    }

    fun renameProfile(id: String, name: String): String? {
        val clean = name.trim()
        if (clean.isBlank()) return "Name cannot be empty"
        val res = write("PATCH", "profiles?id=eq.${enc(id)}", JSONObject().put("name", clean).toString())
            ?: return NO_CONNECTION
        return if (res.ok) null else reasonOf(res)
    }

    /** Borra un perfil. Sus asignaciones se van con él: la tabla las tiene en cascada. */
    fun deleteProfile(id: String): String? {
        val res = write("DELETE", "profiles?id=eq.${enc(id)}") ?: return NO_CONNECTION
        return if (res.ok) null else reasonOf(res)
    }

    /**
     * Deja el training en manos exactamente de [profileIds].
     *
     * El payload se sube en cada confirmación, no solo la primera vez: así reasignar es
     * también la forma de publicar los cambios del training. Se sube ANTES de tocar las
     * asignaciones porque estas apuntan a él por clave foránea.
     *
     * Quitar a alguien le retira el training del teléfono en su siguiente sincronización.
     * Es la operación que borra, y por eso quien llama avisa antes de confirmar.
     */
    fun setAssignees(training: Training, profileIds: Set<String>): String? {
        if (training.uid.isBlank()) return "This training has no stable id yet; save it first"

        if (profileIds.isNotEmpty()) {
            val payload = JSONObject()
                .put("uid", training.uid)
                .put("name", training.name)
                // El training viaja tal cual lo serializa el app, que es el formato que el
                // que recibe ya sabe leer. forPublishing() le quita lo que es de quien lo
                // reparte y no del que lo recibe: la insignia de asignado y los vídeos que
                // uno haya apagado para sí mismo.
                .put("payload", TrainingJson.toJson(training.forPublishing()))
                .put("updated_at", java.time.Instant.now().toString())
                .toString()
            val up = write("POST", "trainings", payload, merge = true) ?: return NO_CONNECTION
            if (!up.ok) return reasonOf(up)
        }

        val current = profilesWith(training.uid)?.toSet() ?: return NO_CONNECTION

        (profileIds - current).forEach { id ->
            val body = JSONObject().put("profile_id", id).put("training_uid", training.uid).toString()
            val res = write("POST", "assignments", body, merge = true) ?: return NO_CONNECTION
            if (!res.ok) return reasonOf(res)
        }
        (current - profileIds).forEach { id ->
            val path = "assignments?profile_id=eq.${enc(id)}&training_uid=eq.${enc(training.uid)}"
            val res = write("DELETE", path) ?: return NO_CONNECTION
            if (!res.ok) return reasonOf(res)
        }
        return null
    }

    // ---------- Plomería ----------

    /** Los trainings de alguien en una sola llamada, incrustando el de cada asignación. */
    private fun assignmentQuery(profileId: String, select: String = "trainings(payload)"): String =
        "assignments?profile_id=eq.${enc(profileId)}&select=$select"

    private fun get(path: String): String {
        val res = Http.request("GET", "${Supabase.REST}$path", Supabase.headers())
        if (!res.ok) throw IOException("HTTP ${res.code}: ${res.body}")
        return res.body
    }

    /**
     * Escribe con la sesión del entrenador. Null si no hay sesión válida o falló la red;
     * el código de la respuesta se deja intacto para que quien llama distinga un rechazo
     * del servidor de un problema de conexión.
     */
    private fun write(method: String, path: String, body: String? = null, merge: Boolean = false): HttpResponse? {
        val token = auth.accessToken() ?: return null
        val headers = Supabase.headers(token) + mapOf(
            // return=minimal ahorra devolver la fila entera, que aquí nadie mira; con el
            // payload de un training completo eso es duplicar la subida.
            "Prefer" to if (merge) "resolution=merge-duplicates,return=minimal" else "return=minimal",
        )
        return runCatching { Http.request(method, "${Supabase.REST}$path", headers, body) }
            .onFailure { Log.w(TAG, "$method $path fallo", it) }
            .getOrNull()
    }

    /** Motivo legible de un rechazo de PostgREST, que lo explica en el cuerpo del error. */
    private fun reasonOf(res: HttpResponse): String {
        val msg = runCatching { JSONObject(res.body).optString("message") }.getOrNull()
        return msg?.takeIf { it.isNotBlank() } ?: "HTTP ${res.code}"
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val TAG = "AssignmentRepository"
        const val KEY_PROFILE = "profile_id"
        const val KEY_PROFILE_NAME = "profile_name"
        const val NO_CONNECTION = "No connection, or the coach session expired"
    }
}

/**
 * Id de un perfil a partir de su nombre: `Niko Zegarra` → `niko-zegarra`.
 *
 * Se guarda como texto legible y no como identificador opaco por dos razones: los
 * teléfonos que ya venían de la versión anterior llevan guardado `mauro`, y mirar la
 * tabla en la consola tiene que poder hacerse sin descifrar nada.
 *
 * Un nombre que no deje ninguna letra utilizable —solo emojis, por ejemplo— cae a un id
 * aleatorio: preferible a rechazar el nombre, que es asunto del usuario y no del id.
 */
fun profileIdFrom(name: String): String {
    val ascii = java.text.Normalizer.normalize(name.trim().lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
    val slug = ascii.replace(Regex("[^a-z0-9]+"), "-").trim('-')
    return slug.ifBlank { java.util.UUID.randomUUID().toString() }
}
