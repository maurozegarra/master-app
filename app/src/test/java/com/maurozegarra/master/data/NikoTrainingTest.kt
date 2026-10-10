package com.maurozegarra.master.data

import com.maurozegarra.master.model.Exercise
import org.junit.Assert.assertEquals
import org.junit.Test

/** Las rutinas de NIKO como las deja el coach: lo que cambia cada revisión. */
class NikoTrainingTest {

    private fun ex(trainingId: Long, exerciseId: String): Exercise =
        MasterDefaults.nikoTrainings("en").single { it.id == trainingId }
            .workouts.flatMap { it.exercises }.single { it.exerciseId == exerciseId }

    @Test
    fun `NIKO 5 sube lo que le sobro dos veces seguidas`() {
        // Revision 22 (9-oct): faciles el 25-sep y el 2-oct.
        val salto = ex(MasterDefaults.NIKO_POWER_ID, "ex_box_jump")
        assertEquals(4, salto.sets)
        assertEquals(6, salto.workValue)

        val rodillas = ex(MasterDefaults.NIKO_POWER_ID, "ex_deep_knees")
        assertEquals(4, rodillas.sets)
        assertEquals(40, rodillas.workValue)

        val teep = ex(MasterDefaults.NIKO_POWER_ID, "ex_teep")
        assertEquals(3, teep.sets)
        assertEquals(10, teep.workValue)
    }
}
