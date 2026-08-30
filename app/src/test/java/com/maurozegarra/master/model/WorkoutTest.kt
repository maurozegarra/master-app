package com.maurozegarra.master.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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

    // ---------- deepCopy: ningún id se comparte con el original ----------

    /** Generador determinista para poder afirmar sobre los ids resultantes. */
    private fun idGen(start: Long = 100L): () -> Long {
        var next = start
        return { next++ }
    }

    @Test
    fun `deepCopy reassigns workout and exercise ids`() {
        val w = Workout(id = 1, name = "Lower", exercises = listOf(exercise(), exercise("Lunge")))
        val copy = w.deepCopy(idGen())

        assertNotEquals(w.id, copy.id)
        assertEquals(w.exercises.size, copy.exercises.size)
        val originalIds = w.exercises.map { it.id }.toSet()
        assertTrue(copy.exercises.none { it.id in originalIds })
    }

    @Test
    fun `deepCopy reassigns variant ids and their exercise ids`() {
        val w = Workout(
            id = 1,
            rotating = true,
            variants = listOf(
                variant(10, "Running", listOf(exercise("Run"))),
                variant(11, "Lower", listOf(exercise("Squat"), exercise("Lunge"))),
            ),
        )
        val copy = w.deepCopy(idGen())

        assertEquals(w.variants.size, copy.variants.size)
        val variantIds = w.variants.map { it.id }.toSet()
        assertTrue(copy.variants.none { it.id in variantIds })

        val exerciseIds = w.variants.flatMap { v -> v.exercises.map { it.id } }.toSet()
        val copyExerciseIds = copy.variants.flatMap { v -> v.exercises.map { it.id } }
        assertTrue(copyExerciseIds.none { it in exerciseIds })
    }

    @Test
    fun `deepCopy produces globally unique ids`() {
        val w = Workout(
            id = 1,
            exercises = listOf(exercise()),
            rotating = true,
            variants = listOf(variant(10, "A", listOf(exercise("Squat"), exercise("Lunge")))),
        )
        val copy = w.deepCopy(idGen())

        val ids = buildList {
            add(copy.id)
            addAll(copy.exercises.map { it.id })
            addAll(copy.variants.map { it.id })
            addAll(copy.variants.flatMap { v -> v.exercises.map { it.id } })
        }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `deepCopy keeps content and resets rotationIndex`() {
        val w = Workout(
            id = 1,
            name = "Cardio",
            rotating = true,
            rotationIndex = 3,
            variants = listOf(variant(10, "Running", listOf(exercise("Run")))),
        )
        val copy = w.deepCopy(idGen())

        assertEquals(w.name, copy.name)
        assertEquals(w.rotating, copy.rotating)
        assertEquals(0, copy.rotationIndex)
        assertEquals("Running", copy.variants[0].name)
        assertEquals("Run", copy.variants[0].exercises[0].name)
    }

    @Test
    fun `editing the copy does not touch the original`() {
        val w = Workout(
            id = 1,
            rotating = true,
            variants = listOf(variant(10, "Running", listOf(exercise("Run")))),
        )
        val copy = w.deepCopy(idGen())
        val edited = copy.copy(
            variants = copy.variants.map { v -> v.copy(name = "Cycling", exercises = emptyList()) },
        )

        assertEquals("Running", w.variants[0].name)
        assertEquals(1, w.variants[0].exercises.size)
        assertEquals("Cycling", edited.variants[0].name)
    }
}
