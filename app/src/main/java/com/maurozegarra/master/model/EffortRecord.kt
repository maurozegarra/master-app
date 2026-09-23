package com.maurozegarra.master.model

/**
 * El registro de cómo fue una sesión, en lo que no lleva peso (TD-152), para el resumen.
 *
 * Es lo que el coach lee para ajustar la siguiente: "Push-ups: Easy", "Inverted row: Hard
 * (6/8)", "Dead hang: Right (held 18 of 25 s)", "Heavy bag: R1 Right R2 Right R3 Hard...".
 * El app no cambia la rutina solo; la rutina de NIKO la ajusta el coach desde el código.
 */
object EffortRecord {

    data class Mark(
        val set: Int,
        val effort: Int?,
        val reps: Int,
        val repsDone: Int?,
        /** Los segundos aguantados, si se cortó antes; null si se completó. */
        val heldSec: Int?,
        val plannedSec: Int?,
    )

    data class Line(
        val exerciseId: String,
        val name: String,
        val side: String,
        /** Si hay más de una serie contada: entonces cada una va con su número. */
        val numbered: Boolean,
        val marks: List<Mark>,
    )

    /** Una línea por ejercicio y lado que tenga algo que contar, en el orden de la rutina. */
    fun lines(session: SessionLog): List<Line> =
        session.exercises.mapNotNull { er ->
            val marks = er.sets.mapIndexedNotNull { i, sr ->
                if (sr.effort == null && sr.plannedSec == null) {
                    null
                } else {
                    Mark(
                        set = i,
                        effort = sr.effort,
                        reps = sr.reps,
                        repsDone = sr.repsDone,
                        heldSec = sr.plannedSec?.let { sr.durationSec },
                        plannedSec = sr.plannedSec,
                    )
                }
            }
            if (marks.isEmpty()) null
            else Line(er.exerciseId, er.name, er.side, numbered = marks.size > 1, marks = marks)
        }
}
