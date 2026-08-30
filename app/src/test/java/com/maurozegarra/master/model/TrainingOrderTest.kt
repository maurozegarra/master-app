package com.maurozegarra.master.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Orden del selector de workouts: manda el historial, no la edición. La lista de trainings
 * acumula pruebas que nunca se ejercitaron y no deben tapar al que sí se usa.
 */
class TrainingOrderTest {

    private fun training(id: Long, name: String, updatedAt: Long) =
        Training(id = id, name = name, createdAt = 0L, updatedAt = updatedAt)

    private fun session(id: Long, trainingId: Long, completedAt: Long) =
        SessionLog(id = id, trainingId = trainingId, trainingName = "", completedAt = completedAt)

    @Test
    fun `trained trainings come first ordered by last session`() {
        val a = training(1, "A", updatedAt = 10)
        val b = training(2, "B", updatedAt = 20)
        val sessions = listOf(
            session(100, trainingId = 1, completedAt = 5_000),
            session(101, trainingId = 2, completedAt = 1_000),
        )

        val sorted = listOf(a, b).sortedByLastTrained(sessions)

        assertEquals(listOf("A", "B"), sorted.map { it.name })
    }

    @Test
    fun `never trained trainings sink below trained ones`() {
        val used = training(1, "Used", updatedAt = 1)
        val test1 = training(2, "Test 1", updatedAt = 500)
        val test2 = training(3, "Test 2", updatedAt = 900)
        val sessions = listOf(session(100, trainingId = 1, completedAt = 1_000))

        val sorted = listOf(test1, test2, used).sortedByLastTrained(sessions)

        // Aunque los de prueba se editaron después, el entrenado va primero.
        assertEquals(listOf("Used", "Test 2", "Test 1"), sorted.map { it.name })
    }

    @Test
    fun `never trained fall back to updatedAt`() {
        val old = training(1, "Old", updatedAt = 100)
        val recent = training(2, "Recent", updatedAt = 300)

        val sorted = listOf(old, recent).sortedByLastTrained(emptyList())

        assertEquals(listOf("Recent", "Old"), sorted.map { it.name })
    }

    @Test
    fun `last session wins when a training has several`() {
        val a = training(1, "A", updatedAt = 0)
        val b = training(2, "B", updatedAt = 0)
        val sessions = listOf(
            session(100, trainingId = 1, completedAt = 1_000),
            session(101, trainingId = 2, completedAt = 2_000),
            session(102, trainingId = 1, completedAt = 9_000),
        )

        val sorted = listOf(a, b).sortedByLastTrained(sessions)

        assertEquals(listOf("A", "B"), sorted.map { it.name })
    }

    @Test
    fun `orphan sessions of deleted trainings are ignored`() {
        val a = training(1, "A", updatedAt = 0)
        val sessions = listOf(
            session(100, trainingId = 99, completedAt = 9_000),
            session(101, trainingId = 1, completedAt = 1_000),
        )

        val sorted = listOf(a).sortedByLastTrained(sessions)

        assertEquals(listOf("A"), sorted.map { it.name })
    }

    @Test
    fun `lastTrainedAt keeps the most recent per training`() {
        val map = lastTrainedAt(
            listOf(
                session(100, trainingId = 1, completedAt = 1_000),
                session(101, trainingId = 1, completedAt = 7_000),
                session(102, trainingId = 2, completedAt = 3_000),
            )
        )

        assertEquals(7_000L, map[1])
        assertEquals(3_000L, map[2])
        assertEquals(null, map[3])
    }
}
