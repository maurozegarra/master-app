package com.maurozegarra.master.i18n

import org.junit.Assert.assertEquals
import org.junit.Test

class CountTest {
    private val t = I18n.EN

    @Test
    fun `uno en singular, varios en plural`() {
        // La pantalla previa del 30-sep decia "7 Exercise · 4 Workout".
        assertEquals("1 Exercise", t.exerciseCount(1))
        assertEquals("7 Exercises", t.exerciseCount(7))
        assertEquals("1 Workout", t.workoutCount(1))
        assertEquals("4 Workouts", t.workoutCount(4))
    }
}
