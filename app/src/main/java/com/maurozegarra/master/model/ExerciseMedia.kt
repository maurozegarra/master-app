package com.maurozegarra.master.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Material de apoyo de un ejercicio: por ahora, los pasos escritos.
 *
 * Va por `exerciseId` (el id del catálogo) y no por instancia de [Exercise]: se escribe
 * una vez para "Gato-vaca" y sirve en todos los trainings que lo usen.
 *
 * El vídeo **no** vive aquí. Es un archivo en el directorio privado del app, gestionado
 * por `VideoCache`, y su presencia es todo el estado que hay: guardar además su nombre
 * daba dos fuentes de verdad que podían discrepar, y discreparon.
 */
data class ExerciseMedia(
    val instructions: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = instructions.isEmpty()
}

/** Serialización del mapa `exerciseId → media`. Pura, para poder testear el ida y vuelta. */
object ExerciseMediaJson {

    fun encode(media: Map<String, ExerciseMedia>): String {
        val root = JSONObject()
        media.forEach { (id, m) ->
            if (m.isEmpty) return@forEach
            val steps = JSONArray()
            m.instructions.forEach { steps.put(it) }
            root.put(id, JSONObject().put("instructions", steps))
        }
        return root.toString()
    }

    /**
     * Los respaldos viejos traen además un campo `video` con el nombre del archivo en
     * almacenamiento compartido. Se ignora: ese archivo ya no se usa, y si el ejercicio
     * tiene vídeo se sabe por el propio archivo en la caché.
     */
    fun decode(json: String): Map<String, ExerciseMedia> = try {
        val root = JSONObject(json)
        buildMap {
            root.keys().forEach { id ->
                val o = root.getJSONObject(id)
                val steps = o.optJSONArray("instructions")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList()
                val media = ExerciseMedia(instructions = steps)
                if (!media.isEmpty) put(id, media)
            }
        }
    } catch (_: Exception) {
        emptyMap()
    }
}
