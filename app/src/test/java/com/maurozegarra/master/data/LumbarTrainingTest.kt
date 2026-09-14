package com.maurozegarra.master.data

import com.maurozegarra.master.model.StepEngine
import com.maurozegarra.master.model.StepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El training LUMBAR que se siembra en el dispositivo (TD-086).
 *
 * Se prueba aqui y no a mano en el telefono porque es lo que hace que sembrar sea
 * automatizable: si la piramide sale mal, falla el build y no llega a instalarse.
 */
class LumbarTrainingTest {

    private val training = MasterDefaults.lumbarTraining("en")
    private val steps = StepEngine.buildSteps(training.copy(workouts = training.workouts))

    @Test
    fun `tiene los cinco bloques de la rutina`() {
        assertEquals(
            listOf("Warm Walk", "Mobility", "McGill Big 3", "Hip & Glute", "Cool Walk"),
            training.workouts.map { it.name },
        )
        assertEquals(listOf(1, 2, 4, 3, 1), training.workouts.map { it.exercises.size })
    }

    @Test
    fun `las caminatas son de 12 y de 5 minutos`() {
        assertEquals(720, training.workouts.first().exercises.single().workValue)
        assertEquals(300, training.workouts.last().exercises.single().workValue)
    }

    @Test
    fun `los tres de McGill van en piramide descendente`() {
        val mcgill = training.workouts[2]

        mcgill.exercises.forEach { e ->
            assertEquals(12, e.sets)
            assertEquals(10, e.workValue)
            assertEquals(3, e.restSec)
            // El respiro largo cierra el bloque de 6 y el de 4, y nada mas lo lleva.
            assertEquals(listOf(5, 9), e.setList.indices.filter { e.setList[it].restSec != null })
            assertTrue(e.setList.filter { it.restSec != null }.all { it.restSec == 30 })
        }
    }

    @Test
    fun `cada movimiento de McGill dura 3 minutos y 47 segundos`() {
        val porMovimiento = training.workouts[2].exercises.map { e ->
            val t = StepEngine.buildSteps(
                training.copy(workouts = listOf(training.workouts[2].copy(exercises = listOf(e)))),
            )
            t.sumOf { it.durationSec }
        }

        // 20 s para acomodarse + 3:27 de piramide.
        assertTrue(porMovimiento.all { it == 227 })
    }

    @Test
    fun `la plancha lateral se hace a los dos lados y cada uno cuenta aparte`() {
        val mcgill = training.workouts[2]
        val ids = mcgill.exercises.map { it.exerciseId }

        assertTrue("ex_side_plank_l" in ids && "ex_side_plank_r" in ids)
        // Ids distintos es lo que mantiene separadas sus series en el historial, que
        // agrupa por ejercicio dentro del workout.
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `el bloque de cadera va por repeticiones con un minuto de descanso`() {
        training.workouts[3].exercises.forEach { e ->
            assertEquals(3, e.sets)
            assertEquals(60, e.restSec)
        }
    }

    @Test
    fun `lo cronometrado suma 38 minutos y 58 segundos`() {
        assertEquals(2338, steps.sumOf { it.durationSec })
    }

    @Test
    fun `todos los ejercicios tienen nombre del catalogo`() {
        val todos = training.workouts.flatMap { it.exercises }

        todos.forEach { e ->
            assertEquals(ExerciseCatalog.name(e.exerciseId, "en"), e.name)
            // name() devuelve el propio id cuando no lo conoce: eso seria un ejercicio
            // fuera del catalogo, y en el player se leeria "ex_curl_up".
            assertTrue(e.name != e.exerciseId)
        }
    }

    @Test
    fun `las instrucciones cubren todos los ejercicios del training`() {
        val ids = training.workouts.flatMap { it.exercises }.map { it.exerciseId }.toSet()

        assertTrue(MasterDefaults.lumbarInstructions().keys.containsAll(ids))
    }

    @Test
    fun `los ids no chocan con los de los otros defaults`() {
        fun ids(t: com.maurozegarra.master.model.Training) = buildList {
            add(t.id)
            t.workouts.forEach { w ->
                add(w.id)
                w.exercises.forEach { add(it.id) }
                w.variants.forEach { v -> add(v.id); v.exercises.forEach { add(it.id) } }
            }
        }

        val otros = ids(MasterDefaults.masterTraining("en")) + ids(MasterDefaults.frikiNikiTraining("en"))

        assertTrue(ids(training).none { it in otros })
    }

    @Test
    fun `el primer ejercicio lleva las dos reglas de la rutina`() {
        val pasos = MasterDefaults.lumbarInstructions().getValue("ex_walk").instructions

        assertTrue(pasos.any { it.contains("first hour after waking up") })
        assertTrue(pasos.any { it.contains("radiates down the leg") })
    }

    @Test
    fun `el id del training no se mueve`() {
        // Es el que quedo sembrado en el dispositivo del usuario.
        assertEquals(950016L, training.id)
        assertEquals(MasterDefaults.LUMBAR_ID, training.id)
    }

    // ---------- La variante de dia malo (TD-088) ----------

    private val badDay = MasterDefaults.lumbarBadDayTraining("en")

    @Test
    fun `el dia malo abre con movilidad y camina despues`() {
        assertEquals(
            listOf("Mobility", "Short Walk", "McGill Big 3", "Hip & Glute"),
            badDay.workouts.map { it.name },
        )
    }

    @Test
    fun `el dia malo camina 6 minutos en vez de 12`() {
        assertEquals(360, badDay.workouts[1].exercises.single().workValue)
    }

    @Test
    fun `el dia malo no sube nada en McGill`() {
        fun forma(t: com.maurozegarra.master.model.Training) =
            t.workouts.first { it.name == "McGill Big 3" }.exercises.map {
                Triple(it.exerciseId, it.sets to it.workValue, it.setList.map { s -> s.restSec })
            }

        assertEquals(forma(training), forma(badDay))
    }

    @Test
    fun `el bloque de cadera va al final, donde se puede saltar`() {
        assertEquals("Hip & Glute", badDay.workouts.last().name)
    }

    @Test
    fun `los dos trainings lumbares no comparten ningun id`() {
        fun ids(t: com.maurozegarra.master.model.Training) =
            listOf(t.id) + t.workouts.flatMap { w -> listOf(w.id) + w.exercises.map { it.id } }

        assertTrue(ids(training).none { it in ids(badDay) })
    }
}
