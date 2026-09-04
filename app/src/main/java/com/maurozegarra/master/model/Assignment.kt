package com.maurozegarra.master.model

import org.json.JSONArray

/** Una persona que puede recibir trainings. */
data class Profile(val id: String, val name: String)

/**
 * Directorio de perfiles: quién puede recibir trainings.
 *
 * El dispositivo lo lee para ofrecer la lista y que su dueño diga cuál es. Los demás no
 * inician sesión —solo lo hace quien asigna—, así que aquí la identidad no autentica
 * nada: únicamente decide qué trainings se descargan.
 *
 * Lo que llega es la respuesta de la tabla `profiles`, o sea un array de filas.
 */
object ProfileDirectoryJson {

    /** Null si la respuesta no es una lista de filas; una fila sin `id` se ignora. */
    fun decode(json: String): List<Profile>? {
        val arr = try { JSONArray(json) } catch (_: Exception) { return null }
        return (0 until arr.length()).mapNotNull { i ->
            val row = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = row.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Profile(id = id, name = row.optString("name").ifBlank { id })
        }
    }
}

/** Trainings asignados a un perfil. */
object AssignedTrainingsJson {

    /**
     * Decodifica las filas de `assignments` con su training incrustado, tal y como las
     * devuelve `?select=trainings(payload)`:
     *
     * ```
     * [ { "trainings": { "payload": { ...training... } } } ]
     * ```
     *
     * Devuelve null si la respuesta no es válida, y **null no es lista vacía**: quien
     * llame no debe confundir "no se pudo leer" con "ya no te toca ninguno", porque lo
     * segundo borra trainings del dispositivo. Por eso una fila sin payload legible
     * invalida la respuesta entera en vez de saltarse esa fila: saltársela se vería,
     * desde fuera, igual que una desasignación.
     */
    fun decode(json: String): List<Training>? {
        val arr = try { JSONArray(json) } catch (_: Exception) { return null }
        val payloads = JSONArray()
        for (i in 0 until arr.length()) {
            val payload = arr.optJSONObject(i)
                ?.optJSONObject("trainings")
                ?.optJSONObject("payload")
                ?: return null
            payloads.put(payload)
        }
        val trainings = TrainingJson.decode(payloads.toString())
        // decode() devuelve lista vacía ante JSON corrupto: distinguirlo de una asignación
        // legítimamente vacía evita tomar un fallo de parseo por una desasignación.
        if (trainings.isEmpty() && payloads.length() > 0) return null
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
