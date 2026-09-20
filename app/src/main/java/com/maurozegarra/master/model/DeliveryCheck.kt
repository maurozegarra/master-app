package com.maurozegarra.master.model

/** Un ejercicio del training que llegaría sin algo (TD-143). */
data class MissingContent(
    val exerciseId: String,
    val name: String,
    val noInstructions: Boolean,
    val noVideo: Boolean,
)

/**
 * Qué le va a llegar incompleto a quien reciba un training (TD-143).
 *
 * Repartir una rutina parecía un solo acto y no lo era: el training viaja, pero las
 * instrucciones y el vídeo son de cada `exerciseId` y pueden no existir. Cuando faltan, el
 * fallo es **silencioso**: quien la recibe abre el ejercicio y ve un nombre, nada más, y
 * quien la repartió no se entera nunca.
 *
 * Pasó de verdad el 19-sep-2026: el día de Muay Thai de NIKO se asignó con los seis
 * ejercicios del calentamiento sin una sola línea de cómo hacerlos, y se descubrió por
 * casualidad, revisando otra cosa, la víspera de que ella lo entrenara.
 *
 * **El vídeo propio no cuenta.** Vive en el directorio privado del teléfono que lo asignó y
 * no viaja con la rutina, así que aquí solo vale el publicado: es lo único que el otro
 * teléfono puede descargar.
 */
object DeliveryCheck {

    fun gaps(
        training: Training,
        media: Map<String, ExerciseMedia>,
        publishedVideos: Set<String>,
    ): List<MissingContent> {
        val vistos = LinkedHashMap<String, MissingContent>()
        training.workouts
            .flatMap { w -> w.exercises + w.variants.flatMap { it.exercises } }
            .forEach { e ->
                val id = e.exerciseId
                if (id.isBlank() || id in vistos) return@forEach
                val sinPasos = media[id]?.instructions.isNullOrEmpty()
                val sinVideo = id !in publishedVideos
                if (sinPasos || sinVideo) {
                    vistos[id] = MissingContent(id, e.name, sinPasos, sinVideo)
                }
            }
        // Primero lo que no tiene ni instrucciones: un ejercicio sin vídeo se puede leer,
        // uno sin instrucciones y sin vídeo no se puede hacer.
        return vistos.values.sortedByDescending { it.noInstructions }
    }
}
