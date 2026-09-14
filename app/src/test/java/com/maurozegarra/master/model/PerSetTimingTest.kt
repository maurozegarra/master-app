package com.maurozegarra.master.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Una serie puede llevarse su propio trabajo y su propio descanso (TD-085).
 *
 * El caso que lo pidio es la piramide descendente de los tres de McGill -6 aguantes de
 * 10 s, 30 s de respiro, 4, otros 30 s, 2-, que antes obligaba a partir cada movimiento
 * en tres ejercicios distintos.
 *
 * Lo que se fija aqui, mas que los numeros, es que **null hereda y 0 no**: son los dos
 * valores que la UI y el JSON pueden confundir, y confundirlos deja al ejercicio sin
 * descanso o con uno que no pidio.
 */
class PerSetTimingTest {

    private fun ex(
        sets: Int,
        workMode: WorkMode = WorkMode.TIME,
        workValue: Int = 10,
        restSec: Int = 3,
        restSkipOnLastSet: Boolean = true,
        weightType: WeightType = WeightType.NONE,
        setList: List<WorkSet> = emptyList(),
    ) = Exercise(
        id = 1,
        exerciseId = "ex_curl_up",
        name = "Curl-up",
        prepareSec = 0,
        sets = sets,
        workMode = workMode,
        workValue = workValue,
        restSec = restSec,
        restSkipOnLastSet = restSkipOnLastSet,
        cooldownSec = 0,
        weightType = weightType,
        setList = setList,
    )

    private fun steps(e: Exercise) =
        StepEngine.buildSteps(Training(id = 1, name = "Lumbar", workouts = listOf(Workout(id = 1, name = "McGill", exercises = listOf(e)))))

    /** Las 12 series de la piramide: 30 s de descanso solo tras la 6 y la 10. */
    private fun pyramid(): Exercise {
        val list = (0 until 12).map { i ->
            if (i == 5 || i == 9) WorkSet(restSec = 30) else WorkSet()
        }
        return ex(sets = 12, setList = list)
    }

    // ---------- El caso que lo pidio ----------

    @Test
    fun `la piramide descendente cabe en un solo ejercicio`() {
        val s = steps(pyramid())

        assertEquals(12, s.count { it.kind == StepKind.WORK })
        // Descanso tras cada serie menos la ultima, que se lo salta.
        assertEquals(11, s.count { it.kind == StepKind.REST })
        assertTrue(s.filter { it.kind == StepKind.WORK }.all { it.durationSec == 10 })

        val rests = s.filter { it.kind == StepKind.REST }
        assertEquals(listOf(3, 3, 3, 3, 3, 30, 3, 3, 3, 30, 3), rests.map { it.durationSec })
        // El respiro largo cae donde termina el bloque de 6 y el de 4, no en otro sitio.
        assertEquals(listOf(5, 9), rests.filter { it.durationSec == 30 }.map { it.setIndex })
    }

    @Test
    fun `la piramide dura 3 minutos y 27 segundos`() {
        assertEquals(207, steps(pyramid()).sumOf { it.durationSec })
    }

    // ---------- null hereda, 0 no ----------

    @Test
    fun `sin valor propio la serie sigue al ejercicio`() {
        val s = steps(ex(sets = 3, setList = listOf(WorkSet(), WorkSet(), WorkSet())))

        assertTrue(s.filter { it.kind == StepKind.WORK }.all { it.durationSec == 10 })
        assertTrue(s.filter { it.kind == StepKind.REST }.all { it.durationSec == 3 })
    }

    @Test
    fun `una serie puede durar distinto que el ejercicio`() {
        val s = steps(ex(sets = 3, setList = listOf(WorkSet(sec = 30), WorkSet(), WorkSet(sec = 20))))

        assertEquals(listOf(30, 10, 20), s.filter { it.kind == StepKind.WORK }.map { it.durationSec })
    }

