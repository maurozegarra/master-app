package com.maurozegarra.master.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Material de apoyo de un ejercicio: el vídeo que muestra cómo se ejecuta y los pasos
 * escritos.
 *
 * Va por `exerciseId` (el id del catálogo) y no por instancia de [Exercise]: se asigna
 * una vez a "Gato-vaca" y sirve en todos los trainings que lo usen.
 *
 * [videoFile] es solo el nombre del archivo dentro de `Movies/MASTER/`, no una
 * ruta ni una URI: las URIs de SAF caducan y las rutas absolutas cambian entre
 * dispositivos, y esto viaja en el respaldo.
 */
data class ExerciseMedia(
    val videoFile: String = "",
    val instructions: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = videoFile.isBlank() && instructions.isEmpty()
}

/** Serialización del mapa `exerciseId → media`. Pura, para poder testear el ida y vuelta. */
object ExerciseMediaJson {

    fun encode(media: Map<String, ExerciseMedia>): String {
        val root = JSONObject()
        media.forEach { (id, m) ->
            if (m.isEmpty) return@forEach
            val steps = JSONArray()
            m.instructions.forEach { steps.put(it) }
            root.put(
                id,
                JSONObject()
                    .put("video", m.videoFile)
                    .put("instructions", steps),
            )
        }
        return root.toString()
    }

    fun decode(json: String): Map<String, ExerciseMedia> = try {
        val root = JSONObject(json)
        buildMap {
            root.keys().forEach { id ->
                val o = root.getJSONObject(id)
                val steps = o.optJSONArray("instructions")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList()
                val media = ExerciseMedia(videoFile = o.optString("video", ""), instructions = steps)
                if (!media.isEmpty) put(id, media)
            }
        }
    } catch (_: Exception) {
        emptyMap()
    }
}
