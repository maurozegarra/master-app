package com.maurozegarra.master.model

/**
 * Qué se ve en la lista y qué queda guardado (TD-138).
 *
 * La lista acumula tres cosas que estorban sin ser desechables: la variante de día malo,
 * que solo se usa cuando la espalda manda; los trainings de prueba, que no se quieren ver
 * ni borrar; y los días ya entrenados de un atleta. Esconderlos por una regla automática
 * -"solo lo de mañana"- se equivocaría justo el día malo, así que se archivan a mano.
 *
 * **La marca va por `uid` y vive fuera del training.** Las rutinas del coach se reemplazan
 * enteras al subir su revisión conservando el uid; si la marca viajara dentro del training,
 * lo archivado reaparecería en la siguiente revisión. Y al quedarse fuera tampoco entra en
 * lo que se publica: archivar es una preferencia de este teléfono, no algo que se reparta.
 */
object Archive {

    /** Un training sin uid nunca está archivado: no hay con qué recordarlo entre revisiones. */
    fun isArchived(training: Training, archived: Set<String>): Boolean =
        training.uid.isNotBlank() && training.uid in archived

    fun visible(trainings: List<Training>, archived: Set<String>): List<Training> =
        trainings.filterNot { isArchived(it, archived) }

    fun archived(trainings: List<Training>, archived: Set<String>): List<Training> =
        trainings.filter { isArchived(it, archived) }

    /**
     * Las posiciones reales de lo que se ve, en orden.
     *
     * Reordenar arrastra sobre la lista visible, pero el orden que se guarda es el de todos
     * los trainings: sin esta traducción, con algo archivado en medio, la tarjeta soltada
     * aterrizaría en otro sitio.
     */
    fun visibleIndices(trainings: List<Training>, archived: Set<String>): List<Int> =
        trainings.indices.filterNot { isArchived(trainings[it], archived) }

    /** Lo mismo para lo archivado, que desde TD-150 también se ordena arrastrando. */
    fun archivedIndices(trainings: List<Training>, archived: Set<String>): List<Int> =
        trainings.indices.filter { isArchived(trainings[it], archived) }

    /**
     * Mueve el training de la posición real [from] a [to], como lo hace la lista.
     *
     * Solo cambia de sitio uno, así que el orden relativo de todos los demás se conserva:
     * mover dentro de lo archivado no toca el orden de lo visible, aunque en la lista
     * completa estén intercalados.
     */
    fun move(trainings: List<Training>, from: Int, to: Int): List<Training> {
        if (from == to || from !in trainings.indices || to !in trainings.indices) return trainings
        return trainings.toMutableList().apply { add(to, removeAt(from)) }
    }
}
