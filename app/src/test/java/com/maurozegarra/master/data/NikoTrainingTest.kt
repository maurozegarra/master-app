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

    @Test
    fun `revision 23 sube lo que le sobro en NIKO 4 y la altura del salto`() {
        // 10-oct: el hip thrust a una pierna fue fácil las seis; la hiperextensión, bien a 10.
        assertEquals(12, ex(MasterDefaults.NIKO_SINGLE_LEG_ID, "ex_single_leg_hip_thrust").workValue)
        assertEquals(12, ex(MasterDefaults.NIKO_SINGLE_LEG_ID, "ex_back_extension").workValue)
        // El salto sube de altura, no de repeticiones.
        val salto = ex(MasterDefaults.NIKO_POWER_ID, "ex_box_jump")
        assertEquals(6, salto.workValue)
        assertEquals(true, salto.note.contains("61 cm"))
    }
}
