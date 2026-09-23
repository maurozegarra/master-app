package com.maurozegarra.master.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** El historial de un atleta en el app del coach (TD-126, etapa 3). */
class AthleteHistoryTest {

    private fun s(id: Long, at: Long) = SessionLog(id = id, trainingId = 1L, trainingName = "NIKO", completedAt = at)

    @Test
    fun `solo las suyas, de la mas nueva a la mas vieja, sin repetir`() {
        val todas = listOf(
            AthleteSession("niko", s(1, 100)),
            AthleteSession("otro", s(2, 300)),
            AthleteSession("niko", s(3, 200)),
            AthleteSession("niko", s(3, 200)),
        )
        assertEquals(listOf(3L, 1L), AthleteHistory.of(todas, "niko").map { it.id })
    }
}
