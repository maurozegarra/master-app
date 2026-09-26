package com.maurozegarra.master.model

import com.maurozegarra.master.data.MasterDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Circuitos (TD-137) y "lo que se hizo, se hizo" en el registro. */
class CircuitTest {

    private val saco = Exercise(id = 1L, exerciseId = "ex_heavy_bag", name = "Saco", sets = 3, workMode = WorkMode.TIME, workValue = 180, restSec = 0, prepareSec = 10)
    private val sprawl = Exercise(id = 2L, exerciseId = "ex_burpees", name = "Sprawl", sets = 3, workMode = WorkMode.TIME, workValue = 30, restSec = 60, prepareSec = 10)

    private fun t(circuit: Boolean) = Training(
        id = 1L, name = "T",
        workouts = listOf(Workout(id = 1L, name = "W", exercises = listOf(saco, sprawl), circuit = circuit)),
    )

    private fun etiquetas(ps: List<PlayerStep>) = ps.map {
        when (it.kind) {
            StepKind.PREP -> "P ${it.ownerName}"
            StepKind.WORK -> "${it.ownerName} ${it.setIndex + 1}"
            StepKind.REST -> "R"
            StepKind.COOLDOWN -> "C"
        }
    }

    @Test
    fun `en circuito se alterna round a round`() {
        // El plan de NIKO 6 pedia saco, sprawl, saco desde el 19-sep.
        assertEquals(
            listOf("P Saco", "Saco 1", "P Sprawl", "Sprawl 1", "R", "Saco 2", "Sprawl 2", "R", "Saco 3", "Sprawl 3"),
            etiquetas(StepEngine.buildSteps(t(circuit = true))),
        )
    }

    @Test
    fun `mismos pasos que en bloques, solo en otro orden`() {
        val bloques = StepEngine.buildSteps(t(circuit = false))
        val circuito = StepEngine.buildSteps(t(circuit = true))
        assertEquals(bloques.map { it.copy(circuit = true) }.sortedBy { it.hashCode() }, circuito.sortedBy { it.hashCode() })
    }

    @Test
    fun `reubicar en circuito no vuelve a un round ya hecho`() {
        val ps = StepEngine.buildSteps(t(circuit = true))
        val actual = ps.first { it.kind == StepKind.WORK && it.ownerName == "Saco" && it.setIndex == 1 }
        assertEquals(ps.indexOf(actual), StepEngine.relocate(actual, ps))
    }

    @Test
    fun `volver a una serie hecha no la acorta`() {
        // El 25-sep NIKO hizo la cuerda entera y volvio atras solo para marcar: el registro
        // de 180 s se piso con uno de 1 s.
        val r = SessionRecorder()
        val round = StepEngine.buildSteps(t(circuit = false)).first { it.kind == StepKind.WORK && it.ownerName == "Saco" }
        r.onWorkStepCompleted(round, actualSec = 180)
        r.onWorkStepCompleted(round, actualSec = 1)
        val serie = r.build().single { it.exerciseId == "ex_heavy_bag" }.sets.first()
        assertEquals(180, serie.durationSec)
        assertEquals(null, serie.plannedSec)
        // Y saltarla despues tampoco la desmarca.
        r.onWorkStepSkipped(round)
        assertTrue(!r.build().single { it.exerciseId == "ex_heavy_bag" }.sets.first().skipped)
    }

    @Test
    fun `NIKO 6 alterna el saco con el sprawl`() {
        val niko6 = MasterDefaults.nikoTrainings("es").single { it.name.startsWith("NIKO 6") }
        val trabajo = StepEngine.buildSteps(niko6).filter { it.kind == StepKind.WORK && it.ownerExerciseId in setOf("ex_heavy_bag", "ex_burpees") }
        assertEquals(
            listOf("ex_heavy_bag", "ex_burpees", "ex_heavy_bag", "ex_burpees", "ex_heavy_bag", "ex_burpees", "ex_heavy_bag", "ex_burpees"),
            trabajo.map { it.ownerExerciseId },
        )
    }
}
