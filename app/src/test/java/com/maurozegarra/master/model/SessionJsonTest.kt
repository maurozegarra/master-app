package com.maurozegarra.master.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionJsonTest {

    private fun sampleSession(
        id: Long = 1000L,
        status: SessionStatus = SessionStatus.COMPLETED,
        exercises: List<ExerciseRecord> = emptyList(),
    ) = SessionLog(
        id = id,
        trainingId = 42L,
        trainingName = "Hybrid",
        completedAt = 1700000000000L,
        startedAt = 1699999990000L,
        status = status,
        exercises = exercises,
        durationSec = 1800,
    )

    private fun sampleExercise(
        exerciseId: String = "squat",
        name: String = "Squat",
        workoutIndex: Int = 0,
        setsCompleted: Int = 3,
        totalSets: Int = 3,
        timeBased: Boolean = true,
        totalExercisesInWorkout: Int = 1,
        feedbackDeltaKg: Double? = null,
    ) = ExerciseRecord(
        exerciseId = exerciseId,
        name = name,
        workoutName = "Lower",
        workoutIndex = workoutIndex,
        setsCompleted = setsCompleted,
        totalSets = totalSets,
        sets = listOf(
            SetRecord(reps = 12, weightKg = 40.0, durationSec = 30),
            SetRecord(reps = 10, weightKg = 42.5, durationSec = 30),
            SetRecord(reps = 8, weightKg = 45.0, durationSec = 30),
        ),
        timeBased = timeBased,
        totalExercisesInWorkout = totalExercisesInWorkout,
        feedbackDeltaKg = feedbackDeltaKg,
    )

    @Test
    fun `round-trip preserves all fields`() {
        val original = listOf(sampleSession(exercises = listOf(sampleExercise(feedbackDeltaKg = -2.5))))
        val json = SessionJson.encode(original)
        val decoded = SessionJson.decode(json)
        assertEquals(1, decoded.size)
        val s = decoded[0]
        assertEquals(1000L, s.id)
        assertEquals(42L, s.trainingId)
        assertEquals("Hybrid", s.trainingName)
        assertEquals(1700000000000L, s.completedAt)
        assertEquals(1699999990000L, s.startedAt)
        assertEquals(SessionStatus.COMPLETED, s.status)
        assertEquals(1800, s.durationSec)
        assertEquals(1, s.exercises.size)
        val er = s.exercises[0]
        assertEquals("squat", er.exerciseId)
        assertEquals("Squat", er.name)
        assertEquals(3, er.setsCompleted)
        assertEquals(3, er.totalSets)
        assertEquals(3, er.sets.size)
        assertEquals(12, er.sets[0].reps)
        assertEquals(40.0, er.sets[0].weightKg, 0.001)
        assertEquals(30, er.sets[0].durationSec)
        assertEquals(-2.5, er.feedbackDeltaKg!!, 0.001)
        assertEquals(1, er.totalExercisesInWorkout)
    }

    @Test
    fun `round-trip with partial status`() {
        val original = listOf(sampleSession(status = SessionStatus.PARTIAL))
        val decoded = SessionJson.decode(SessionJson.encode(original))
        assertEquals(SessionStatus.PARTIAL, decoded[0].status)
    }

    @Test
    fun `round-trip with no exercises`() {
        val original = listOf(sampleSession(exercises = emptyList()))
        val decoded = SessionJson.decode(SessionJson.encode(original))
        assertTrue(decoded[0].exercises.isEmpty())
    }

    @Test
    fun `round-trip with null feedbackDeltaKg`() {
        val original = listOf(sampleSession(exercises = listOf(sampleExercise(feedbackDeltaKg = null))))
        val decoded = SessionJson.decode(SessionJson.encode(original))
        assertNull(decoded[0].exercises[0].feedbackDeltaKg)
    }

    @Test
    fun `migration from old format without new fields`() {
        val oldJson = """[{"id":500,"trainingId":10,"trainingName":"Old","completedAt":1600000000000}]"""
        val decoded = SessionJson.decode(oldJson)
        assertEquals(1, decoded.size)
        val s = decoded[0]
        assertEquals(500L, s.id)
        assertEquals(10L, s.trainingId)
        assertEquals("Old", s.trainingName)
        assertEquals(1600000000000L, s.completedAt)
        assertEquals(0L, s.startedAt)
        assertEquals(SessionStatus.COMPLETED, s.status)
        assertEquals(0, s.durationSec)
        assertTrue(s.exercises.isEmpty())
    }

    @Test
    fun `migration from old format with missing status defaults to completed`() {
        val oldJson = """[{"id":1,"trainingId":1,"trainingName":"X","completedAt":100}]"""
        val decoded = SessionJson.decode(oldJson)
        assertEquals(SessionStatus.COMPLETED, decoded[0].status)
    }

    @Test
    fun `multiple sessions round-trip`() {
        val original = listOf(
            sampleSession(id = 1L, exercises = listOf(sampleExercise())),
            sampleSession(id = 2L, status = SessionStatus.PARTIAL, exercises = listOf(
                sampleExercise(exerciseId = "press", name = "Press", workoutIndex = 1),
            )),
        )
        val decoded = SessionJson.decode(SessionJson.encode(original))
        assertEquals(2, decoded.size)
        assertEquals(1L, decoded[0].id)
        assertEquals(2L, decoded[1].id)
        assertEquals(SessionStatus.PARTIAL, decoded[1].status)
    }

    @Test
    fun `round-trip preserves totalExercisesInWorkout`() {
        val original = listOf(sampleSession(exercises = listOf(
            sampleExercise(totalExercisesInWorkout = 8),
        )))
        val decoded = SessionJson.decode(SessionJson.encode(original))
        assertEquals(8, decoded[0].exercises[0].totalExercisesInWorkout)
    }

    @Test
    fun `migration from old format without totalExercisesInWorkout defaults to 0`() {
        val oldJson = """[{"id":1,"trainingId":1,"trainingName":"X","completedAt":100,"exercises":[{"exerciseId":"squat","name":"Squat","workoutName":"Lower","workoutIndex":0,"setsCompleted":3,"totalSets":3,"sets":[],"timeBased":true}]}]"""
        val decoded = SessionJson.decode(oldJson)
        assertEquals(0, decoded[0].exercises[0].totalExercisesInWorkout)
    }

    @Test
    fun `invalid json returns empty list`() {
        assertTrue(SessionJson.decode("not json").isEmpty())
        assertTrue(SessionJson.decode("").isEmpty())
    }

    @Test
    fun `reps-based exercise round-trip`() {
        val original = listOf(sampleSession(exercises = listOf(
            sampleExercise(timeBased = false, feedbackDeltaKg = 2.5),
        )))
        val decoded = SessionJson.decode(SessionJson.encode(original))
        val er = decoded[0].exercises[0]
        assertEquals(false, er.timeBased)
        assertEquals(2.5, er.feedbackDeltaKg!!, 0.001)
    }
}
