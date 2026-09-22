package com.maurozegarra.master.model

/**
 * Qué vídeos no quiere ver quien usa ESTE teléfono (TD-154).
 *
 * **Vive fuera del training**, igual que el archivado (TD-138), y por las mismas dos
 * razones. Las rutinas del coach se reemplazan enteras al subir su revisión: con la marca
 * dentro, el 22-sep el usuario apagaba un vídeo y volvía a salir en la revisión siguiente.
 * Y un training asignado no se toca, porque la siguiente sincronización lo devolvería a
 * como estaba: NIKO no tenía ni el botón. Mirar o no un vídeo es de quien lo mira, no de
 * quien diseña la rutina, así que tampoco viaja al asignar.
 *
 * Se guardan los APAGADOS, por uid del training y movimiento. El uid es lo que sobrevive a
 * una revisión; el id del ejercicio no. El movimiento y no la posición: la caminata de
 * entrada y la de salida son el mismo vídeo, y apagar uno las apaga a las dos.
 *
 * [Exercise.showVideo] sigue existiendo, pero solo como lo que se aplica al pintar y como
 * lo que traían los trainings de antes -ver [migrate]-.
 */
object VideoPrefs {

    fun key(trainingUid: String, exerciseId: String): String = "$trainingUid|$exerciseId"

    /**
     * El training con cada [Exercise.showVideo] puesto según [hidden].
     *
     * Sin uid se deja como está: no hay con qué recordar la preferencia entre revisiones, y
     * lo único que se tiene es lo que el training trae.
     */
    fun apply(training: Training, hidden: Set<String>): Training {
        if (training.uid.isBlank()) return training
        fun ex(list: List<Exercise>) = list.map {
            val ver = key(training.uid, it.exerciseId) !in hidden
            if (it.showVideo == ver) it else it.copy(showVideo = ver)
        }
        return training.copy(
            workouts = training.workouts.map { w ->
                w.copy(exercises = ex(w.exercises), variants = w.variants.map { v -> v.copy(exercises = ex(v.exercises)) })
            },
        )
    }

    /** [hidden] con el vídeo de [exerciseId] en [training] apagado o encendido. */
    fun set(hidden: Set<String>, training: Training, exerciseId: String, show: Boolean): Set<String> {
        if (training.uid.isBlank()) return hidden
        val k = key(training.uid, exerciseId)
        return if (show) hidden - k else hidden + k
    }

    /**
     * Lo que el editor dejó en el borrador, pasado a [hidden]: el interruptor "Show it in
     * this training" escribe en el ejercicio, y al guardar se traslada aquí.
     */
    fun fromTraining(hidden: Set<String>, training: Training): Set<String> {
        if (training.uid.isBlank()) return hidden
        val todos = training.workouts.flatMap { w -> w.exercises + w.variants.flatMap { it.exercises } }
        var out = hidden
        todos.map { it.exerciseId }.distinct().forEach { id ->
            // Si aparece dos veces y una quedó apagada, manda el apagado: es lo que se tocó.
            val ver = todos.filter { it.exerciseId == id }.all { it.showVideo }
            out = set(out, training, id, ver)
        }
        return out
    }

    /**
     * Los vídeos que ya estaban apagados dentro de los trainings, para arrancar el conjunto
     * la primera vez. Sin esto, instalar la versión nueva encendería todo lo que alguien
     * ya había apagado a mano.
     */
    fun migrate(trainings: List<Training>): Set<String> =
        trainings.filter { it.uid.isNotBlank() }.flatMap { t ->
            (t.workouts.flatMap { w -> w.exercises + w.variants.flatMap { it.exercises } })
                .filter { !it.showVideo }
                .map { key(t.uid, it.exerciseId) }
        }.toSet()
}
