package com.maurozegarra.master.model

/**
 * El historial de un atleta, tal como lo ve el coach en el app (TD-126, etapa 3).
 *
 * Hasta aqui sus sesiones llegaban al telefono del coach pero solo las leia el asistente,
 * en el respaldo: el usuario no tenia donde ver como entreno NIKO sin preguntar. Es el mismo
 * historial que el propio -la misma pantalla, las mismas filas-, con sus sesiones.
 */
object AthleteHistory {

    /**
     * Las sesiones de [profileId], de la mas nueva a la mas vieja.
     *
     * Sin repetidas por id: si una sesion bajara dos veces -una subida que se reintento-,
     * la lista contaria un entrenamiento dos veces.
     */
    fun of(all: List<AthleteSession>, profileId: String): List<SessionLog> =
        all.asSequence()
            .filter { it.profileId == profileId }
            .map { it.session }
            .distinctBy { it.id }
            .sortedByDescending { it.completedAt }
            .toList()
}
