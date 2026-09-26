package com.maurozegarra.master.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/**
 * Cuál es el training que sigue, para ponerlo primero en la lista y marcarlo (TD-167).
 *
 * Dos formas de programar, porque son dos atletas distintos:
 *
 * - **Por día de la semana** ([Training.scheduleDays]), la del usuario: su semana es fija
 *   -lunes, miércoles, jueves y domingo el corto; martes, viernes y sábado el completo-.
 *   Si hoy toca algo y todavía no entrenó, el siguiente es el de HOY: a las cinco de la
 *   mañana tiene que estar arriba el que le toca. Si ya entrenó hoy, es el de mañana.
 * - **Por número** ([Training.cycleDay]), la de NIKO: sus días van del 1 al 6 y se corren
 *   si pierde uno, así que no pueden ir por día de la semana. El siguiente es el que viene
 *   después del último que completó, y del último se vuelve al primero.
 *
 * Si hay de los dos, manda el día de la semana. Es lo que hace que en el teléfono del coach
 * -que tiene los suyos y los de NIKO- el siguiente sea el SUYO: los de ella se miran en su
 * historial, no en la lista.
 */
object NextTraining {

    fun of(trainings: List<Training>, sessions: List<SessionLog>, today: LocalDate, zone: ZoneId): Long? =
        porSemana(trainings, sessions, today, zone) ?: porNumero(trainings, sessions)

    private fun porSemana(trainings: List<Training>, sessions: List<SessionLog>, today: LocalDate, zone: ZoneId): Long? {
        val programados = trainings.filter { it.scheduleDays.isNotEmpty() }
        if (programados.isEmpty()) return null
        val ids = programados.map { it.id }.toSet()
        // Entrenado hoy = una sesion COMPLETA de uno de sus trainings programados. Una a
        // medias no cuenta: si la corto, lo de hoy sigue pendiente.
        val entrenoHoy = sessions.any {
            it.trainingId in ids && it.status == SessionStatus.COMPLETED &&
                java.time.Instant.ofEpochMilli(it.completedAt).atZone(zone).toLocalDate() == today
        }
        val desde = if (entrenoHoy) 1 else 0
        for (i in desde..desde + 6) {
            val dia: DayOfWeek = today.plusDays(i.toLong()).dayOfWeek
            programados.firstOrNull { dia in it.scheduleDays }?.let { return it.id }
        }
        return null
    }

    private fun porNumero(trainings: List<Training>, sessions: List<SessionLog>): Long? {
        val ciclo = trainings.filter { it.cycleDay != null }.sortedBy { it.cycleDay }
        if (ciclo.isEmpty()) return null
        val porId = ciclo.associateBy { it.id }
        val ultimo = sessions
            .filter { it.status == SessionStatus.COMPLETED && it.trainingId in porId }
            .maxByOrNull { it.completedAt }
            ?.let { porId.getValue(it.trainingId).cycleDay }
            ?: return ciclo.first().id
        // El que sigue entre los que HAY: a NIKO se le asigna un dia a la vez, asi que puede
        // no tener todavia el numero exacto que viene.
        return (ciclo.firstOrNull { it.cycleDay!! > ultimo } ?: ciclo.first()).id
    }
}
