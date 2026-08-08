package com.maurozegarra.master.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRecorderTest {

    private fun workStep(
        exerciseId: String = "squat",
        ownerName: String = "Squat",
        workoutName: String = "Lower",
        workoutIndex: Int = 0,
        setIndex: Int = 0,
        totalSets: Int = 3,
        reps: Int = 12,
        durationSec: Int = 30,
        timeBased: Boolean = true,
        weighted: Boolean = false,
        weightTotal: Double = 0.0,
    ) = PlayerStep(
        kind = StepKind.WORK,
        title = ownerName,
        ownerName = ownerName,
        ownerExerciseId = exerciseId,
        workoutName = workoutName,
        workoutIndex = workoutIndex,
        setIndex = setIndex,
        totalSets = totalSets,
        reps = reps,
        durationSec = durationSec,
        timeBased = timeBased,
        weighted = weighted,
        weightTotal = weightTotal,
    )

    @Test
    fun `empty recorder builds empty list`() {
        val r = SessionRecorder()
        assertTrue(r.isEmpty())
        assertTrue(r.build().isEmpty())
    }

    @Test
    fun `single work step produces one exercise record with one set`() {
        val r = SessionRecorder()
        r.onWorkStepCompleted(workStep(setIndex = 0, totalSets = 3, reps = 12, timeBased = true, durationSec = 30))
        val records = r.build()
        assertEquals(1, records.size)
        val er = records[0]
        assertEquals("squat", er.exerciseId)
        assertEquals("Squat", er.name)
        assertEquals(1, er.setsCompleted)
        assertEquals(3, er.totalSets)
        assertEquals(1, er.sets.size)
        assertEquals(12, er.sets[0].reps)
        assertEquals(30, er.sets[0].durationSec)
    }

    @Test
    fun `multiple sets of same exercise accumulate into one record`() {
        val r = SessionRecorder()
        r.onWorkStepCompleted(workStep(setIndex = 0, totalSets = 3))
        r.onWorkStepCompleted(workStep(setIndex = 1, totalSets = 3))
        r.onWorkStepCompleted(workStep(setIndex = 2, totalSets = 3))
        val records = r.build()
        assertEquals(1, records.size)
        assertEquals(3, records[0].setsCompleted)
        assertEquals(3, records[0].sets.size)
    }

    @Test
    fun `different exercises produce separate records`() {
        val r = SessionRecorder()
        r.onWorkStepCompleted(workStep(exerciseId = "squat", ownerName = "Squat", workoutIndex = 0))
        r.onWorkStepCompleted(workStep(exerciseId = "press", ownerName = "Press", workoutIndex = 0))
        val records = r.build()
        assertEquals(2, records.size)
    }

    @Test
    fun `same exercise in different workouts produces separate records`() {
        val r = SessionRecorder()
        r.onWorkStepCompleted(workStep(exerciseId = "squat", ownerName = "Squat", workoutIndex = 0))
        r.onWorkStepCompleted(workStep(exerciseId = "squat", ownerName = "Squat", workoutIndex = 1))
        val records = r.build()
        assertEquals(2, records.size)
        assertEquals(0, records[0].workoutIndex)
        assertEquals(1, records[1].workoutIndex)
    }

    @Test
    fun `reps-based step records weight and no duration`() {
        val r = SessionRecorder()
        r.onWorkStepCompleted(workStep(
            timeBased = false,
            reps = 10,
            weighted = true,
            weightTotal = 42.5,
            durationSec = 0,
        ))
        val er = r.build()[0]
        assertEquals(10, er.sets[0].reps)
        assertEquals(42.5, er.sets[0].weightKg, 0.001)
        assertEquals(0, er.sets[0].durationSec)
    }

    @Test
    fun `time-based step records duration and weight`() {
        val r = SessionRecorder()
        r.onWorkStepCompleted(workStep(
            timeBased = true,
            reps = 0,
            durationSec = 45,
            weighted = true,
            weightTotal = 20.0,
        ))
        val er = r.build()[0]
        assertEquals(45, er.sets[0].durationSec)
        assertEquals(20.0, er.sets[0].weightKg, 0.001)
    }

    @Test
    fun `non-work steps are ignored`() {
        val r = SessionRecorder()
        val restStep = workStep().copy(kind = StepKind.REST)
        r.onWorkStepCompleted(restStep)
        assertTrue(r.isEmpty())
    }

    @Test
    fun `setFeedback attaches delta to existing record`() {
        val r = SessionRecorder()
        r.onWorkStepCompleted(workStep(exerciseId = "squat", workoutIndex = 0))
        r.setFeedback("squat", 0, -2.5)
        val er = r.build()[0]
        assertEquals(-2.5, er.feedbackDeltaKg!!, 0.001)
    }

    @Test
    fun `setFeedback on non-existing record is no-op`() {
        val r = SessionRecorder()
        r.setFeedback("squat", 0, -2.5)
        assertTrue(r.isEmpty())
    }

    @Test
    fun `clear resets recorder`() {
        val r = SessionRecorder()
        r.onWorkStepCompleted(workStep())
        r.clear()
        assertTrue(r.isEmpty())
    }

    @Test
    fun `build sorts by workoutIndex then name`() {
        val r = SessionRecorder()
        r.onWorkStepCompleted(workStep(exerciseId = "press", ownerName = "Press", workoutIndex = 1))
        r.onWorkStepCompleted(workStep(exerciseId = "squat", ownerName = "Squat", workoutIndex = 0))
        r.onWorkStepCompleted(workStep(exerciseId = "deadlift", ownerName = "Deadlift", workoutIndex = 0))
        val records = r.build()
        assertEquals(0, records[0].workoutIndex)
        assertEquals("Deadlift", records[0].name)
        assertEquals(0, records[1].workoutIndex)
        assertEquals("Squat", records[1].name)
        assertEquals(1, records[2].workoutIndex)
    }

    @Test
    fun `totalExercisesInWorkout is injected from setTotalExercisesByWorkout`() {
        val r = SessionRecorder()
        r.setTotalExercisesByWorkout(mapOf(0 to 8, 1 to 5))
        r.onWorkStepCompleted(workStep(exerciseId = "squat", workoutIndex = 0))
        r.onWorkStepCompleted(workStep(exerciseId = "press", workoutIndex = 1))
        val records = r.build()
        assertEquals(8, records[0].totalExercisesInWorkout)
        assertEquals(5, records[1].totalExercisesInWorkout)
    }

    @Test
    fun `totalExercisesInWorkout defaults to 0 when not set`() {
        val r = SessionRecorder()
        r.onWorkStepCompleted(workStep(exerciseId = "squat", workoutIndex = 0))
        val records = r.build()
        assertEquals(0, records[0].totalExercisesInWorkout)
    }
}
