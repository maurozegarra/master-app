package com.maurozegarra.master.model

import org.json.JSONObject

/** Una persona que puede recibir trainings. */
data class Profile(val id: String, val name: String)

/**
 * Directorio de perfiles publicados: quién puede recibir trainings.
 *
 * El dispositivo lo lee para ofrecer la lista y que su dueño diga cuál es. No hay cuentas
 * ni contraseñas: son cuatro personas conocidas y la identidad solo decide qué archivo de
 * trainings se descarga.
 */
object ProfileDirectoryJson {

    const val FORMAT = 1

    fun decode(json: String): List<Profile>? {
        val root = try { JSONObject(json) } catch (_: Exception) { return null }
        if (root.optInt("format", 0) !in 1..FORMAT) return null
        val arr = root.optJSONArray("users") ?: return null
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Profile(id = id, name = o.optString("name").ifBlank { id })
        }
    }
}

/** Trainings asignados a un perfil. */
object AssignedTrainingsJson {

    const val FORMAT = 1

    /**
     * Devuelve null si el documento no es válido, y **null no es lista vacía**: quien
     * llame no debe confundir "no se pudo leer" con "ya no te toca ninguno", porque lo
     * segundo borra trainings del dispositivo.
     */
    fun decode(json: String): List<Training>? {
        val root = try { JSONObject(json) } catch (_: Exception) { return null }
        if (root.optInt("format", 0) !in 1..FORMAT) return null
        val arr = root.optJSONArray("trainings") ?: return null
        val trainings = TrainingJson.decode(arr.toString())
        // decode() devuelve lista vacía ante JSON corrupto: distinguirlo de un archivo
        // legítimamente vacío evita tomar un fallo de parseo por una desasignación.
        if (trainings.isEmpty() && arr.length() > 0) return null
        return trainings
    }
}

/**
 * Aplica los trainings asignados sobre los que ya hay en el dispositivo.
 *
 * Reglas, y cada una está aquí porque su contraria pierde datos:
 *
 * - **Los trainings propios no se tocan.** Nunca. Recibir una asignación no puede
 *   costarle a nadie lo que se creó por su cuenta.
 * - **El emparejamiento va por [Training.uid]**, que es estable entre dispositivos, pero
 *   **el `id` local se conserva**. `SessionLog.trainingId` apunta a ese `id`: darle uno
 *   nuevo en cada sincronización desengancharía el historial del training y rompería el
 *   orden por último entrenamiento.
 * - **Lo que ya no está asignado se retira**, pero solo si llegó asignado. Un training
 *   propio con el mismo nombre no se ve afectado.
 * - **Los nuevos se añaden al final**, sin reordenar lo que el usuario ya tenía colocado.
 */
fun mergeAssigned(
    local: List<Training>,
    incoming: List<Training>,
    newId: () -> Long,
): List<Training> {
    val byUid = incoming.filter { it.uid.isNotBlank() }.associateBy { it.uid }
    val seen = mutableSetOf<String>()

    val kept = local.mapNotNull { current ->
        if (!current.assigned) return@mapNotNull current
        val fresh = byUid[current.uid] ?: return@mapNotNull null
        seen += current.uid
        fresh.copy(id = current.id, assigned = true)
    }

    val added = incoming
        .filter { it.uid.isNotBlank() && it.uid !in seen }
        .map { it.copy(id = newId(), assigned = true) }

    return kept + added
}
