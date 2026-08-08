package com.maurozegarra.master.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutTest {

    private fun variant(id: Long, name: String, exercises: List<Exercise> = emptyList()) =
        WorkoutVariant(id = id, name = name, exercises = exercises)

    private fun exercise(name: String = "Squat") = Exercise(id = 1, exerciseId = "squat", name = name)

    @Test
    fun `activeVariant returns null for non-rotating workout`() {
        val w = Workout(id = 1, rotating = false, variants = listOf(variant(10, "A")))
        assertNull(w.activeVariant())
    }

    @Test
    fun `activeVariant returns null for rotating workout with no variants`() {
        val w = Workout(id = 1, rotating = true, variants = emptyList())
        assertNull(w.activeVariant())
    }

    @Test
    fun `activeVariant returns variant at rotationIndex`() {
        val v0 = variant(10, "A")
        val v1 = variant(11, "B")
        val w = Workout(id = 1, rotating = true, rotationIndex = 0, variants = listOf(v0, v1))
        assertEquals(v0, w.activeVariant())
    }

    @Test
    fun `activeVariant wraps around with modulo`() {
        val v0 = variant(10, "A")
        val v1 = variant(11, "B")
        val w = Workout(id = 1, rotating = true, rotationIndex = 3, variants = listOf(v0, v1))
        // 3 % 2 = 1 → v1
        assertEquals(v1, w.activeVariant())
    }

    @Test
    fun `activeExercises returns variant exercises when rotating`() {
        val exA = exercise("Sprint")
        val exB = exercise("Bike")
        val w = Workout(id = 1, rotating = true, rotationIndex = 0,
            variants = listOf(variant(10, "A", listOf(exA)), variant(11, "B", listOf(exB))))
        assertEquals(listOf(exA), w.activeExercises())
    }

    @Test
    fun `activeExercises returns own exercises when not rotating`() {
        val ex0 = exercise("Squat")
        val w = Workout(id = 1, rotating = false, exercises = listOf(ex0))
        assertEquals(listOf(ex0), w.activeExercises())
    }

    @Test
    fun `activeName returns variant name when rotating and variant name not blank`() {
        val w = Workout(id = 1, name = "Cardio", rotating = true, rotationIndex = 0,
            variants = listOf(variant(10, "Running")))
        assertEquals("Running", w.activeName())
    }

    @Test
    fun `activeName falls back to workout name when variant name is blank`() {
        val w = Workout(id = 1, name = "Cardio", rotating = true, rotationIndex = 0,
            variants = listOf(variant(10, "")))
        assertEquals("Cardio", w.activeName())
    }

    @Test
    fun `activeName returns workout name when not rotating`() {
        val w = Workout(id = 1, name = "Lower", rotating = false)
        assertEquals("Lower", w.activeName())
    }

    @Test
    fun `hasContent true when exercises is non-empty`() {
        val w = Workout(id = 1, exercises = listOf(exercise()))
        assertTrue(w.hasContent())
    }

    @Test
    fun `hasContent true when a variant has exercises even if own exercises is empty`() {
        val w = Workout(id = 1, exercises = emptyList(),
            variants = listOf(variant(10, "A", listOf(exercise()))))
        assertTrue(w.hasContent())
    }

    @Test
    fun `hasContent false when no exercises and no variant exercises`() {
        val w = Workout(id = 1, exercises = emptyList(), variants = listOf(variant(10, "A")))
        assertFalse(w.hasContent())
    }

    // --- Weight calculations ---

    @Test
    fun `weightTotal BARBELL adds barWeight plus set weight`() {
        val e = Exercise(id = 1, exerciseId = "squat", name = "Squat",
            weightType = WeightType.BARBELL, barWeight = 20.0)
        val ws = WorkSet(reps = 5, weight = 40.0)
        assertEquals(60.0, e.weightTotal(ws), 0.001)
    }

    @Test
    fun `weightTotal DUMBBELL doubles set weight`() {
        val e = Exercise(id = 1, exerciseId = "curl", name = "Curl",
            weightType = WeightType.DUMBBELL)
        val ws = WorkSet(reps = 12, weight = 12.5)
        assertEquals(25.0, e.weightTotal(ws), 0.001)
    }

    @Test
    fun `weightTotal TOTAL returns set weight directly`() {
        val e = Exercise(id = 1, exerciseId = "kb", name = "KB Swing",
            weightType = WeightType.TOTAL)
        val ws = WorkSet(reps = 15, weight = 24.0)
        assertEquals(24.0, e.weightTotal(ws), 0.001)
    }

    @Test
    fun `weightTotal NONE returns zero`() {
        val e = Exercise(id = 1, exerciseId = "pu", name = "Pushup",
            weightType = WeightType.NONE)
        val ws = WorkSet(reps = 20, weight = 0.0)
        assertEquals(0.0, e.weightTotal(ws), 0.001)
    }

    @Test
    fun `setAt returns setList element when in range`() {
        val e = Exercise(id = 1, exerciseId = "squat", name = "Squat",
            setList = listOf(WorkSet(reps = 10), WorkSet(reps = 8), WorkSet(reps = 6)))
        assertEquals(10, e.setAt(0).reps)
        assertEquals(8, e.setAt(1).reps)
        assertEquals(6, e.setAt(2).reps)
    }

    @Test
    fun `setAt falls back to WorkSet with workValue when out of range`() {
        val e = Exercise(id = 1, exerciseId = "squat", name = "Squat",
            workValue = 12, setList = listOf(WorkSet(reps = 10)))
        assertEquals(12, e.setAt(5).reps)
    }

    @Test
    fun `isWeighted true when REPS mode and weightType is not NONE`() {
        val e = Exercise(id = 1, exerciseId = "squat", name = "Squat",
            workMode = WorkMode.REPS, weightType = WeightType.BARBELL)
        assertTrue(e.isWeighted)
    }

    @Test
    fun `isWeighted false when TIME mode even with weightType`() {
        val e = Exercise(id = 1, exerciseId = "squat", name = "Squat",
            workMode = WorkMode.TIME, weightType = WeightType.BARBELL)
        assertFalse(e.isWeighted)
    }

    @Test
    fun `isWeighted false when REPS mode with NONE`() {
        val e = Exercise(id = 1, exerciseId = "pu", name = "Pushup",
            workMode = WorkMode.REPS, weightType = WeightType.NONE)
        assertFalse(e.isWeighted)
    }
}
