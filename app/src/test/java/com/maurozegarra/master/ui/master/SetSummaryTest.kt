package com.maurozegarra.master.ui.master

import com.maurozegarra.master.i18n.I18n
import com.maurozegarra.master.model.SetRecord
import org.junit.Assert.assertEquals
import org.junit.Test

/** La línea corta de cada serie en el historial (TD-118). */
class SetSummaryTest {

    private val t = I18n.EN

    @Test
    fun `por reps con peso, reps por kilos`() {
        assertEquals("12 × 6 kg", setSummary(SetRecord(reps = 12, weightKg = 6.0), timeBased = false, t))
        assertEquals("2 × 12.5 kg", setSummary(SetRecord(reps = 2, weightKg = 12.5), timeBased = false, t))
    }

    @Test
    fun `por reps sin peso, las reps con su palabra`() {
        assertEquals("8 ${t.repLabel}", setSummary(SetRecord(reps = 8), timeBased = false, t))
    }

    @Test
    fun `por tiempo, solo el tiempo y sin las reps que ahi no miden nada`() {
        // En McGill decia "10 reps · 10s" en cada uno de los doce aguantes.
        assertEquals("10s", setSummary(SetRecord(reps = 10, durationSec = 10), timeBased = true, t))
        assertEquals("6:00", setSummary(SetRecord(reps = 12, durationSec = 360), timeBased = true, t))
    }

    @Test
    fun `por tiempo con peso, el tiempo y el peso`() {
        assertEquals("30s  ·  10 kg", setSummary(SetRecord(durationSec = 30, weightKg = 10.0), timeBased = true, t))
    }
}
