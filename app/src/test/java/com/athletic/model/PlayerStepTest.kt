package com.athletic.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerStepTest {

    private fun step(
        kind: StepKind = StepKind.WORK,
        timeBased: Boolean = true,
        confirm: ConfirmMode = ConfirmMode.AUTO,
        display: DisplayMode = DisplayMode.COUNTDOWN,
        durationSec: Int = 30,
        reps: Int = 0,
        secPerRep: Int = 3,
    ) = PlayerStep(
        kind = kind,
        title = "",
        timeBased = timeBased,
        confirm = confirm,
        display = display,
        durationSec = durationSec,
        reps = reps,
        secPerRep = secPerRep,
    )

    @Test
    fun `manual is true for WORK with REPS mode (timeBased false)`() {
        assertTrue(step(kind = StepKind.WORK, timeBased = false).manual)
    }

    @Test
    fun `manual is false for WORK with TIME mode`() {
        assertFalse(step(kind = StepKind.WORK, timeBased = true).manual)
    }

    @Test
    fun `manual is true when confirm is MANUAL`() {
        assertTrue(step(confirm = ConfirmMode.MANUAL).manual)
    }

    @Test
    fun `manual is true when display is STATIC`() {
        assertTrue(step(display = DisplayMode.STATIC).manual)
    }

    @Test
    fun `manual is false for PREP with AUTO and COUNTDOWN`() {
        assertFalse(step(kind = StepKind.PREP, confirm = ConfirmMode.AUTO, display = DisplayMode.COUNTDOWN).manual)
    }

    @Test
    fun `manual is false for REST with AUTO and COUNTDOWN`() {
        assertFalse(step(kind = StepKind.REST, confirm = ConfirmMode.AUTO, display = DisplayMode.COUNTDOWN).manual)
    }

    @Test
    fun `estimatedSec returns durationSec for timeBased steps`() {
        assertEquals(45, step(timeBased = true, durationSec = 45).estimatedSec)
    }

    @Test
    fun `estimatedSec returns reps times secPerRep for REPS steps`() {
        assertEquals(30, step(timeBased = false, reps = 10, secPerRep = 3).estimatedSec)
    }

    @Test
    fun `estimatedSec with custom secPerRep`() {
        assertEquals(50, step(timeBased = false, reps = 10, secPerRep = 5).estimatedSec)
    }
}
