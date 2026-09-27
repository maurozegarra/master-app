package com.maurozegarra.master.model

import com.maurozegarra.master.data.MasterDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** La pantalla previa dice lo que de verdad se va a hacer (TD-173). */
class TrainingPreviewTest {

    // Con el respiro para contestar, como la arma el player.
    private val lumbar = TrainingPreview.of(StepEngine.buildSteps(MasterDefaults.lumbarTraining("en"), answerWindow = true))

    private fun item(blocks: List<PreviewBlock>, id: String) = blocks.flatMap { it.items }.first { it.exerciseId == id }

    @Test
    fun `el curl-up dice sus doce aguantes, no uno`() {
        // El 26-sep se leia "0:10", y parecia que el ejercicio duraba diez segundos.
        val curl = item(lumbar, "ex_curl_up")
        assertEquals(12, curl.work.size)
        assertTrue(TrainingPreview.describe(curl).startsWith("12 × 10 s"))
    }

    @Test
    fun `los pesos salen serie por serie`() {
        val ht = TrainingPreview.describe(item(lumbar, "ex_hip_thrust"))
        assertTrue(ht, ht.startsWith("3 × 10 · 21/31/36 kg"))
    }

    @Test
    fun `el carry dice metros, lados alternados y su peso`() {
        val carry = TrainingPreview.describe(item(lumbar, "ex_suitcase_carry"))
        assertTrue(carry, carry.startsWith("3 × 36 m · sides alternating · 15/17.5/20 kg"))
    }

    @Test
    fun `los lados uno despues del otro no se cuentan dos veces`() {
        val plancha = item(lumbar, "ex_side_plank")
        assertEquals(2, plancha.sides)
        assertFalse(plancha.alternate)
        assertEquals(12, plancha.work.size)
    }

    @Test
    fun `el respiro del final no es un descanso`() {
        // El ultimo ejercicio con reloj lleva un respiro para contestar (TD-156): no es
        // parte de la receta.
        // En NIKO 6 es el cuello, cuatro direcciones de una serie.
        val niko6 = MasterDefaults.nikoTrainings("es").single { it.name.startsWith("NIKO 6") }
        val steps = StepEngine.buildSteps(niko6, answerWindow = true)
        assertTrue(steps.last().kind == StepKind.REST)
        assertTrue(TrainingPreview.of(steps).last().items.all { it.rests.isEmpty() })
    }

    @Test
    fun `el circuito dice sus rounds, y cada ejercicio su receta`() {
        val niko6 = MasterDefaults.nikoTrainings("es").single { it.name.startsWith("NIKO 6") }
        val bloques = TrainingPreview.of(StepEngine.buildSteps(niko6, answerWindow = true))
        val circuito = bloques.single { it.circuit }
        assertEquals(4, circuito.rounds)
        assertEquals("4 × 3 min", TrainingPreview.describe(item(bloques, "ex_heavy_bag")))
        assertEquals("4 × 30 s · rest 1 min", TrainingPreview.describe(item(bloques, "ex_burpees")))
    }

    @Test
    fun `el tiempo del bloque cuenta tambien las repeticiones`() {
        val steps = StepEngine.buildSteps(MasterDefaults.lumbarTraining("en"), answerWindow = true)
        val cadera = TrainingPreview.of(steps).single { it.title == "Hip & Glute" }
        assertEquals(steps.filter { it.workoutIndex == cadera.index }.sumOf { it.estimatedSec }, cadera.estimatedSec)
        assertTrue(cadera.estimatedSec > steps.filter { it.workoutIndex == cadera.index && it.timeBased }.sumOf { it.durationSec })
    }

    @Test
    fun `formas de la receta`() {
        fun it(kind: PreviewItem.Kind, work: List<Int>) = PreviewItem("x", "", true, kind, work, emptyList(), 1, false, emptyList())
        assertEquals("12 reps", TrainingPreview.describe(it(PreviewItem.Kind.REPS, listOf(12))))
        assertEquals("12/10/8 reps", TrainingPreview.describe(it(PreviewItem.Kind.REPS, listOf(12, 10, 8))))
        assertEquals("6 × 10 s + 4 × 15 s", TrainingPreview.describe(it(PreviewItem.Kind.TIME, List(6) { 10 } + List(4) { 15 })))
        assertEquals("1:30", TrainingPreview.duration(90))
    }
}
