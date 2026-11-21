package com.maurozegarra.master.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Qué se ve en la pantalla principal (8-oct-2026, pedido suyo).
 *
 * **Uno solo arriba:** el training de hoy. Si ya entrenó, el que hizo, en estado *Done*
 * (TD-215): hasta entonces subía el de mañana, y parecía que hoy faltaba algo. Si hoy no toca
 * nada, el que sigue ([NextTraining]). Los días que vienen se miran tocándolos en el
 * calendario ([on]).
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

    /** El lunes de la semana de [today]: donde empieza el calendario de arriba. */
    fun weekStart(today: LocalDate): LocalDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** El de arriba: el que hizo hoy, el de hoy o el que sigue. Null si nada lleva programa. */
    fun main(trainings: List<Training>, sessions: List<SessionLog>, today: LocalDate, zone: ZoneId): Training? {
        val visibles = shown(trainings)
        doneToday(trainings, sessions, today, zone)?.let { s -> visibles.firstOrNull { it.id == s.trainingId } }?.let { return it }
        val id = NextTraining.of(visibles, sessions, today, zone) ?: return null
        return visibles.firstOrNull { it.id == id }
    }

    /**
     * La sesión completa de hoy de un training de la lista (TD-215), la última si hay varias.
     * Una a medias no cuenta, como en [NextTraining]: si la cortó, lo de hoy sigue pendiente.
     * Vale cualquiera de la lista, no solo el programado: si cambió el corto por el día malo, lo
     * hecho es el día malo.
     */
    fun doneToday(trainings: List<Training>, sessions: List<SessionLog>, today: LocalDate, zone: ZoneId): SessionLog? {
        val programables = shown(trainings).filter { it.scheduleDays.isNotEmpty() || it.scheduleDates.isNotEmpty() || it.cycleDay != null }
        if (programables.isEmpty()) return null
        val ids = shown(trainings).map { it.id }.toSet()
        return sessions
            .filter { it.trainingId in ids && it.status == SessionStatus.COMPLETED && Instant.ofEpochMilli(it.completedAt).atZone(zone).toLocalDate() == today }
            .maxByOrNull { it.completedAt }
    }

    /**
     * Lo que toca un día que viene (TD-215), para verlo tocándolo en el calendario.
     *
     * - **Por día de la semana:** la fecha puntual manda sobre el día, como en [NextTraining].
     * - **Por número (NIKO):** supone que entrena todos los días. Si un día no entrena, lo que
     *   viene se corre solo, porque se cuenta desde lo último que completó: así funciona su
     *   programa, decidido con él el 20-nov.
     *
     * Null si [date] no es posterior a [today] o si ese día no toca nada.
     */
    fun on(trainings: List<Training>, sessions: List<SessionLog>, date: LocalDate, today: LocalDate, zone: ZoneId): Training? {
        if (!date.isAfter(today)) return null
        val visibles = shown(trainings)
        val programados = visibles.filter { it.scheduleDays.isNotEmpty() || it.scheduleDates.isNotEmpty() }
        if (programados.isNotEmpty()) {
            return programados.firstOrNull { date in it.scheduleDates }
                ?: programados.firstOrNull { date.dayOfWeek in it.scheduleDays }
        }
        val ciclo = visibles.filter { it.cycleDay != null }.sortedBy { it.cycleDay }
        if (ciclo.isEmpty()) return null
        // El siguiente que le toca: hoy si todavía no entrenó, mañana si ya.
        val siguiente = NextTraining.of(ciclo, sessions, today, zone) ?: return null
        val desde = if (doneToday(ciclo, sessions, today, zone) != null) today.plusDays(1) else today
        val pasos = java.time.temporal.ChronoUnit.DAYS.between(desde, date).toInt()
        val i = ciclo.indexOfFirst { it.id == siguiente }
        return ciclo[(i + pasos) % ciclo.size]
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
