package com.maurozegarra.master.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRecorderTest {

    private fun workStep(
        exerciseId: String = "squat",
        ownerName: String = "Squat",
        workoutName: String = "Lower",
        workoutIndex: Int = 0,
        exerciseIndex: Int = 0,
        setIndex: Int = 0,
        totalSets: Int = 3,
        reps: Int = 12,
        durationSec: Int = 30,
        timeBased: Boolean = true,
        weighted: Boolean = false,
        weightTotal: Double = 0.0,
    ) = PlayerStep(
        kind = StepKind.WORK,
        title = ownerName,
        ownerName = ownerName,
        ownerExerciseId = exerciseId,
        workoutName = workoutName,
        workoutIndex = workoutIndex,
        exerciseIndex = exerciseIndex,
        setIndex = setIndex,
        totalSets = totalSets,
        reps = reps,
        durationSec = durationSec,
        timeBased = timeBased,
        weighted = weighted,
        weightTotal = weightTotal,
    )

    // ---------- Como se sintio la sesion (TD-089) ----------

    @Test
    fun `una sesion sin contestar no tiene feedback`() {
        val s = SessionLog(id = 1, trainingId = 1, trainingName = "T", completedAt = 1L)

        assertFalse(s.hasFeedback())
        assertNull(s.painBefore)
        assertNull(s.painAfter)
        assertNull(s.radiating)
    }

    @Test
    fun `un cero es un dato y se distingue de no haber contestado`() {
        // Es lo que separa "hoy no me dolio" de "no lo apunte", y son cosas distintas.
        val cero = SessionLog(id = 1, trainingId = 1, trainingName = "T", completedAt = 1L, painAfter = 0)

        val back = SessionJson.decode(SessionJson.encode(listOf(cero))).single()

        assertEquals(0, back.painAfter)
        assertNull(back.painBefore)
        assertTrue(back.hasFeedback())
    }

    @Test
    fun `lo contestado sobrevive la ida y vuelta a json`() {
        val s = SessionLog(
            id = 1, trainingId = 1, trainingName = "LUMBAR", completedAt = 1L,
            painBefore = 4, painAfter = 2, radiating = false, note = "5 km/h, caderas sueltas",
        )

        val back = SessionJson.decode(SessionJson.encode(listOf(s))).single()

        assertEquals(s, back)
    }

    @Test
    fun `una sesion guardada antes de TD-089 se lee sin feedback`() {
        val json = """[{"id":1,"trainingId":1,"trainingName":"T","completedAt":1,"status":"COMPLETED","exercises":[]}]"""

        val back = SessionJson.decode(json).single()

        assertFalse(back.hasFeedback())
        assertEquals("", back.note)
    }

    @Test
    fun `solo se pregunta en los trainings que lo piden`() {
        assertFalse(Training(id = 1).tracksPain)
        val back = TrainingJson.decode(TrainingJson.encode(listOf(Training(id = 1, tracksPain = true)))).single()
        assertTrue(back.tracksPain)
    }

    // ---------- De donde salio el registro (TD-101) ----------

    @Test
    fun `una sesion es medida salvo que diga lo contrario`() {
        assertEquals(SessionSource.MEASURED, SessionLog(id = 1, trainingId = 1, trainingName = "T", completedAt = 1L).source)
    }

    @Test
    fun `el origen sobrevive la ida y vuelta a json`() {
        val log = SessionLog(id = 1, trainingId = 1, trainingName = "T", completedAt = 1L, source = SessionSource.RECONSTRUCTED)

        assertEquals(SessionSource.RECONSTRUCTED, SessionJson.decode(SessionJson.encode(listOf(log))).single().source)
    }

    @Test
    fun `una sesion guardada antes de TD-101 se lee como medida`() {
        // Y es verdad: todas las que hay guardadas las escribio el player. La unica
        // excepcion se corrige en su propia migracion.
        val json = """[{"id":1,"trainingId":1,"trainingName":"T","completedAt":1,"status":"COMPLETED","exercises":[]}]"""

        assertEquals(SessionSource.MEASURED, SessionJson.decode(json).single().source)
    }

    // ---------- Devolverle el orden a una sesion vieja (TD-102) ----------

    private fun record(id: String, name: String, workoutIndex: Int = 0) = ExerciseRecord(
        exerciseId = id, name = name, workoutName = "W", workoutIndex = workoutIndex,
        setsCompleted = 1, totalSets = 1, sets = listOf(SetRecord(reps = 1)), timeBased = false,
    )

    private fun trainingDe(vararg workouts: List<String>) = Training(
        id = 1,
        workouts = workouts.mapIndexed { wi, ids ->
            Workout(id = wi.toLong(), name = "W$wi", exercises = ids.mapIndexed { i, id ->
                Exercise(id = (wi * 100 + i).toLong(), exerciseId = id, name = id)
            })
        },
    )

    @Test
    fun `reorderedFrom devuelve el orden que manda el training`() {
        // Guardada en alfabetico, como quedaban antes de TD-099.
        val s = SessionLog(
            id = 1, trainingId = 1, trainingName = "LUMBAR", completedAt = 1L,
            exercises = listOf(record("bird_dog", "Bird Dog"), record("curl_up", "Curl-up")),
        )

        val ordenada = s.reorderedFrom(trainingDe(listOf("curl_up", "bird_dog")))!!

        assertEquals(listOf("Curl-up", "Bird Dog"), ordenada.exercises.map { it.name })
        assertEquals(listOf(0, 1), ordenada.exercises.map { it.exerciseIndex })
    }

    @Test
    fun `reorderedFrom es idempotente`() {
        val t = trainingDe(listOf("curl_up", "bird_dog"))
        val s = SessionLog(
            id = 1, trainingId = 1, trainingName = "LUMBAR", completedAt = 1L,
            exercises = listOf(record("bird_dog", "Bird Dog"), record("curl_up", "Curl-up")),
        )

        assertEquals(s.reorderedFrom(t), s.reorderedFrom(t)!!.reorderedFrom(t))
    }

    @Test
    fun `reorderedFrom se rinde si el ejercicio ya no esta en ese workout`() {
        // El training cambio desde aquel dia: reordenar seria inventar.
        val s = SessionLog(
            id = 1, trainingId = 1, trainingName = "LUMBAR", completedAt = 1L,
            exercises = listOf(record("curl_up", "Curl-up"), record("ya_no_esta", "Otro")),
        )

        assertNull(s.reorderedFrom(trainingDe(listOf("curl_up", "bird_dog"))))
    }

    @Test
    fun `reorderedFrom se rinde si el workout ya no existe`() {
        val s = SessionLog(
            id = 1, trainingId = 1, trainingName = "LUMBAR", completedAt = 1L,
            exercises = listOf(record("curl_up", "Curl-up", workoutIndex = 7)),
        )

        assertNull(s.reorderedFrom(trainingDe(listOf("curl_up"))))
    }

    @Test
    fun `reorderedFrom se rinde si el ejercicio aparece dos veces en el workout`() {
        // Sin saber cual de las dos apariciones era, cualquier posicion es un invento.
        val s = SessionLog(
            id = 1, trainingId = 1, trainingName = "LUMBAR", completedAt = 1L,
            exercises = listOf(record("walk", "Walk")),
        )

        assertNull(s.reorderedFrom(trainingDe(listOf("walk", "curl_up", "walk"))))
    }

    @Test
    fun `reorderedFrom respeta el orden de los workouts`() {
        val s = SessionLog(
            id = 1, trainingId = 1, trainingName = "LUMBAR", completedAt = 1L,
            exercises = listOf(record("b", "Del segundo", workoutIndex = 1), record("a", "Del primero", workoutIndex = 0)),
        )

        val ordenada = s.reorderedFrom(trainingDe(listOf("a"), listOf("b")))!!

        assertEquals(listOf("Del primero", "Del segundo"), ordenada.exercises.map { it.name })
    }

    // ---------- El orden del historial es el de la rutina (TD-099) ----------

    @Test
    fun `los ejercicios salen en el orden de la rutina, no por nombre`() {
        // Los tres de McGill: el curl-up abre y el bird dog cierra. Por nombre salia al
        // reves, y el historial contaba una sesion que nadie hizo.
        val r = SessionRecorder()
        listOf(
            "ex_curl_up" to "Curl-up",
            "ex_side_plank_l" to "Side Plank L",
            "ex_side_plank_r" to "Side Plank R",
            "ex_bird_dog" to "Bird Dog",
        ).forEachIndexed { i, (id, name) ->
            r.onWorkStepCompleted(workStep(exerciseId = id, ownerName = name, exerciseIndex = i, totalSets = 1))
        }

        assertEquals(
            listOf("Curl-up", "Side Plank L", "Side Plank R", "Bird Dog"),
            r.build().map { it.name },
        )
    }

    @Test
    fun `volver atras a mitad de corrida no reordena el historial`() {
        // Por eso se ordena y no se confia en el orden de insercion: quien salta adelante y
        // vuelve registra los ejercicios en un orden que no es el de la rutina.
        val r = SessionRecorder()
        r.onWorkStepCompleted(workStep(exerciseId = "c", ownerName = "Tercero", exerciseIndex = 2, totalSets = 1))
        r.onWorkStepCompleted(workStep(exerciseId = "a", ownerName = "Primero", exerciseIndex = 0, totalSets = 1))
        r.onWorkStepCompleted(workStep(exerciseId = "b", ownerName = "Segundo", exerciseIndex = 1, totalSets = 1))

        assertEquals(listOf("Primero", "Segundo", "Tercero"), r.build().map { it.name })
    }

    @Test
    fun `el workout manda sobre la posicion dentro de el`() {
        val r = SessionRecorder()
        r.onWorkStepCompleted(workStep(exerciseId = "z", ownerName = "Del segundo workout", workoutIndex = 1, exerciseIndex = 0, totalSets = 1))
        r.onWorkStepCompleted(workStep(exerciseId = "y", ownerName = "Del primero", workoutIndex = 0, exerciseIndex = 9, totalSets = 1))

        assertEquals(listOf("Del primero", "Del segundo workout"), r.build().map { it.name })
    }

    @Test
    fun `la posicion sobrevive la ida y vuelta a json`() {
        val r = SessionRecorder()
        r.onWorkStepCompleted(workStep(exerciseId = "ex_curl_up", ownerName = "Curl-up", exerciseIndex = 3, totalSets = 1))
        val log = SessionLog(id = 1, trainingId = 1, trainingName = "LUMBAR", completedAt = 1L, exercises = r.build())

        val back = SessionJson.decode(SessionJson.encode(listOf(log))).single()

        assertEquals(3, back.exercises.single().exerciseIndex)
    }

    @Test
    fun `una sesion guardada antes de TD-099 se lee sin posicion y no se reordena`() {
        val json = """[{"id":1,"trainingId":1,"trainingName":"T","completedAt":1,"status":"COMPLETED","exercises":[
          {"exerciseId":"b","name":"Bird Dog","workoutIndex":0,"totalSets":1,"sets":[{"reps":0,"weightKg":0,"durationSec":10}]},
          {"exerciseId":"c","name":"Curl-up","workoutIndex":0,"totalSets":1,"sets":[{"reps":0,"weightKg":0,"durationSec":10}]}
        ]}]"""

        val back = SessionJson.decode(json).single()

        assertTrue(back.exercises.all { it.exerciseIndex == 0 })
        assertEquals(listOf("Bird Dog", "Curl-up"), back.exercises.map { it.name })
    }

    @Test
    fun `empty recorder builds empty list`() {
        val r = SessionRecorder()
        assertTrue(r.isEmpty())
        assertTrue(r.build().isEmpty())
    }

    @Test
    fun `single work step produces one exercise record with one set`() {
        val r = SessionRecorder()
        r.onWorkStepCompleted(workStep(setIndex = 0, totalSets = 3, reps = 12, timeBased = true, durationSec = 30))
        val records = r.build()
        assertEquals(1, records.size)
        val er = records[0]
        assertEquals("squat", er.exerciseId)
        assertEquals("Squat", er.name)
        assertEquals(1, er.setsCompleted)
        assertEquals(3, er.totalSets)
        assertEquals(1, er.sets.size)
        assertEquals(12, er.sets[0].reps)
        assertEquals(30, er.sets[0].durationSec)
    }

    @Test
    fun `multiple sets of same exercise accumulate into one record`() {
        val r = SessionRecorder()
        r.onWorkStepCompleted(workStep(setIndex = 0, totalSets = 3))
        r.onWorkStepCompleted(workStep(setIndex = 1, totalSets = 3))
        r.onWorkStepCompleted(workStep(setIndex = 2, totalSets = 3))
        val records = r.build()
        assertEquals(1, records.size)
        assertEquals(3, records[0].setsCompleted)
        assertEquals(3, records[0].sets.size)
    }

    @Test
    fun `different exercises produce separate records`() {
        val r = SessionRecorder()
        r.onWorkStepCompleted(workStep(exerciseId = "squat", ownerName = "Squat", workoutIndex = 0))
        r.onWorkStepCompleted(workStep(exerciseId = "press", ownerName = "Press", workoutIndex = 0))
        val records = r.build()
        assertEquals(2, records.size)
    }

    @Test
    fun `same exercise in different workouts produces separate records`() {
        val r = SessionRecorder()
        r.onWorkStepCompleted(workStep(exerciseId = "squat", ownerName = "Squat", workoutIndex = 0))
        r.onWorkStepCompleted(workStep(exerciseId = "squat", ownerName = "Squat", workoutIndex = 1))
        val records = r.build()
        assertEquals(2, records.size)
        assertEquals(0, records[0].workoutIndex)
        assertEquals(1, records[1].workoutIndex)
    }

    @Test
    fun `reps-based step records weight and no duration`() {
        val r = SessionRecorder()
        r.onWorkStepCompleted(workStep(
            timeBased = false,
            reps = 10,
            weighted = true,
            weightTotal = 42.5,
            durationSec = 0,
        ))
        val er = r.build()[0]
        assertEquals(10, er.sets[0].reps)
        assertEquals(42.5, er.sets[0].weightKg, 0.001)
        assertEquals(0, er.sets[0].durationSec)
    }

    @Test
    fun `time-based step records duration and weight`() {
        val r = SessionRecorder()
        r.onWorkStepCompleted(workStep(
            timeBased = true,
            reps = 0,
            durationSec = 45,
            weighted = true,
            weightTotal = 20.0,
        ))
        val er = r.build()[0]
        assertEquals(45, er.sets[0].durationSec)
        assertEquals(20.0, er.sets[0].weightKg, 0.001)
    }

    @Test
    fun `non-work steps are ignored`() {
        val r = SessionRecorder()
        val restStep = workStep().copy(kind = StepKind.REST)
        r.onWorkStepCompleted(restStep)
        assertTrue(r.isEmpty())
    }

    // ---------- Feedback del peso por serie (TD-117) ----------
    //
    // Los dos tests de antes probaban justo el orden en que NO se usa: marcar DESPUES de
    // completar la serie. En el player la tarjeta se toca DURANTE la serie, y en ese orden
    // no sobrevivia ningun toque -el 17-sep el usuario marco casi todas las series y en el
    // historial no habia ni una-. "setFeedback on non-existing record is no-op" afirmaba
    // como correcto el agujero que perdia la primera serie. Se sustituyen por los que
    // siguen, que reproducen el uso real.

    @Test
    fun `lo marcado durante la primera serie sobrevive a completarla`() {
        val r = SessionRecorder()
        r.setFeedback("bridge", 0, 0, 2.5)
        r.onWorkStepCompleted(workStep(exerciseId = "bridge", setIndex = 0, weightTotal = 6.0))
        assertEquals(2.5, r.build()[0].sets[0].feedbackDeltaKg!!, 0.001)
    }

    @Test
    fun `lo marcado en una serie sobrevive a las series siguientes`() {
        val r = SessionRecorder()
        r.setFeedback("bridge", 0, 0, 2.5)
        r.onWorkStepCompleted(workStep(exerciseId = "bridge", setIndex = 0))
        r.onWorkStepCompleted(workStep(exerciseId = "bridge", setIndex = 1))
        r.onWorkStepCompleted(workStep(exerciseId = "bridge", setIndex = 2))
        assertEquals(2.5, r.build()[0].sets[0].feedbackDeltaKg!!, 0.001)
    }

    @Test
    fun `cada serie guarda lo suyo, y la que no se marco queda en null`() {
        // La piramide del puente del 17-sep: el 6 ligero, el 16 ligero, el 31 sin marcar.
        val r = SessionRecorder()
        listOf(6.0, 16.0, 31.0).forEachIndexed { i, kg ->
            if (i < 2) r.setFeedback("bridge", 0, i, 2.5)
            r.onWorkStepCompleted(workStep(exerciseId = "bridge", setIndex = i, weightTotal = kg))
        }
        val sets = r.build()[0].sets
        assertEquals(2.5, sets[0].feedbackDeltaKg!!, 0.001)
        assertEquals(2.5, sets[1].feedbackDeltaKg!!, 0.001)
        // No marcar no es "justo": un 0 seria un dato que nadie dio.
        assertNull(sets[2].feedbackDeltaKg)
    }

    @Test
    fun `volver a tocar la tarjeta en la misma serie se queda con el ultimo toque`() {
        val r = SessionRecorder()
        r.setFeedback("bridge", 0, 0, -2.5)
        r.setFeedback("bridge", 0, 0, 0.0)
        r.onWorkStepCompleted(workStep(exerciseId = "bridge", setIndex = 0))
        assertEquals(0.0, r.build()[0].sets[0].feedbackDeltaKg!!, 0.001)
    }

    @Test
    fun `el resumen del ejercicio es el de la ultima serie marcada`() {
        val r = SessionRecorder()
        r.setFeedback("bridge", 0, 0, 2.5)
        r.setFeedback("bridge", 0, 1, 0.0)
        (0..2).forEach { r.onWorkStepCompleted(workStep(exerciseId = "bridge", setIndex = it)) }
        assertEquals(0.0, r.build()[0].feedbackDeltaKg!!, 0.001)
    }

    @Test
    fun `marcar sin completar ninguna serie no inventa un ejercicio`() {
        val r = SessionRecorder()
        r.setFeedback("bridge", 0, 0, 2.5)
        assertTrue(r.isEmpty())
    }

    @Test
    fun `el feedback no se cruza entre ejercicios ni entre workouts`() {
        val r = SessionRecorder()
        r.setFeedback("bridge", 3, 0, 2.5)
        r.onWorkStepCompleted(workStep(exerciseId = "bridge", workoutIndex = 3, setIndex = 0))
        r.onWorkStepCompleted(workStep(exerciseId = "bridge", workoutIndex = 5, setIndex = 0))
        r.onWorkStepCompleted(workStep(exerciseId = "carry", workoutIndex = 3, setIndex = 0))
        val porClave = r.build().associateBy { it.exerciseId to it.workoutIndex }
        assertEquals(2.5, porClave.getValue("bridge" to 3).sets[0].feedbackDeltaKg!!, 0.001)
        assertNull(porClave.getValue("bridge" to 5).sets[0].feedbackDeltaKg)
        assertNull(porClave.getValue("carry" to 3).sets[0].feedbackDeltaKg)
    }

    @Test
    fun `clear tambien olvida el feedback`() {
        val r = SessionRecorder()
        r.setFeedback("bridge", 0, 0, 2.5)
        r.clear()
        r.onWorkStepCompleted(workStep(exerciseId = "bridge", setIndex = 0))
        assertNull(r.build()[0].sets[0].feedbackDeltaKg)
    }

    @Test
    fun `clear resets recorder`() {
        val r = SessionRecorder()
        r.onWorkStepCompleted(workStep())
        r.clear()
        assertTrue(r.isEmpty())
    }

    /**
     * Este test fijaba "por workoutIndex y luego por NOMBRE" hasta TD-099. Se cambia a
     * proposito, no se borra: ordenar por nombre era justamente el fallo que el usuario
     * reporto -en su sesion del 14-sep-2026 el bird dog salia antes que el curl-up-, y lo
     * que tiene que mandar es la posicion del ejercicio dentro del workout.
     */
    @Test
    fun `build sorts by workoutIndex then position in the workout`() {
        val r = SessionRecorder()
        r.onWorkStepCompleted(workStep(exerciseId = "press", ownerName = "Press", workoutIndex = 1, exerciseIndex = 0))
        r.onWorkStepCompleted(workStep(exerciseId = "squat", ownerName = "Squat", workoutIndex = 0, exerciseIndex = 0))
        r.onWorkStepCompleted(workStep(exerciseId = "deadlift", ownerName = "Deadlift", workoutIndex = 0, exerciseIndex = 1))
        val records = r.build()
        assertEquals(0, records[0].workoutIndex)
        assertEquals("Squat", records[0].name)
        assertEquals(0, records[1].workoutIndex)
        assertEquals("Deadlift", records[1].name)
        assertEquals(1, records[2].workoutIndex)
    }

    @Test
    fun `totalExercisesInWorkout is injected from setTotalExercisesByWorkout`() {
        val r = SessionRecorder()
        r.setTotalExercisesByWorkout(mapOf(0 to 8, 1 to 5))
        r.onWorkStepCompleted(workStep(exerciseId = "squat", workoutIndex = 0))
        r.onWorkStepCompleted(workStep(exerciseId = "press", workoutIndex = 1))
        val records = r.build()
        assertEquals(8, records[0].totalExercisesInWorkout)
        assertEquals(5, records[1].totalExercisesInWorkout)
    }

    @Test
    fun `totalExercisesInWorkout defaults to 0 when not set`() {
        val r = SessionRecorder()
        r.onWorkStepCompleted(workStep(exerciseId = "squat", workoutIndex = 0))
        val records = r.build()
        assertEquals(0, records[0].totalExercisesInWorkout)
    }

    @Test
    fun `completed exercise has COMPLETED status`() {
        val r = SessionRecorder()
        r.onWorkStepCompleted(workStep(setIndex = 0, totalSets = 3))
        r.onWorkStepCompleted(workStep(setIndex = 1, totalSets = 3))
        r.onWorkStepCompleted(workStep(setIndex = 2, totalSets = 3))
        val records = r.build()
        assertEquals(ExerciseStatus.COMPLETED, records[0].status)
    }

    @Test
    fun `partially completed exercise has PARTIAL status`() {
        val r = SessionRecorder()
        r.onWorkStepCompleted(workStep(setIndex = 0, totalSets = 3))
        val records = r.build()
        assertEquals(ExerciseStatus.PARTIAL, records[0].status)
    }

    @Test
    fun `skip single set records set as skipped with planned values`() {
        val r = SessionRecorder()
        r.onWorkStepSkipped(workStep(setIndex = 0, totalSets = 3, reps = 15, timeBased = false))
        val er = r.build()[0]
        assertEquals(0, er.setsCompleted)
        assertEquals(1, er.sets.size)
        assertTrue(er.sets[0].skipped)
        assertEquals(15, er.sets[0].reps)
    }

    @Test
    fun `skip all sets produces SKIPPED status`() {
        val r = SessionRecorder()
        r.onWorkStepSkipped(workStep(setIndex = 0, totalSets = 3))
        r.onWorkStepSkipped(workStep(setIndex = 1, totalSets = 3))
        r.onWorkStepSkipped(workStep(setIndex = 2, totalSets = 3))
        val er = r.build()[0]
        assertEquals(ExerciseStatus.SKIPPED, er.status)
        assertEquals(0, er.setsCompleted)
        assertEquals(3, er.sets.size)
        assertTrue(er.sets.all { it.skipped })
    }

    @Test
    fun `mixed completed and skipped sets produces PARTIAL status`() {
        val r = SessionRecorder()
        r.onWorkStepCompleted(workStep(setIndex = 0, totalSets = 3))
        r.onWorkStepSkipped(workStep(setIndex = 1, totalSets = 3))
        r.onWorkStepCompleted(workStep(setIndex = 2, totalSets = 3))
        val er = r.build()[0]
        assertEquals(ExerciseStatus.PARTIAL, er.status)
        assertEquals(2, er.setsCompleted)
        assertEquals(3, er.sets.size)
        assertFalse(er.sets[0].skipped)
        assertTrue(er.sets[1].skipped)
        assertFalse(er.sets[2].skipped)
    }

    @Test
    fun `go-back after skip replaces skipped set with completed`() {
        val r = SessionRecorder()
        r.onWorkStepSkipped(workStep(setIndex = 1, totalSets = 3))
        r.onWorkStepCompleted(workStep(setIndex = 1, totalSets = 3))
        val er = r.build()[0]
        assertEquals(1, er.setsCompleted)
        assertEquals(1, er.sets.size)
        assertFalse(er.sets[0].skipped)
    }

    @Test
    fun `skip remaining sets after some completed produces PARTIAL`() {
        val r = SessionRecorder()
        r.onWorkStepCompleted(workStep(setIndex = 0, totalSets = 3))
        r.onWorkStepSkipped(workStep(setIndex = 1, totalSets = 3))
        r.onWorkStepSkipped(workStep(setIndex = 2, totalSets = 3))
        val er = r.build()[0]
        assertEquals(ExerciseStatus.PARTIAL, er.status)
        assertEquals(1, er.setsCompleted)
        assertEquals(3, er.sets.size)
        assertFalse(er.sets[0].skipped)
        assertTrue(er.sets[1].skipped)
        assertTrue(er.sets[2].skipped)
    }

    @Test
    fun `skip on non-work step is ignored`() {
        val r = SessionRecorder()
        r.onWorkStepSkipped(workStep().copy(kind = StepKind.REST))
        assertTrue(r.isEmpty())
    }

    @Test
    fun `clear resets all state`() {
        val r = SessionRecorder()
        r.onWorkStepCompleted(workStep(setIndex = 0, totalSets = 3))
        r.onWorkStepSkipped(workStep(setIndex = 1, totalSets = 3))
        r.clear()
        assertTrue(r.isEmpty())
    }
}
