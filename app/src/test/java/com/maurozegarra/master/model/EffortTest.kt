package com.maurozegarra.master.model

import com.maurozegarra.master.data.MasterDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lo que no lleva peso tambien dice como fue (TD-152). */
class EffortTest {

    private fun ex(id: String, mode: WorkMode = WorkMode.REPS, sets: Int = 3, sides: List<String> = emptyList()) =
        Exercise(id = 1L, exerciseId = id, name = id, sets = sets, workMode = mode, workValue = if (mode == WorkMode.TIME) 25 else 8, sides = sides)

    private fun pasos(e: Exercise) =
        StepEngine.buildSteps(Training(id = 1L, name = "T", workouts = listOf(Workout(id = 1L, name = "W", exercises = listOf(e)))))

    @Test
    fun `cada ejercicio progresa por lo que es`() {
        assertEquals(Progression.REPS, Effort.of(ex("ex_inverted_row")))
        assertEquals(Progression.TIME, Effort.of(ex("ex_dead_hang", WorkMode.TIME)))
        assertEquals(Progression.ROUNDS, Effort.of(ex("ex_heavy_bag", WorkMode.TIME)))
        assertEquals(Progression.NONE, Effort.of(ex("ex_walk", WorkMode.TIME)))
        assertEquals(Progression.LOAD, Effort.of(ex("ex_dumbbell_row").copy(weightType = WeightType.DUMBBELL, setList = listOf(WorkSet(8, 10.0)))))
        // Lo declarado gana: McGill va por tiempo pero es un protocolo fijo.
        assertEquals(Progression.NONE, Effort.of(ex("ex_curl_up", WorkMode.TIME).copy(progression = Progression.NONE)))
    }

    @Test
    fun `repeticiones y aguantes preguntan en cada serie de cada lado`() {
        // Primero se pregunto solo en la ultima, y el usuario lo cambio el mismo dia: la
        // primera serie puede costar porque el musculo esta frio y las otras salir bien, y
        // con una sola respuesta eso se lee "bien" y se pierde.
        val trabajo = pasos(ex("ex_single_leg_hip_thrust", sides = listOf("Izquierda", "Derecha")))
            .filter { it.kind == StepKind.WORK }
        assertEquals(6, trabajo.size)
        assertTrue(trabajo.all(Effort::asks))
    }

    @Test
    fun `los rounds preguntan en cada uno`() {
        // "Necesito registro detallado": lo que interesa es en cual round se cae.
        val trabajo = pasos(ex("ex_heavy_bag", WorkMode.TIME, sets = 5)).filter { it.kind == StepKind.WORK }
        assertTrue(trabajo.all(Effort::asks))
    }

    @Test
    fun `el descanso de despues tambien ensena la tarjeta`() {
        // Durante un round no se puede tocar la pantalla: se contesta en el minuto de despues.
        val descansos = pasos(ex("ex_heavy_bag", WorkMode.TIME, sets = 5).copy(restSec = 60)).filter { it.kind == StepKind.REST }
        assertTrue(descansos.isNotEmpty())
        assertTrue(descansos.all(Effort::cardFor))
    }

    @Test
    fun `McGill y el calentamiento no preguntan, el resto de NIKO si`() {
        val lumbar = MasterDefaults.lumbarTraining("en")
        assertTrue(StepEngine.buildSteps(lumbar).none(Effort::asks))

        val niko = MasterDefaults.nikoTrainings("es")
        val calentamiento = niko.flatMap { StepEngine.buildSteps(it) }.filter { it.workoutName == "Calentamiento" }
        assertTrue(calentamiento.none(Effort::asks))

        val preguntan = StepEngine.buildSteps(niko.first { it.name.startsWith("NIKO 3") })
            .filter(Effort::asks).map { it.ownerExerciseId }.toSet()
        assertEquals(setOf("ex_inverted_row", "ex_pushups", "ex_dead_hang", "ex_neck_iso"), preguntan)
    }

    @Test
    fun `se guarda lo que paso, no lo planeado`() {
        val r = SessionRecorder()
        val remo = pasos(ex("ex_inverted_row")).last { it.kind == StepKind.WORK }
        r.setEffort(remo.ownerExerciseId, remo.workoutIndex, remo.setIndex, Effort.HARD, repsDone = 6)
        r.onWorkStepCompleted(remo)
        val barra = pasos(ex("ex_dead_hang", WorkMode.TIME, sets = 1)).first { it.kind == StepKind.WORK }
        r.onWorkStepCompleted(barra, actualSec = 18)

        val registro = r.build()
        val serie = registro.first { it.exerciseId == "ex_inverted_row" }.sets.last()
        assertEquals(Effort.HARD, serie.effort)
        assertEquals(6, serie.repsDone)
        val aguante = registro.first { it.exerciseId == "ex_dead_hang" }.sets.single()
        // El 22-sep el historial habria dicho 25 aunque se soltara a los 18.
        assertEquals(18, aguante.durationSec)
        assertEquals(25, aguante.plannedSec)
    }

    @Test
    fun `terminar el aguante entero no deja marca de corte`() {
        val r = SessionRecorder()
        val barra = pasos(ex("ex_dead_hang", WorkMode.TIME, sets = 1)).first { it.kind == StepKind.WORK }
        r.onWorkStepCompleted(barra, actualSec = 25)
        assertNull(r.build().single().sets.single().plannedSec)
    }

    @Test
    fun `el registro nombra las series una por una`() {
        val sets = listOf(Effort.RIGHT, Effort.RIGHT, Effort.HARD).map { SetRecord(durationSec = 180, effort = it) }
        val s = SessionLog(
            id = 1L, trainingId = 1L, trainingName = "NIKO 2", completedAt = 1L,
            exercises = listOf(ExerciseRecord(exerciseId = "ex_heavy_bag", name = "Heavy Bag", workoutName = "Rounds", workoutIndex = 0, setsCompleted = 3, totalSets = 3, sets = sets, timeBased = true)),
        )
        val l = EffortRecord.lines(s).single()
        assertTrue(l.numbered)
        assertEquals(listOf(Effort.RIGHT, Effort.RIGHT, Effort.HARD), l.marks.map { it.effort })
    }

    @Test
    fun `las respuestas sobreviven al guardado`() {
        val set = SetRecord(reps = 8, effort = Effort.HARD, repsDone = 6, durationSec = 18, plannedSec = 25)
        val s = SessionLog(
            id = 1L, trainingId = 1L, trainingName = "T", completedAt = 1L,
            exercises = listOf(ExerciseRecord(exerciseId = "x", name = "x", workoutName = "w", workoutIndex = 0, setsCompleted = 1, totalSets = 1, sets = listOf(set), timeBased = false)),
        )
        assertEquals(set, SessionJson.decode(SessionJson.encode(listOf(s))).single().exercises.single().sets.single())
        // Y lo declarado en la rutina: sin esto, McGill volveria a preguntar al releerse.
        val t = Training(id = 1L, name = "T", workouts = listOf(Workout(id = 1L, name = "W", exercises = listOf(ex("ex_curl_up").copy(progression = Progression.NONE)))))
        assertEquals(Progression.NONE, TrainingJson.decode(TrainingJson.encode(listOf(t))).single().workouts.single().exercises.single().progression)
        assertNull(TrainingJson.decode(TrainingJson.encode(listOf(t.copy(workouts = listOf(Workout(id = 1L, name = "W", exercises = listOf(ex("ex_pushups")))))))).single().workouts.single().exercises.single().progression)
    }
}
