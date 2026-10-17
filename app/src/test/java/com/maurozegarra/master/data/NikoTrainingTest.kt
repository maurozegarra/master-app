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

    @Test
    fun `revision 24 sube lo que pidio y baja el remo que le pesa`() {
        val hip = ex(MasterDefaults.NIKO_GLUTE_ID, "ex_hip_thrust")
        assertEquals(listOf(46.0, 58.5, 68.5, 73.5), hip.setList.map { hip.barWeight + it.weight })
        val remo = ex(MasterDefaults.NIKO_UPPER_ID, "ex_dumbbell_row")
        assertEquals(listOf(7.5, 7.5, 7.5, 7.5), remo.setList.map { it.weight })
        val pmUna = ex(MasterDefaults.NIKO_SINGLE_LEG_ID, "ex_single_leg_deadlift")
        assertEquals(listOf(10.0, 10.0, 10.0), pmUna.setList.map { it.weight })
        assertEquals(true, ex(MasterDefaults.NIKO_SINGLE_LEG_ID, "ex_lateral_band_walk").note.contains("FUERTE"))
        assertEquals(true, ex(MasterDefaults.NIKO_SINGLE_LEG_ID, "ex_glute_bridge").note.contains("FUERTE"))
        // NIKO 1 sigue con la media: ahi la marco bien, no facil.
        assertEquals(true, ex(MasterDefaults.NIKO_GLUTE_ID, "ex_lateral_band_walk").note.contains("MEDIA"))
        val cosaca = ex(MasterDefaults.NIKO_MIXED_ID, "ex_cossack_squat")
        assertEquals(listOf(7.5, 7.5), cosaca.setList.map { it.weight })
        assertEquals(true, cosaca.note.contains("7.5 kg"))
    }
}
