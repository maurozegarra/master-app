package com.maurozegarra.master.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RotationTest {

    private fun workout(
        id: Long = 1,
        rotating: Boolean = true,
        rotationIndex: Int = 0,
        variants: Int = 3,
    ) = Workout(
        id = id,
        rotating = rotating,
        rotationIndex = rotationIndex,
        variants = (0 until variants).map { WorkoutVariant(id = it.toLong(), name = "V$it") },
    )

    // --- nextRotationIndex ---

    @Test
    fun `nextRotationIndex advances by 1 when not at end`() {
        val w = workout(rotationIndex = 0, variants = 3)
        assertEquals(1, StepEngine.nextRotationIndex(w))
    }

    @Test
    fun `nextRotationIndex wraps to 0 after last variant`() {
        val w = workout(rotationIndex = 2, variants = 3)
        assertEquals(0, StepEngine.nextRotationIndex(w))
    }

    @Test
    fun `nextRotationIndex wraps with single variant`() {
        val w = workout(rotationIndex = 0, variants = 1)
        assertEquals(0, StepEngine.nextRotationIndex(w))
    }

    @Test
    fun `nextRotationIndex returns null for non-rotating workout`() {
        val w = workout(rotating = false, variants = 3)
        assertNull(StepEngine.nextRotationIndex(w))
    }

    @Test
    fun `nextRotationIndex returns null when no variants`() {
        val w = Workout(id = 1, rotating = true, rotationIndex = 0, variants = emptyList())
        assertNull(StepEngine.nextRotationIndex(w))
    }

    @Test
    fun `nextRotationIndex handles large rotationIndex with modulo`() {
        val w = workout(rotationIndex = 5, variants = 3)
        // (5 + 1) % 3 = 0
        assertEquals(0, StepEngine.nextRotationIndex(w))
    }

    // --- workoutsToRotate (idempotencia) ---

    @Test
    fun `workoutsToRotate returns all indices when advancedWorkouts is empty`() {
        val result = StepEngine.workoutsToRotate(emptySet(), uptoExclusive = 3)
        assertEquals(listOf(0, 1, 2), result)
    }

    @Test
    fun `workoutsToRotate excludes already advanced workouts`() {
        val result = StepEngine.workoutsToRotate(setOf(0, 1), uptoExclusive = 3)
        assertEquals(listOf(2), result)
    }

    @Test
    fun `workoutsToRotate returns empty when all already advanced`() {
        val result = StepEngine.workoutsToRotate(setOf(0, 1, 2), uptoExclusive = 3)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `workoutsToRotate with uptoExclusive 0 returns empty`() {
        val result = StepEngine.workoutsToRotate(emptySet(), uptoExclusive = 0)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `workoutsToRotate idempotency - calling twice with same advanced set produces no new rotations`() {
        val advanced = mutableSetOf<Int>()
        val first = StepEngine.workoutsToRotate(advanced, uptoExclusive = 3)
        advanced.addAll(first)
        val second = StepEngine.workoutsToRotate(advanced, uptoExclusive = 3)
        assertTrue(second.isEmpty())
    }

    @Test
    fun `workoutsToRotate idempotency - partial advance then extend produces only new indices`() {
        val advanced = mutableSetOf<Int>()
        // First call: complete workouts 0 and 1
        val first = StepEngine.workoutsToRotate(advanced, uptoExclusive = 2)
        advanced.addAll(first)
        assertEquals(setOf(0, 1), advanced)

        // Second call: now complete workout 2 as well (uptoExclusive = 3)
        val second = StepEngine.workoutsToRotate(advanced, uptoExclusive = 3)
        assertEquals(listOf(2), second)
    }

    @Test
    fun `workoutsToRotate ignores indices outside range even if in advanced set`() {
        // advancedWorkouts has index 5 (out of range), uptoExclusive = 3
        val result = StepEngine.workoutsToRotate(setOf(5), uptoExclusive = 3)
        assertEquals(listOf(0, 1, 2), result)
    }

    @Test
    fun `workoutsToRotate with single workout returns it when not advanced`() {
        val result = StepEngine.workoutsToRotate(emptySet(), uptoExclusive = 1)
        assertEquals(listOf(0), result)
    }

    @Test
    fun `workoutsToRotate with single workout returns empty when already advanced`() {
        val result = StepEngine.workoutsToRotate(setOf(0), uptoExclusive = 1)
        assertTrue(result.isEmpty())
    }
}
