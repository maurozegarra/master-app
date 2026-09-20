package com.maurozegarra.master.data

import com.maurozegarra.master.model.StepEngine
import com.maurozegarra.master.model.WeightType
import com.maurozegarra.master.model.WorkSet
import com.maurozegarra.master.model.weightTotal
import com.maurozegarra.master.model.StepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
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
    private val short = MasterDefaults.lumbarShortTraining("en")
    private val steps = StepEngine.buildSteps(training.copy(workouts = training.workouts))

    @Test
    fun `tiene los cinco bloques de la rutina`() {
        assertEquals(
            listOf("Warm Walk", "Mobility", "McGill Big 3", "Hip & Glute", "Cool Walk"),
            training.workouts.map { it.name },
        )
        // 3 en McGill y no 4 desde TD-147: la plancha lateral era dos ejercicios clonados y
        // ahora es uno con sus dos lados declarados.
        assertEquals(listOf(1, 2, 3, 3, 1), training.workouts.map { it.exercises.size })
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
            // El respiro largo cierra el bloque de 6 y el de 4. La plancha lleva ademas uno
            // en la ULTIMA serie: son los 20 s para cambiar de lado, que antes los daba la
            // PREP del segundo ejercicio y al fundirlos habrian desaparecido (TD-147).
            val conDescanso = e.setList.indices.filter { e.setList[it].restSec != null }
            if (e.sides.isEmpty()) {
                assertEquals(listOf(5, 9), conDescanso)
                assertTrue(e.setList.filter { it.restSec != null }.all { it.restSec == 30 })
            } else {
                assertEquals(listOf(5, 9, 11), conDescanso)
                assertEquals(20, e.setList[11].restSec)
            }
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

        // 20 s para acomodarse + 3:27 de piramide. La plancha son los dos lados: 20 de
        // acomodarse, una piramide, 20 para cambiar de lado y la otra piramide. El total del
        // bloque no se mueve -antes eran dos PREP de 20 y ahora una PREP y un cambio-, que
        // es lo que hace que fundirla no le cambie la sesion a nadie (TD-147).
        assertEquals(listOf(227, 454, 227), porMovimiento)
    }

    @Test
    fun `la plancha lateral se hace a los dos lados y cada uno cuenta aparte`() {
        // Hasta TD-147 eran dos entradas del catalogo clonadas. Lo que se protegia con eso
        // -que izquierda y derecha se cuenten aparte, porque una asimetria es justo lo que
        // hay que ver- lo da ahora el lado declarado en el ejercicio, sin clonar nada.
        val plancha = training.workouts[2].exercises.single { it.exerciseId == "ex_side_plank" }

        assertEquals(listOf("Left", "Right"), plancha.sides)
        val registros = StepEngine.buildSteps(training)
            .filter { it.kind == StepKind.WORK && it.ownerExerciseId == "ex_side_plank" }
            .map { it.side }
            .distinct()
        assertEquals(listOf("Left", "Right"), registros)
    }

    @Test
    fun `el bloque de cadera va por repeticiones con un minuto de descanso`() {
        training.workouts[3].exercises.forEach { e ->
            assertEquals(3, e.sets)
            assertEquals(60, e.restSec)
        }
    }

    // ---------- El bloque de cadera lleva carga (TD-098) ----------

    private fun cadera(t: com.maurozegarra.master.model.Training) =
        t.workouts.first { it.name == "Hip & Glute" }.exercises.associateBy { it.exerciseId }

    @Test
    fun `el puente de gluteos usa la barra de 6 kilos, no la de 20 por defecto`() {
        val e = cadera(training).getValue("ex_glute_bridge")

        assertEquals(WeightType.BARBELL, e.weightType)
        // 20 era el defecto del app y el 15-sep le enseno "40 kg" cuando iban a ser 26:
        // le parecio mucho y bajo la carga. Un numero mal puesto le cambio el entrenamiento.
        assertEquals(6.0, e.barWeight, 0.0)
        // Los numeros de la serie son DISCOS: el total es la barra mas eso.
        // 21 -> 26 -> 31 -> 36, cada subida pedida por el cuerpo: el 18-sep marco los 31
        // como ligeros en el player. Revision 9: 41 arriba, tras las nueve series ligeras
        // del 19-sep.
        //
        // REVISION 11, con lo que contesto el 20-sep: 41 PESADO (-2.5) y 26 bien. El tope
        // baja a 38.5 -6 + 10 + 5 + 1.25 por lado- y la de abajo sube de la barra sola a 16:
        // llevaba tres dias marcando la barra vacia como ligera, que es un dato que se
        // repite sin decir nada nuevo.
        assertEquals(listOf(16.0, 26.0, 38.5), e.setList.map { e.weightTotal(it) })
        assertTrue(e.setList.all { it.reps == 12 })
    }

    @Test
    fun `el carry se queda y la sentadilla sube`() {
        // Suben en la revision 6 ("ligero" en las tres el 17-sep) y otra vez en la 9 (las nueve
        // series del bloque marcadas ligeras el 19-sep).
        assertEquals(listOf(12.5, 15.0, 17.5), cadera(training).getValue("ex_suitcase_carry").setList.map { it.weight })
        assertEquals(listOf(15.0, 17.5, 20.0), cadera(training).getValue("ex_box_squat").setList.map { it.weight })
        // UNA mancuerna desde la revision 8 (TD-130). Iban como TOTAL, que es tambien como
        // van las maquinas, y el player no podia decir "1 de 10". El numero por serie es el
        // mismo -una mancuerna de 10 pesa 10-, asi que el historial no se parte.
        listOf("ex_box_squat", "ex_suitcase_carry").forEach {
            val e = cadera(training).getValue(it)
            assertEquals(WeightType.DUMBBELL, e.weightType)
            assertEquals(1, e.dumbbellCount)
        }
    }

    @Test
    fun `las caminatas llevan la velocidad como dato, no como texto`() {
        // "Paso vivo" costo tres sesiones: a 3 km/h no hacia nada, a 5 le solto las caderas.
        // El numero vivio un tiempo dentro de la nota, que era mejor que un adjetivo pero
        // seguia siendo texto: nadie puede comparar notas entre sesiones. Desde TD-124 es un
        // campo, el player lo deja ajustar y queda en el registro.
        val entrada = training.workouts.first().exercises.single()
        val corta = badDay.workouts[1].exercises.single()
        assertEquals(6.0, entrada.speedKmh!!, 0.0)
        assertEquals(6.0, corta.speedKmh!!, 0.0)
        // Y la de cierre va mas suave que la de entrada, que es lo que significa "easy".
        val cierre = training.workouts.last().exercises.single()
        assertTrue(cierre.speedKmh!! < entrada.speedKmh!!)
        // El numero ya no se repite en la nota: un dato en dos sitios acaba contradiciendose.
        listOf(entrada, corta, cierre).forEach { assertFalse(it.note.contains("km/h")) }
    }

    @Test
    fun `el corto comparte el carry con el completo, al mismo peso`() {
        // Lo que se comparte es el peso, no el bloque entero: el corto lleva solo el carry
        // (revision 11). Si el completo sube, el corto sube con el; llevar dos numeros para
        // el mismo ejercicio seria dos historiales que no se pueden comparar.
        val enCompleto = cadera(training).getValue("ex_suitcase_carry")
        val enCorto = short.workouts.flatMap { it.exercises }.first { it.exerciseId == "ex_suitcase_carry" }

        assertEquals(enCompleto.weightType, enCorto.weightType)
        assertEquals(enCompleto.setList, enCorto.setList)
        assertEquals(enCompleto.dumbbellCount, enCorto.dumbbellCount)
    }

    // ---------- El corto, para los dias con trabajo presencial (revision 11) ----------

    @Test
    fun `el corto camina, hace McGill a la mitad y solo carga el carry`() {
        assertEquals(
            listOf("Warm Walk", "Mobility", "McGill Big 3", "Carry", "Cool Walk"),
            short.workouts.map { it.name },
        )
        assertEquals(listOf(6, 6, 6), short.workouts.first { it.name == "McGill Big 3" }.exercises.map { it.sets })
        // De los tres con carga se queda el que mas da por minuto y que ademas es caminar
        // cargado. El puente y la sentadilla los hace igual cuatro dias por semana.
        assertEquals(
            listOf("ex_suitcase_carry"),
            short.workouts.flatMap { it.exercises }.filter { it.weightType != WeightType.NONE }.map { it.exerciseId },
        )
    }

    @Test
    fun `el corto cabe en media hora`() {
        // El motivo de que exista: los lunes, miercoles y jueves trabaja fuera. Si se pasa de
        // 35 minutos deja de resolver el problema y vuelve a saltarse la sesion.
        val medido = StepEngine.buildSteps(short).sumOf { it.durationSec }
        assertTrue("dura ${medido / 60} min", medido in 1500..2100)
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

        assertTrue(pasos.any { it.contains("First hour after waking up") })
        assertTrue(pasos.any { it.contains("radiates down the leg") })
    }

    @Test
    fun `la regla de la primera hora dice de que protege, no solo que no se entrene`() {
        // Leida a las seis de la manana, "no entrenes en la primera hora" se entiende como
        // "hoy no entrenes". Lo que cobra caro es la flexion lumbar con carga.
        val pasos = MasterDefaults.lumbarInstructions().getValue("ex_walk").instructions

        assertTrue(pasos.any { it.contains("FLEXION") })
        assertTrue(pasos.any { it.contains("suitcase carry") })
    }

    @Test
    fun `el texto viejo de la caminata se conserva para poder distinguirlo`() {
        // Es lo que permite reescribirlo solo si el usuario no lo ha tocado.
        assertNotEquals(
            MasterDefaults.WALK_INSTRUCTIONS_V1,
            MasterDefaults.lumbarInstructions().getValue("ex_walk"),
        )
        assertTrue(MasterDefaults.WALK_INSTRUCTIONS_V1.instructions.any { it.contains("Do not train in the first hour") })
    }

    @Test
    fun `el id del training no se mueve`() {
        // Es el que quedo sembrado en el dispositivo y al que apunta la sesion del
        // historial: si cambia, esa sesion deja de pertenecer a ningun training.
        assertEquals(950016L, training.id)
        assertEquals(MasterDefaults.LUMBAR_ID, training.id)
    }

    // ---------- La variante de dia malo (TD-088) ----------

    private val badDay = MasterDefaults.lumbarBadDayTraining("en")

    @Test
    fun `el dia malo abre con movilidad y camina despues`() {
        assertEquals(
            listOf("Mobility", "Walk", "McGill Big 3", "Cool Walk"),
            badDay.workouts.map { it.name },
        )
    }

    @Test
    fun `el dia malo camina MAS que el normal, no menos`() {
        // Hasta la revision 10 caminaba 6 minutos frente a los 12+5 del dia bueno, y llevaba
        // la misma carga. Estaba al reves: en un dia de crisis lo que sobra es carga y lo que
        // falta es movimiento.
        val malo = badDay.workouts.flatMap { it.exercises }.filter { it.exerciseId == "ex_walk" }
        assertEquals(listOf(600, 300), malo.map { it.workValue })
        // 15 minutos de caminata frente a los 17 del dia bueno, pero sin nada de carga.
        val bueno = training.workouts.flatMap { it.exercises }.filter { it.exerciseId == "ex_walk" }
        assertEquals(listOf(720, 300), bueno.map { it.workValue })
    }

    @Test
    fun `el dia malo baja McGill a la mitad`() {
        fun aguantes(t: com.maurozegarra.master.model.Training) =
            t.workouts.first { it.name == "McGill Big 3" }.exercises.map { it.sets }

        assertEquals(listOf(12, 12, 12), aguantes(training))
        assertEquals(listOf(6, 6, 6), aguantes(badDay))
        // Y los 10 s de cada aguante no se tocan: lo que baja es cuantos, no cuanto dura.
        assertTrue(badDay.workouts.first { it.name == "McGill Big 3" }.exercises.all { it.workValue == 10 })
    }

    @Test
    fun `el dia malo no lleva carga`() {
        // Antes iba al final "donde se puede saltar con el skip". Saltarlo dependia de que el
        // usuario decidiera bien el peor dia; ahora no esta y no hay nada que decidir.
        assertTrue(badDay.workouts.flatMap { it.exercises }.none { it.weightType != WeightType.NONE })
    }

    @Test
    fun `los tres trainings lumbares no comparten ningun id`() {
        fun ids(t: com.maurozegarra.master.model.Training) =
            listOf(t.id) + t.workouts.flatMap { w -> listOf(w.id) + w.exercises.map { it.id } }

        val todos = listOf(ids(training), ids(short), ids(badDay))
        assertEquals(todos.flatten().size, todos.flatten().toSet().size)
    }

    // ---------- La rutina va por revision (TD-103) ----------

    @Test
    fun `withLumbarRevision agrega los tres lumbares si no estan`() {
        val otros = listOf(MasterDefaults.masterTraining("en"))

        val out = MasterDefaults.withLumbarRevision(otros, "en")

        assertEquals(4, out.size)
        assertTrue(out.any { it.id == MasterDefaults.LUMBAR_ID })
        assertTrue(out.any { it.id == MasterDefaults.LUMBAR_SHORT_ID })
        assertTrue(out.any { it.id == MasterDefaults.LUMBAR_BAD_DAY_ID })
    }

    @Test
    fun `withLumbarRevision reemplaza en su sitio y no toca a los demas`() {
        val master = MasterDefaults.masterTraining("en")
        val viejo = training.copy(workouts = emptyList())

        val out = MasterDefaults.withLumbarRevision(listOf(viejo, master), "en")

        assertEquals(4, out.size)
        assertEquals(MasterDefaults.LUMBAR_ID, out[0].id)
        assertEquals(5, out[0].workouts.size)
        assertEquals(master, out[1])
    }

    @Test
    fun `withLumbarRevision conserva el uid y la fecha de creacion`() {
        // El uid es la identidad con la que un training viaja entre dispositivos: cambiarlo
        // seria otro training. Y createdAt es cuando aparecio de verdad.
        val viejo = training.copy(uid = "uid-de-siempre", createdAt = 111L, workouts = emptyList())

        val out = MasterDefaults.withLumbarRevision(listOf(viejo), "en").first { it.id == MasterDefaults.LUMBAR_ID }

        assertEquals("uid-de-siempre", out.uid)
        assertEquals(111L, out.createdAt)
        assertEquals(5, out.workouts.size)
    }

    @Test
    fun `withLumbarRevision es idempotente`() {
        val una = MasterDefaults.withLumbarRevision(emptyList(), "en")
        val dos = MasterDefaults.withLumbarRevision(una, "en")

        assertEquals(una.map { it.id }, dos.map { it.id })
        assertEquals(una.map { it.workouts }, dos.map { it.workouts })
    }

    @Test
    fun `la correccion del 15-sep sabe que peso arreglar`() {
        // 6 kg de barra mas 0, 10 y 15 de discos: lo que de verdad movio.
        assertEquals(listOf(20.0, 30.0, 40.0), MasterDefaults.SESSION_15_SEP_WRONG)
        assertEquals(listOf(6.0, 16.0, 21.0), MasterDefaults.SESSION_15_SEP_RIGHT)
        assertEquals(3, MasterDefaults.SESSION_15_SEP_RIGHT.size)
    }

    @Test
    fun `los dos lumbares preguntan como te fue`() {
        assertTrue(training.tracksPain)
        assertTrue(badDay.tracksPain)
        // Y los demas no: la pregunta es sobre dolor y no pinta despues de un cardio.
        assertFalse(MasterDefaults.masterTraining("en").tracksPain)
        assertFalse(MasterDefaults.frikiNikiTraining("en").tracksPain)
    }

    @Test
    fun `la rutina lumbar tiene dueno`() {
        // Sembrar desde el codigo llega a cualquier instalacion, y una rutina de
        // rehabilitacion de la espalda de otro no es ruido neutro: es algo que alguien
        // podria ponerse a hacer.
        assertEquals("mauro", MasterDefaults.LUMBAR_PROFILE)
    }

    @Test
    fun `la escala de dolor describe los once numeros`() {
        val escala = com.maurozegarra.master.i18n.I18n.EN.painScale

        assertEquals(11, escala.size)
        assertTrue(escala.all { it.isNotBlank() })
        // El 0 y el 10 son los extremos; lo que se pidio es que el 4 tambien diga algo.
        assertEquals("No pain", escala.first())
        assertTrue(escala[4].isNotBlank() && escala[4] != escala[5])
    }

    @Test
    fun `la revision de la rutina no baja`() {
        // Subir este numero es lo unico que hace falta para que un cambio llegue al
        // dispositivo. El test esta para que nadie lo baje sin querer.
        assertTrue(MasterDefaults.LUMBAR_REVISION >= 3)
    }

    // ---------- La sesion del 13-sep-2026 (TD-090) ----------

    private val session = MasterDefaults.lumbarFirstSession("en")

    @Test
    fun `la sesion pertenece al training sembrado`() {
        assertEquals(MasterDefaults.LUMBAR_ID, session.trainingId)
        assertEquals("LUMBAR", session.trainingName)
    }

    @Test
    fun `la sesion empieza a la una de la tarde del 13 de setiembre de 2026`() {
        val zona = java.time.ZoneId.of("America/Lima")
        val inicio = java.time.Instant.ofEpochMilli(session.startedAt).atZone(zona)

        assertEquals(java.time.LocalDate.of(2026, 9, 13), inicio.toLocalDate())
        assertEquals(13, inicio.hour)
        assertEquals(0, inicio.minute)
        assertEquals(55 * 60, session.durationSec)
        assertEquals(session.durationSec * 1000L, session.completedAt - session.startedAt)
    }

    @Test
    fun `la sesion recoge los once ejercicios, todos completos`() {
        assertEquals(11, session.exercises.size)
        assertTrue(session.exercises.all { it.setsCompleted == it.totalSets })
        assertTrue(session.exercises.none { r -> r.sets.any { it.skipped } })
        assertEquals(com.maurozegarra.master.model.SessionStatus.COMPLETED, session.status)
    }

    @Test
    fun `cada movimiento de McGill queda con sus doce aguantes de diez segundos`() {
        val curlUp = session.exercises.single { it.exerciseId == "ex_curl_up" }

        assertEquals(12, curlUp.totalSets)
        assertTrue(curlUp.timeBased)
        assertTrue(curlUp.sets.all { it.durationSec == 10 })
    }

    @Test
    fun `lo que va por repeticiones se anota con sus reps y sin duracion`() {
        val puente = session.exercises.single { it.exerciseId == "ex_glute_bridge" }

        assertFalse(puente.timeBased)
        assertEquals(listOf(12, 12, 12), puente.sets.map { it.reps })
        assertTrue(puente.sets.all { it.durationSec == 0 })
    }

    @Test
    fun `la sesion sobrevive la ida y vuelta a json`() {
        val back = com.maurozegarra.master.model.SessionJson
            .decode(com.maurozegarra.master.model.SessionJson.encode(listOf(session)))
            .single()

        assertEquals(session, back)
    }
}