    @Test
    fun `un descanso propio genera etapa aunque el ejercicio no descanse`() {
        val s = steps(ex(sets = 3, restSec = 0, setList = listOf(WorkSet(), WorkSet(restSec = 30), WorkSet())))

        val rests = s.filter { it.kind == StepKind.REST }
        assertEquals(1, rests.size)
        assertEquals(30, rests.single().durationSec)
        assertEquals(1, rests.single().setIndex)
    }

    @Test
    fun `una serie con descanso 0 no descansa aunque el ejercicio si`() {
        val s = steps(ex(sets = 3, restSec = 20, setList = listOf(WorkSet(restSec = 0), WorkSet(), WorkSet())))

        val rests = s.filter { it.kind == StepKind.REST }
        assertEquals(1, rests.size)
        assertEquals(20, rests.single().durationSec)
        assertEquals(1, rests.single().setIndex)
    }

    // ---------- Convivencia con lo que ya existia ----------

    @Test
    fun `el descanso propio de la ultima serie tambien se salta`() {
        // El interruptor es sobre la POSICION de la serie, no sobre de donde sale el numero.
        val s = steps(ex(sets = 2, setList = listOf(WorkSet(), WorkSet(restSec = 45))))

        assertEquals(1, s.count { it.kind == StepKind.REST })
        assertEquals(3, s.first { it.kind == StepKind.REST }.durationSec)
    }

    @Test
    fun `sin skip el descanso propio de la ultima serie se reproduce`() {
        val s = steps(ex(sets = 2, restSkipOnLastSet = false, setList = listOf(WorkSet(), WorkSet(restSec = 45))))

        assertEquals(listOf(3, 45), s.filter { it.kind == StepKind.REST }.map { it.durationSec })
    }

    @Test
    fun `con peso mandan las reps de cada serie`() {
        val e = ex(
            sets = 2,
            workMode = WorkMode.REPS,
            workValue = 15,
            weightType = WeightType.TOTAL,
            setList = listOf(WorkSet(reps = 12, weight = 20.0), WorkSet(reps = 10, weight = 25.0)),
        )

        assertEquals(listOf(12, 10), steps(e).filter { it.kind == StepKind.WORK }.map { it.reps })
    }

    @Test
    fun `el descanso por serie tambien vale por repeticiones`() {
        val e = ex(
            sets = 3,
            workMode = WorkMode.REPS,
            workValue = 12,
            setList = listOf(WorkSet(), WorkSet(restSec = 60), WorkSet()),
        )

        assertEquals(listOf(3, 60), steps(e).filter { it.kind == StepKind.REST }.map { it.durationSec })
    }

    // ---------- Rellenar la lista para poder editarla ----------

    @Test
    fun `materializedSets rellena hasta el numero de series`() {
        val e = ex(sets = 4, setList = listOf(WorkSet(sec = 30)))
        val list = e.materializedSets()

        assertEquals(4, list.size)
        assertEquals(30, list[0].sec)
        assertTrue(list.drop(1).all { it.sec == null && it.restSec == null })
    }

    @Test
    fun `el relleno usa las repeticiones del ejercicio`() {
        val e = ex(sets = 3, workMode = WorkMode.REPS, workValue = 15)

        assertTrue(e.materializedSets().all { it.reps == 15 })
    }

    // ---------- La lista no puede divergir a escondidas ----------

    @Test
    fun `sin carga las reps de cada serie siguen a las del ejercicio`() {
        // La lista existe solo porque una serie se llevo su descanso; sus reps no se ven en
        // ningun sitio, asi que no pueden quedarse en el valor de ayer.
        val e = ex(
            sets = 2,
            workMode = WorkMode.REPS,
            workValue = 15,
            setList = listOf(WorkSet(reps = 8, restSec = 30), WorkSet(reps = 8)),
        ).normalizedSets()

        assertTrue(e.setList.all { it.reps == 15 })
        assertEquals(30, e.setList[0].restSec)
        assertTrue(steps(e).filter { it.kind == StepKind.WORK }.all { it.reps == 15 })
    }

