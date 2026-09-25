package com.maurozegarra.master.model

import com.maurozegarra.master.data.MasterDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lados alternados, el respiro para contestar y el puesto de cada serie (TD-156). */
class AlternateSidesTest {

    private fun carry() = MasterDefaults.lumbarShortTraining("en").workouts.flatMap { it.exercises }
        .single { it.exerciseId == "ex_suitcase_carry" }

    private fun solo(e: Exercise) = Training(id = 1L, name = "T", workouts = listOf(Workout(id = 1L, name = "W", exercises = listOf(e))))

    @Test
    fun `el carry alterna las manos dentro de cada serie`() {
        // El 24-sep, por lado, "demoro el doble": cinco descansos en vez de dos.
        val pasos = StepEngine.buildSteps(solo(carry())).filter { it.kind != StepKind.PREP }
        assertEquals(
            listOf("W Left 0", "W Right 0", "R", "W Left 1", "W Right 1", "R", "W Left 2", "W Right 2"),
            pasos.map { if (it.kind == StepKind.REST) "R" else "W ${it.side} ${it.setIndex}" },
        )
        // Y con la rampa de peso en cada mano.
        assertEquals(listOf(15.0, 15.0, 17.5, 17.5, 20.0, 20.0), pasos.filter { it.kind == StepKind.WORK }.map { it.weightTotal })
    }

    @Test
    fun `el puesto sigue el orden en que se hace`() {
        val trabajo = StepEngine.buildSteps(solo(carry())).filter { it.kind == StepKind.WORK }
        assertEquals((0..5).toList(), trabajo.map { it.slot })
    }

    @Test
    fun `reubicar con lados no vuelve a un lado ya hecho`() {
        // Plancha por lados, uno tras otro: la serie 1 de la derecha va DESPUES de toda la
        // izquierda. Comparando solo la serie, caia en la serie 1 de la izquierda.
        val plancha = Exercise(id = 1L, exerciseId = "ex_side_plank", name = "P", sets = 3, workMode = WorkMode.TIME, workValue = 30, restSec = 20, sides = listOf("L", "R"))
        val pasos = StepEngine.buildSteps(solo(plancha))
        val actual = pasos.first { it.kind == StepKind.WORK && it.side == "R" && it.setIndex == 1 }
        assertEquals(pasos.indexOf(actual), StepEngine.relocate(actual, pasos))
    }

    @Test
    fun `al final del training hay un respiro para contestar la ultima serie`() {
        // El cuello de NIKO: la ultima direccion terminaba la sesion "sin tiempo para marcar".
        val cuello = Exercise(id = 1L, exerciseId = "ex_neck_iso", name = "Cuello", sets = 1, workMode = WorkMode.TIME, workValue = 20, restSec = 15, sides = listOf("Adelante", "Atras", "Derecha", "Izquierda"))
        val conRespiro = StepEngine.buildSteps(solo(cuello), answerWindow = true)
        val ultimo = conRespiro.last()
        assertEquals(StepKind.REST, ultimo.kind)
        assertEquals(StepEngine.ANSWER_SEC, ultimo.durationSec)
        assertEquals("Izquierda", ultimo.side)
        assertTrue(Effort.cardFor(ultimo))
        // El motor, sin pedirlo, no lo pone: los estimados no cuentan con el.
        assertEquals(StepKind.WORK, StepEngine.buildSteps(solo(cuello)).last().kind)
    }

    @Test
    fun `lo que no pregunta no lleva respiro`() {
        val caminata = Exercise(id = 1L, exerciseId = "ex_walk", name = "Walk", sets = 1, workMode = WorkMode.TIME, workValue = 300)
        assertEquals(StepKind.WORK, StepEngine.buildSteps(solo(caminata), answerWindow = true).last().kind)
    }

    @Test
    fun `las caminatas de la lumbar tienen preparacion`() {
        // "Apenas le doy al play, el tiempo ya esta corriendo" (24-sep).
        val primero = StepEngine.buildSteps(MasterDefaults.lumbarTraining("en")).first()
        assertEquals(StepKind.PREP, primero.kind)
        assertEquals("ex_walk", primero.ownerExerciseId)
    }
}
