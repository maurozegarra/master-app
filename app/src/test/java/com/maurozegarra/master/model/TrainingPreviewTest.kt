package com.maurozegarra.master.model

import com.maurozegarra.master.data.MasterDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** La pantalla previa dice lo que de verdad se va a hacer (TD-173). */
class TrainingPreviewTest {

    // Con el respiro para contestar, como la arma el player.
    private val lumbar = TrainingPreview.of(StepEngine.buildSteps(MasterDefaults.lumbarTraining("en", easedHip = false, easedAnkle = false), answerWindow = true))

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
        assertTrue(carry, carry.startsWith("3 × 36 m · sides alternating · 16/20/22.5 kg"))
    }

    @Test
    fun `la caminata dice cuanto y a que ritmo, con el formato de todo el app`() {
        // El 1-oct la previa decia "5 min" y el historial "5:00 · 5.5 km/h".
        assertEquals("12 min · 6 km/h", TrainingPreview.describe(item(lumbar, "ex_walk")))
        assertEquals("45 s", TrainingPreview.duration(45))
        assertEquals("5 min", TrainingPreview.duration(300))
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
        val steps = StepEngine.buildSteps(MasterDefaults.lumbarTraining("en", easedAnkle = false), answerWindow = true)
        val cadera = TrainingPreview.of(steps).single { it.title == "Hip & Glute" }
        assertEquals(steps.filter { it.workoutIndex == cadera.index }.sumOf { it.estimatedSec }, cadera.estimatedSec)
        assertTrue(cadera.estimatedSec > steps.filter { it.workoutIndex == cadera.index && it.timeBased }.sumOf { it.durationSec })
    }

    @Test
    fun `lo que falta empieza despues del paso actual, con las series que quedan`() {
        val steps = StepEngine.buildSteps(MasterDefaults.lumbarTraining("en", easedAnkle = false), answerWindow = true)
        // En la segunda serie de un ejercicio de tres: faltan la segunda y la tercera.
        val i = steps.indexOfFirst { it.kind == StepKind.WORK && it.totalSets == 3 && it.setIndex == 1 && it.sideCount <= 1 }
        val actual = steps[i]
        val falta = TrainingPreview.remaining(steps, i - 1)
        assertEquals(actual.workoutIndex, falta.first().index)
        assertEquals(actual.ownerExerciseId, falta.first().items.first().exerciseId)
        assertEquals(2, falta.first().items.first().work.size)
        // Lo de antes no está.
        assertTrue(falta.none { it.index < actual.workoutIndex })
        // Al final no falta nada.
        assertTrue(TrainingPreview.remaining(steps, steps.lastIndex).isEmpty())
    }

    @Test
    fun `a mitad de circuito cuenta los rounds que quedan`() {
        val niko6 = MasterDefaults.nikoTrainings("es").single { it.name.startsWith("NIKO 6") }
        val steps = StepEngine.buildSteps(niko6, answerWindow = true)
        val i = steps.indexOfFirst { it.circuit && it.kind == StepKind.WORK && it.slot == 2 }
        assertEquals(2, TrainingPreview.of(steps.drop(i)).single { it.circuit }.rounds)
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
