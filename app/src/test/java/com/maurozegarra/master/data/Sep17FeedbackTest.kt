package com.maurozegarra.master.data

import com.maurozegarra.master.model.ExerciseRecord
import com.maurozegarra.master.model.SessionLog
import com.maurozegarra.master.model.SessionSource
import com.maurozegarra.master.model.SetRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** El feedback del 17-sep escrito desde el chat (TD-120). Toca datos reales: va probado. */
class Sep17FeedbackTest {

    private fun ej(id: String, n: Int, marcado: Double? = null) = ExerciseRecord(
        exerciseId = id, name = id, workoutName = "Hip & Glute", workoutIndex = 3,
        setsCompleted = n, totalSets = n, timeBased = false,
        sets = List(n) { SetRecord(reps = 12, weightKg = 10.0, feedbackDeltaKg = marcado) },
    )

    private fun sesion(vararg e: ExerciseRecord, id: Long = MasterDefaults.SESSION_17_SEP_ID) =
        SessionLog(id = id, trainingId = 951016L, trainingName = "LUMBAR (bad day)", completedAt = 0L, exercises = e.toList())

    @Test
    fun `escribe lo contado serie por serie y marca la sesion como editada`() {
        val s = MasterDefaults.withSep17Feedback(
            sesion(ej("ex_glute_bridge", 3), ej("ex_suitcase_carry", 3), ej("ex_box_squat", 3), ej("ex_curl_up", 12)),
        )!!
        val porId = s.exercises.associateBy { it.exerciseId }
        assertEquals(listOf(2.5, 2.5, null), porId.getValue("ex_glute_bridge").sets.map { it.feedbackDeltaKg })
        assertEquals(listOf(2.5, 2.5, 2.5), porId.getValue("ex_suitcase_carry").sets.map { it.feedbackDeltaKg })
        assertEquals(listOf(2.5, 2.5, 2.5), porId.getValue("ex_box_squat").sets.map { it.feedbackDeltaKg })
        // Lo que no se conto no se inventa.
        assertEquals(List<Double?>(12) { null }, porId.getValue("ex_curl_up").sets.map { it.feedbackDeltaKg })
        assertEquals(SessionSource.EDITED, s.source)
    }

    @Test
    fun `no toca otra sesion`() {
        assertNull(MasterDefaults.withSep17Feedback(sesion(ej("ex_glute_bridge", 3), id = 42L)))
    }

    @Test
    fun `lo que el mismo marco gana sobre lo contado`() {
        val s = sesion(ej("ex_glute_bridge", 3, marcado = 0.0))
        assertNull(MasterDefaults.withSep17Feedback(s))
    }

    @Test
    fun `si el numero de series no cuadra, no se toca`() {
        assertNull(MasterDefaults.withSep17Feedback(sesion(ej("ex_glute_bridge", 2))))
    }
}
