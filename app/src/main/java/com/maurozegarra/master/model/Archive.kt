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
}