    @Test
    fun `con carga mandan las reps de cada serie y normalizedSets no las toca`() {
        val e = ex(
            sets = 2,
            workMode = WorkMode.REPS,
            workValue = 15,
            weightType = WeightType.TOTAL,
            setList = listOf(WorkSet(reps = 12, weight = 20.0), WorkSet(reps = 10, weight = 25.0)),
        ).normalizedSets()

        assertEquals(listOf(12, 10), e.setList.map { it.reps })
        assertEquals(listOf(20.0, 25.0), e.setList.map { it.weight })
    }

    @Test
    fun `quitar la carga no borra los tiempos propios de cada serie`() {
        // Es la trampa de TD-064 en pequeno: un interruptor que ademas borra datos.
        val conPeso = ex(
            sets = 2,
            workMode = WorkMode.REPS,
            workValue = 12,
            weightType = WeightType.TOTAL,
            setList = listOf(WorkSet(reps = 12, weight = 20.0, restSec = 90), WorkSet(reps = 12, weight = 20.0)),
        )

        val sinPeso = conPeso.copy(weightType = WeightType.NONE).normalizedSets()

        assertEquals(90, sinPeso.setList[0].restSec)
        assertTrue(sinPeso.setList.all { it.weight == 0.0 })
    }

    @Test
    fun `sin lista de series no hay nada que normalizar`() {
        val e = ex(sets = 3, workMode = WorkMode.REPS, workValue = 12)

        assertTrue(e.normalizedSets().setList.isEmpty())
    }

    // ---------- Serializacion ----------

    @Test
    fun `el trabajo y el descanso propios sobreviven la ida y vuelta`() {
        val t = Training(id = 1, uid = "u1", name = "Lumbar", workouts = listOf(Workout(id = 1, name = "McGill", exercises = listOf(pyramid()))))

        val back = TrainingJson.decode(TrainingJson.encode(listOf(t))).single()
        val list = back.workouts.single().exercises.single().setList

        assertEquals(12, list.size)
        assertEquals(30, list[5].restSec)
        assertEquals(30, list[9].restSec)
        assertNull(list[0].restSec)
        assertNull(list[0].sec)
    }

    @Test
    fun `una serie sin valor propio no lo gana al guardarla`() {
        // Si el ausente volviera como 0, un ejercicio con descanso se quedaria sin el.
        val t = Training(id = 1, name = "T", workouts = listOf(Workout(id = 1, exercises = listOf(ex(sets = 2, setList = listOf(WorkSet(), WorkSet()))))))

        val back = TrainingJson.decode(TrainingJson.encode(listOf(t))).single()

        assertTrue(back.workouts.single().exercises.single().setList.all { it.sec == null && it.restSec == null })
        assertEquals(3, steps(back.workouts.single().exercises.single()).first { it.kind == StepKind.REST }.durationSec)
    }

    @Test
    fun `un training guardado antes de que existieran los campos se reproduce igual`() {
        // Tal cual lo escribia la version anterior: series con reps y peso, sin sec ni restSec.
        val json = """
            [{"id":1,"uid":"u1","name":"Viejo","workouts":[{"id":1,"name":"W","exercises":[
              {"id":1,"exerciseId":"ex_squat","name":"Squat","sets":2,"workMode":"REPS","workValue":12,
               "restSec":45,"restSkipOnLastSet":true,"weightType":"TOTAL",
               "setList":[{"reps":12,"weight":20},{"reps":10,"weight":25}]}
            ]}]}]
        """.trimIndent()

        val e = TrainingJson.decode(json).single().workouts.single().exercises.single()

        assertTrue(e.setList.all { it.sec == null && it.restSec == null })
        assertEquals(listOf(12, 10), steps(e).filter { it.kind == StepKind.WORK }.map { it.reps })
        assertEquals(45, steps(e).single { it.kind == StepKind.REST }.durationSec)
    }
}
