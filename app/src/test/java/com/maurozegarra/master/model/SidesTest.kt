package com.maurozegarra.master.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ejercicios que se hacen por lado o por dirección (TD-147).
 *
 * Lo que se protege: que declarar los lados no cambie nada de lo bilateral, que el orden
 * sea por lado —no alternando—, y que cada lado se cuente aparte en el historial, que es
 * lo único que hace visible una asimetría.
 */
class SidesTest {

    private fun training(e: Exercise) = Training(
        id = 1L,
        name = "T",
        workouts = listOf(Workout(id = 1L, name = "W", exercises = listOf(e))),
    )

    private val plancha = Exercise(
        id = 1L,
        exerciseId = "ex_side_plank",
        name = "Side Plank",
        sets = 3,
        workMode = WorkMode.TIME,
        workValue = 10,
        restSec = 5,
        restSkipOnLastSet = true,
        sides = listOf("L", "R"),
    )

    @Test
    fun `sin lados no cambia nada`() {
        val bilateral = plancha.copy(sides = emptyList())
        val pasos = StepEngine.buildSteps(training(bilateral)).filter { it.kind == StepKind.WORK }

        assertEquals(3, pasos.size)
        assertTrue(pasos.all { it.side.isBlank() })
    }

    @Test
    fun `primero todas las series de un lado y luego las del otro`() {
        // Alternar obligaria a montarse y desmontarse en cada aguante. Una piramide de
        // McGill se hace de un lado entera y despues del otro, que es como ya se entrenaba
        // cuando esto eran dos ejercicios clonados.
        val pasos = StepEngine.buildSteps(training(plancha)).filter { it.kind == StepKind.WORK }

        assertEquals(listOf("L", "L", "L", "R", "R", "R"), pasos.map { it.side })
        assertEquals(listOf(0, 1, 2, 0, 1, 2), pasos.map { it.setIndex })
    }

    @Test
    fun `el descanso entre lados se queda, el del final se salta`() {
        // restSkipOnLastSet mira el ultimo del ULTIMO lado: entre un lado y el siguiente hay
        // que cambiar de postura, y ahi el descanso es justo lo que hace falta.
        val pasos = StepEngine.buildSteps(training(plancha))
        val descansos = pasos.count { it.kind == StepKind.REST }

        assertEquals(5, descansos)
        assertEquals(StepKind.WORK, pasos.last().kind)
    }

    @Test
    fun `cada lado es un registro propio`() {
        val recorder = SessionRecorder()
        StepEngine.buildSteps(training(plancha))
            .filter { it.kind == StepKind.WORK }
            .forEach { recorder.onWorkStepCompleted(it) }

        val registros = recorder.build()
        assertEquals(2, registros.size)
        assertEquals(listOf("L", "R"), registros.map { it.side })
        assertTrue(registros.all { it.sets.size == 3 })
    }

    @Test
    fun `una direccion no se lleva el feedback de otra`() {
        // Misma serie, distinto lado: si la clave no llevara el lado, marcar el peso en la
        // izquierda aparecia marcado tambien en la derecha.
        val recorder = SessionRecorder()
        val conPeso = plancha.copy(workMode = WorkMode.REPS, workValue = 10, weightType = WeightType.DUMBBELL)
        val pasos = StepEngine.buildSteps(training(conPeso)).filter { it.kind == StepKind.WORK }

        recorder.setFeedback("ex_side_plank", 0, 0, -2.5, side = "L")
        pasos.forEach { recorder.onWorkStepCompleted(it) }

        val porLado = recorder.build().associateBy { it.side }
        assertEquals(-2.5, porLado.getValue("L").sets[0].feedbackDeltaKg!!, 0.0)
        assertEquals(null, porLado.getValue("R").sets[0].feedbackDeltaKg)
    }

    @Test
    fun `los lados sobreviven al guardado`() {
        // Se escaparon en la primera version: el ejercicio tenia sus cuatro direcciones en
        // memoria y el JSON no las guardaba, asi que al recargar quedaba UNA serie suelta.
        // En el telefono de NIKO eso era hacer una direccion de cuatro.
        val vuelta = TrainingJson.decode(TrainingJson.encode(listOf(training(plancha)))).single()

        assertEquals(listOf("L", "R"), vuelta.workouts.single().exercises.single().sides)
    }
}
