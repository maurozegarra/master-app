package com.maurozegarra.master.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Qué instrucciones suben y cuáles bajan (TD-139).
 *
 * Hasta aquí, las instrucciones de un ejercicio llegaban **dentro del APK**: se sembraban por
 * revisión y vivían en cada teléfono. Eso ataba el contenido a publicar una versión, y dejaba
 * a un atleta con un ejercicio nuevo y sin una sola línea de cómo hacerlo hasta que
 * actualizara. La unidad real de contenido es el `exerciseId`, no el training ni el APK.
 *
 * **La dirección la marca quién es coach.** El teléfono del entrenador es el autor: publica lo
 * suyo. El del atleta recibe: aplica lo que hay. Nadie fusiona en las dos direcciones, que es
 * de donde salen los conflictos que nadie sabe explicar.
 *
 * Lo escrito a mano en el teléfono que recibe **no se pisa**, y para saber qué es "escrito a
 * mano" se usan dos pistas: el [ledger] —lo que este teléfono aplicó la última vez— y las
 * versiones sembradas desde el código, que son reemplazables por definición. Lo que no
 * coincide con ninguna de las dos lo escribió alguien, y se queda.
 */
object MediaSync {

    /** Huella del contenido, para saber si cambió sin comparar textos por ahí. */
    fun fingerprintOf(media: ExerciseMedia): Int = media.instructions.joinToString("\u0000").hashCode()

    /**
     * Lo que el teléfono del coach tiene que publicar: lo que no coincide con la huella de lo
     * último que mandó. Lo vacío no se publica: borrar contenido de todos los teléfonos no
     * puede ser el efecto secundario de vaciar un campo.
     */
    fun toPublish(
        local: Map<String, ExerciseMedia>,
        ledger: Map<String, Int>,
    ): List<Pair<String, ExerciseMedia>> =
        local.entries
            .filter { (id, m) -> !m.isEmpty && ledger[id] != fingerprintOf(m) }
            .sortedBy { it.key }
            .map { it.key to it.value }

    /**
     * Lo que el teléfono del coach publica de cada ejercicio: lo suyo, salvo lo que es
     * PERSONAL del coach.
     *
     * Las instrucciones del lumbar ([personal]) están en inglés y hablan de SU espalda y SU
     * banca, y en un teléfono hay una sola versión por ejercicio: el puente, el hip thrust y la
     * caminata lateral de NIKO llegaron a su teléfono con las de él, en un idioma que ella no
     * lee (encontrado el 1-oct). Donde lo local es la versión personal, se publica la del
     * [catalog]; si el catálogo no tiene, no se publica nada.
     */
    fun publicVersion(
        local: Map<String, ExerciseMedia>,
        personal: Map<String, ExerciseMedia>,
        catalog: Map<String, ExerciseMedia>,
    ): Map<String, ExerciseMedia> = local.mapNotNull { (id, m) ->
        when {
            personal[id] != m -> id to m
            else -> catalog[id]?.let { id to it }
        }
    }.toMap()

    /**
     * Lo que el teléfono de un atleta tiene que aplicar de lo que bajó.
     *
     * Entra cuando no hay nada local, cuando lo local es exactamente lo que se aplicó la vez
     * anterior ([ledger]), o cuando es una de las versiones [seeded] que el código sembró. Si
     * no es ninguna de esas, lo escribió quien tiene el teléfono delante y se respeta.
     */
    fun toApply(
        remote: Map<String, ExerciseMedia>,
        local: Map<String, ExerciseMedia>,
        ledger: Map<String, Int>,
        seeded: Map<String, List<ExerciseMedia>>,
    ): Map<String, ExerciseMedia> = remote.filter { (id, r) ->
        val l = local[id]
        when {
            l == null || l.isEmpty -> true
            l == r -> false
            fingerprintOf(l) == ledger[id] -> true
            l in seeded[id].orEmpty() -> true
            else -> false
        }
    }

    /** Las filas del servidor. **Null es "no se pudo leer"**, que no es una tabla vacía. */
    fun parseRows(json: String): Map<String, ExerciseMedia>? = runCatching {
        val rows = JSONArray(json)
        buildMap {
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                val id = row.optString("exercise_id", "")
                if (id.isBlank()) continue
                val steps = row.optJSONArray("instructions") ?: JSONArray()
                put(id, ExerciseMedia(instructions = (0 until steps.length()).map { steps.getString(it) }))
            }
        }
    }.getOrNull()

    /** El cuerpo de una fila, para publicarla. */
    fun rowOf(exerciseId: String, media: ExerciseMedia): String = JSONObject()
        .put("exercise_id", exerciseId)
        .put("instructions", JSONArray(media.instructions))
        .put("updated_at", java.time.Instant.now().toString())
        .toString()
}
