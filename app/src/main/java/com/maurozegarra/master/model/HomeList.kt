package com.maurozegarra.master.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Qué se ve en la pantalla principal (8-oct-2026, pedido suyo).
 *
 * **Uno solo arriba:** el training de hoy o, si ya entrenó, el que sigue ([NextTraining]).
 * Todo lo demás va plegado en la fila de abajo, y el gesto de archivar se quitó: con un solo
 * training a la vista no hay nada que sacar de en medio.
 *
 * **Abajo, primero lo de NIKO de hoy:** el que ella hizo hoy o, si no entrenó todavía, el que
 * le toca, para revisar lo que hará o hizo. Después el resto de NIKO en su orden y al final
 * los demás (los otros lumbares, el día malo).
 *
 * **Ocultos ([HIDDEN_UIDS]):** trainings viejos que ya no usa. No se borran -están publicados
 * en Supabase, en los respaldos del teléfono y en docs/trainings-saved/-; quitarlos de esta
 * lista los devuelve tal cual.
 */
object HomeList {

    val HIDDEN_UIDS: Set<String> = setOf(
        "b9d84943-364f-4da8-92d0-a93f5b555ed0", // MASTER
        "39ac72da-7bd2-4988-9740-62d74b677b59", // NIKO, el de antes de las rutinas numeradas
        "491fc629-c8fa-4126-aa41-1847408dec88", // On Your Marks
    )

    fun shown(trainings: List<Training>): List<Training> = trainings.filter { it.uid !in HIDDEN_UIDS }

    /** El de arriba: el de hoy o el que sigue. Null si nada lleva programa. */
    fun main(trainings: List<Training>, sessions: List<SessionLog>, today: LocalDate, zone: ZoneId): Training? {
        val visibles = shown(trainings)
        val id = NextTraining.of(visibles, sessions, today, zone) ?: return null
        return visibles.firstOrNull { it.id == id }
    }

    /** Lo de abajo, en orden: el de NIKO de hoy, el resto de NIKO por número y lo demás. */
    fun rest(
        trainings: List<Training>,
        sessions: List<SessionLog>,
        athleteSessions: List<SessionLog>,
        today: LocalDate,
        zone: ZoneId,
    ): List<Training> {
        val arriba = main(trainings, sessions, today, zone)
        val resto = shown(trainings).filter { it.id != arriba?.id }
        val ciclo = resto.filter { it.cycleDay != null }.sortedBy { it.cycleDay }
        val hoy = athleteToday(ciclo, athleteSessions, today, zone)
        val otros = resto.filter { it.cycleDay == null }
        return listOfNotNull(hoy) + ciclo.filter { it.id != hoy?.id } + otros
    }

    /**
     * El de NIKO de hoy, con sus sesiones (las que bajan al teléfono del coach): el que
     * completó hoy, o el que sigue al último que completó, como lo calcula su teléfono.
     *
     * Sus sesiones llevan los ids de SU teléfono, no los de aquí, así que se emparejan por
     * nombre, que es el mismo en los dos porque viaja con la asignación.
     */
    fun athleteToday(cycle: List<Training>, athleteSessions: List<SessionLog>, today: LocalDate, zone: ZoneId): Training? {
        if (cycle.isEmpty()) return null
        val porNombre = cycle.associateBy { it.name }
        val hechas = athleteSessions
            .filter { it.status == SessionStatus.COMPLETED && it.trainingName in porNombre }
            .sortedBy { it.completedAt }
        val deHoy = hechas.lastOrNull { Instant.ofEpochMilli(it.completedAt).atZone(zone).toLocalDate() == today }
        if (deHoy != null) return porNombre[deHoy.trainingName]
        val ultimo = hechas.lastOrNull()?.let { porNombre[it.trainingName]?.cycleDay } ?: return cycle.first()
        return cycle.firstOrNull { it.cycleDay!! > ultimo } ?: cycle.first()
    }
}
