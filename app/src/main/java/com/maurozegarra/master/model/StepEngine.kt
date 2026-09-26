package com.maurozegarra.master.model

/**
 * Motor puro de generación de pasos: convierte un [Training] en la lista
 * aplanada de [PlayerStep] que reproduce el player. Sin dependencias de
 * Android (testeable con unit tests puros).
 */
object StepEngine {

    /** Si el ejercicio [ei] del workout [wi] es el ultimo que se hace en el training. */
    private fun esElUltimo(t: Training, wi: Int, ei: Int): Boolean {
        val quedan = t.workouts.drop(wi + 1).any { it.activeExercises().isNotEmpty() }
        return !quedan && ei == t.workouts[wi].activeExercises().lastIndex
    }

    /** Lo que dura el respiro para contestar tras la ultima serie de un aguante (TD-156). */
    const val ANSWER_SEC = 10

    /**
     * La cola de pasos de [t].
     *
     * [answerWindow]: si al final del training va un respiro para contestar la ultima serie
     * (ver [ANSWER_SEC]). Lo pide el player, que es donde se contesta; la estructura del
     * motor, sus estimados y sus tests no lo llevan.
     */
    fun buildSteps(t: Training, answerWindow: Boolean = false): List<PlayerStep> = buildList {
        val tw = t.workouts.size.coerceAtLeast(1)
        t.workouts.forEachIndexed { wi, w ->
            val wName = w.activeName()
            val wVariant = if (w.rotating) (w.activeVariant()?.name ?: "") else ""
            val pasosW = buildList {
            w.activeExercises().forEachIndexed { ei, e ->
                if (e.prepareSec > 0) {
                    add(stageStep(StepKind.PREP, e, wName, wi, tw, durationSec = e.prepareSec, workoutBase = w.name, variant = wVariant, rotating = w.rotating, exerciseIndex = ei))
                }
                val sets = e.sets.coerceAtLeast(1)
                // Un ejercicio sin lados se comporta como siempre: una vuelta con etiqueta
                // vacia. Con lados, TODAS las series de uno y despues las del otro (TD-147),
                // o alternados dentro de cada serie si el ejercicio lo pide (TD-156).
                val sides = e.sides.ifEmpty { listOf("") }
                val orden = if (e.alternateSides) {
                    (0 until sets).flatMap { s -> sides.indices.map { si -> si to s } }
                } else {
                    sides.indices.flatMap { si -> (0 until sets).map { s -> si to s } }
                }
                for ((slot, par) in orden.withIndex()) {
                    val (si, s) = par
                    val side = sides[si]
                    if (e.workMode == WorkMode.TIME) {
                        add(stageStep(StepKind.WORK, e, wName, wi, tw, durationSec = e.workSecAt(s), setIndex = s, totalSets = sets, timeBased = true, workoutBase = w.name, variant = wVariant, rotating = w.rotating, exerciseIndex = ei, speedKmh = e.speedKmh, side = side, sideIndex = si, sideCount = e.sides.size, slot = slot))
                    } else {
                        val ws = e.setAt(s)
                        add(
                            stageStep(
                                StepKind.WORK, e, wName, wi, tw,
                                reps = ws.reps, setIndex = s, totalSets = sets, timeBased = false,
                                weighted = e.isWeighted,
                                weightTotal = if (e.isWeighted) e.weightTotal(ws) else 0.0,
                                weightLabel = if (e.isWeighted) weightLabel(e, ws) else "",
                                workoutBase = w.name, variant = wVariant, rotating = w.rotating,
                                // Un metro con carga es ~1 s caminando: con los 3 s por rep del
                                // defecto, 36 m estimaban casi dos minutos por viaje.
                                secPerRep = if (e.workMode == WorkMode.DISTANCE) 1 else e.secPerRep,
                                exerciseIndex = ei, side = side, sideIndex = si, sideCount = e.sides.size, slot = slot,
                            ),
                        )
                    }
                    // El ultimo de verdad es el ultimo del ULTIMO lado: entre un lado y el
                    // siguiente hay que cambiar de postura, asi que ese descanso se queda.
                    val lastSet = slot == orden.lastIndex
                    // Alternando, entre un lado y el otro de la MISMA serie no se descansa:
                    // se cambia de mano y se sigue. El descanso va al cerrar la serie.
                    val cambioDeMano = e.alternateSides && si < sides.lastIndex
                    // El descanso que decide es el EFECTIVO de esta serie, no el del
                    // ejercicio: si no, una serie con descanso propio no generaria etapa
                    // en un ejercicio con restSec 0, que es justo como se escribe una
                    // piramide (series pegadas y un respiro largo solo en dos de ellas).
                    val rest = e.restSecAt(s)
                    val trabajo = last()
                    if (!cambioDeMano && rest > 0 && !(e.restSkipOnLastSet && lastSet)) {
                        add(stageStep(StepKind.REST, e, wName, wi, tw, durationSec = rest, setIndex = s, totalSets = sets, workoutBase = w.name, variant = wVariant, rotating = w.rotating, exerciseIndex = ei, side = side, sideIndex = si, sideCount = e.sides.size, slot = slot))
                    } else if (answerWindow && lastSet && trabajo.timeBased && Effort.asks(trabajo) && esElUltimo(t, wi, ei)) {
                        // Un respiro para contestar como fue, al FINAL del training (TD-156). Un
                        // aguante o un round no se contesta mientras se hace -la tarjeta sale en
                        // el descanso de despues- y la ultima serie no tiene descanso: el 24-sep,
                        // en el cuello de NIKO, la ultima direccion terminaba la sesion "sin
                        // tiempo para marcar". Entre ejercicios no hace falta: la tarjeta que
                        // quedo sin contestar sale en la PREPARACION del siguiente.
                        add(stageStep(StepKind.REST, e, wName, wi, tw, durationSec = ANSWER_SEC, setIndex = s, totalSets = sets, workoutBase = w.name, variant = wVariant, rotating = w.rotating, exerciseIndex = ei, side = side, sideIndex = si, sideCount = e.sides.size, slot = slot))
                    }
                }
                if (e.cooldownSec > 0) {
                    add(stageStep(StepKind.COOLDOWN, e, wName, wi, tw, durationSec = e.cooldownSec, workoutBase = w.name, variant = wVariant, rotating = w.rotating, exerciseIndex = ei))
                }
            }
            }
            addAll(if (w.circuit) enCircuito(pasosW) else pasosW)
        }
    }

    /**
     * Los pasos de un workout, en orden de circuito (TD-137): round a round, cada ejercicio
     * con su serie de ese round. Se arma sobre la cola normal, reordenandola, para que un
     * circuito tenga exactamente los mismos pasos que el mismo workout en bloques -mismas
     * series, lados, descansos y preguntas- y solo cambie el orden.
     *
     * La preparacion de cada ejercicio va solo la primera vez que aparece: en un circuito se
     * pasa de uno a otro sin pausa, y diez segundos de "prepare" en cada cambio lo romperian.
     * El enfriamiento, al final de todo.
     */
    private fun enCircuito(pasos: List<PlayerStep>): List<PlayerStep> {
        val porEjercicio = pasos.groupBy { it.exerciseIndex }.toSortedMap()
        val rounds = pasos.filter { it.kind == StepKind.WORK || it.kind == StepKind.REST }.maxOfOrNull { it.slot } ?: return pasos
        return buildList {
            for (r in 0..rounds) {
                porEjercicio.forEach { (_, ps) ->
                    if (r == 0) addAll(ps.filter { it.kind == StepKind.PREP })
                    addAll(ps.filter { (it.kind == StepKind.WORK || it.kind == StepKind.REST) && it.slot == r })
                }
            }
            porEjercicio.forEach { (_, ps) -> addAll(ps.filter { it.kind == StepKind.COOLDOWN }) }
        }.map { it.copy(circuit = true) }
    }

    /**
     * Dónde seguir cuando la cola se rehace a mitad de corrida, tras editar el training.
     *
     * Busca el paso equivalente a [current] en [newSteps]. Si ya no existe —porque bajaste
     * las series y esa desapareció, o borraste el ejercicio— devuelve el primero que venga
     * **después**. Nunca uno anterior: retroceder haría repetir trabajo ya hecho, que es el
     * peor fallo posible aquí. Si no queda nada por delante, devuelve el último paso.
     */
    fun relocate(current: PlayerStep, newSteps: List<PlayerStep>): Int {
        if (newSteps.isEmpty()) return 0
        val at = newSteps.indexOfFirst { compareSteps(it, current) >= 0 }
        return if (at >= 0) at else newSteps.lastIndex
    }

    /**
     * Posición de un paso dentro del training, comparable entre dos versiones de la cola.
     *
     * El orden de un ejercicio es `[PREP] · (WORK_s, [REST_s])* · [COOLDOWN]`, así que no
     * vale comparar por etapa: REST de la serie 0 va ANTES que WORK de la serie 1. De ahí
     * el grupo intermedio, que mete las dos en la misma casilla de serie.
     *
     * [PlayerStep.exerciseIndex] es lo que distingue dos apariciones del mismo ejercicio
     * del catálogo dentro de un workout.
     */
    /** Si dos pasos ocupan la misma casilla del training, aunque cambien sus valores. */
    fun sameSlot(a: PlayerStep, b: PlayerStep): Boolean = compareSteps(a, b) == 0

    /**
     * El paso recien construido, pero con el reloj del que se esta corriendo (TD-146).
     *
     * Al editar un training en marcha, el paso en el que uno esta parado NO puede cambiar de
     * duracion: si vas por la serie 15 de 30 s y subes el tiempo a 40, esa serie termina con
     * 30 y el cambio entra en la siguiente. Asi el reloj no salta bajo los pies y lo que se
     * registra para esa serie es lo que de verdad se hizo.
     *
     * Lo que si entra **ahora** es todo lo demas: el video, la nota, el color, el peso. Antes
     * se conservaba el paso viejo ENTERO, y por eso apagar el video desde el editor no hacia
     * nada hasta la serie siguiente -parecia que el interruptor estaba roto-.
     *
     * Si cambio el modo -de tiempo a repeticiones o al reves- no hay reloj que conservar: el
     * paso nuevo manda entero.
     */
    fun keepClock(fresh: PlayerStep, running: PlayerStep): PlayerStep =
        if (fresh.timeBased != running.timeBased) fresh
        else fresh.copy(durationSec = running.durationSec, reps = running.reps)

    private fun compareSteps(a: PlayerStep, b: PlayerStep): Int =
        if (a.circuit && b.circuit && a.workoutIndex == b.workoutIndex) {
            // En circuito manda el ROUND y despues el ejercicio (TD-137): el round 2 del
            // primero va despues del round 1 del ultimo.
            compareValuesBy(
                a, b,
                { if (it.kind == StepKind.COOLDOWN) Int.MAX_VALUE else it.slot },
                { it.exerciseIndex },
                { stageGroup(it) },
                { if (it.kind == StepKind.REST) 1 else 0 },
            )
        } else {
            compareValuesBy(
                a, b,
                { it.workoutIndex },
                { it.exerciseIndex },
                { stageGroup(it) },
                // El puesto y no la serie (TD-156): con lados, la serie sola repetia numero
                // en cada lado y la reubicacion podia caer en un lado ya hecho.
                { if (stageGroup(it) == 1) it.slot else 0 },
                { if (it.kind == StepKind.REST) 1 else 0 },
            )
        }

    private fun stageGroup(s: PlayerStep): Int = when (s.kind) {
        StepKind.PREP -> 0
        StepKind.WORK, StepKind.REST -> 1
        StepKind.COOLDOWN -> 2
    }

    private fun stageStep(
        kind: StepKind,
        e: Exercise,
        workoutName: String,
        workoutIndex: Int,
        totalWorkouts: Int,
        durationSec: Int = 0,
        reps: Int = 0,
        setIndex: Int = 0,
        totalSets: Int = 1,
        timeBased: Boolean = true,
        weighted: Boolean = false,
        weightTotal: Double = 0.0,
        weightLabel: String = "",
        workoutBase: String = "",
        variant: String = "",
        rotating: Boolean = false,
        secPerRep: Int = 3,
        exerciseIndex: Int = 0,
        speedKmh: Double? = null,
        side: String = "",
        sideIndex: Int = 0,
        sideCount: Int = 0,
        slot: Int = 0,
    ): PlayerStep {
        val cfg = when (kind) {
            StepKind.PREP -> e.prepareCfg
            StepKind.WORK -> e.workCfg
            StepKind.REST -> e.restCfg
            StepKind.COOLDOWN -> e.cooldownCfg
        }
        return PlayerStep(
            kind = kind,
            title = if (kind == StepKind.WORK) e.name else "",
            note = e.note,
            ownerName = e.name,
            ownerExerciseId = e.exerciseId,
            exerciseIndex = exerciseIndex,
            showVideo = e.showVideo,
            side = side,
            sideIndex = sideIndex,
            sideCount = sideCount,
            slot = slot,
            progression = Effort.of(e),
            distance = e.workMode == WorkMode.DISTANCE,
            workoutName = workoutName,
            workoutIndex = workoutIndex,
            totalWorkouts = totalWorkouts,
            setIndex = setIndex,
            totalSets = totalSets,
            durationSec = durationSec,
            reps = reps,
            timeBased = timeBased,
            display = cfg.display,
            confirm = cfg.confirm,
            finalCount = cfg.finalCount,
            beepSoundUri = cfg.beepSoundUri,
            alarm = cfg.alarm,
            colorArgb = cfg.color,
            weighted = weighted,
            weightTotal = weightTotal,
            weightLabel = weightLabel,
            workoutBaseName = workoutBase,
            variantName = variant,
            rotating = rotating,
            secPerRep = secPerRep,
            speedKmh = speedKmh,
            weightType = if (weighted) e.weightType else WeightType.NONE,
            barWeight = if (weighted && e.weightType == WeightType.BARBELL) e.barWeight else 0.0,
            dumbbellCount = e.dumbbellCount,
        )
    }

    private fun weightLabel(e: Exercise, s: WorkSet): String = when (e.weightType) {
        // Sin discos no hay nada que desglosar: "6 kg . 6 + 0" es ruido, el total ya lo dice.
        WeightType.BARBELL -> if (s.weight == 0.0) "" else "${fmtKg(e.barWeight)} + ${fmtKg(s.weight)}"
        WeightType.DUMBBELL -> "${e.dumbbellCount} × ${fmtKg(s.weight)}"
        WeightType.TOTAL, WeightType.NONE -> ""
    }

    private fun fmtKg(d: Double): String {
        val r = (d * 10).toLong()
        return if (r % 10 == 0L) (r / 10).toString() else (r / 10.0).toString()
    }

    /**
     * Calcula el siguiente [Workout.rotationIndex] para un workout rotativo.
     * Retorna null si el workout no rota o no tiene variantes (nada que rotar).
     */
    fun nextRotationIndex(w: Workout): Int? =
        if (w.rotating && w.variants.isNotEmpty())
            (w.rotationIndex + 1) % w.variants.size
        else null

    /**
     * Dado el set de workouts ya avanzados en esta corrida [advancedWorkouts] y el
     * rango [0, uptoExclusive), retorna los índices que faltan por rotar.
     * Es la base de la idempotencia: llamar dos veces con el mismo rango no produce
     * rotaciones duplicadas porque los índices ya avanzados se excluyen.
     */
    fun workoutsToRotate(advancedWorkouts: Set<Int>, uptoExclusive: Int): List<Int> =
        (0 until uptoExclusive).filter { it !in advancedWorkouts }
}

/**
 * Cuánto se estima que dura este training, en segundos (TD-040).
 *
 * Sale de la misma cola que va a correr el player, así que cuenta lo que de verdad va a
 * pasar: las variantes ACTIVAS de un workout rotativo, los dos lados de un ejercicio
 * unilateral, las preparaciones y los descansos. Lo que va por repeticiones se pondera con
 * [PlayerStep.estimatedSec] —reps por segundos por repetición—, que es lo único estimado
 * aquí; el resto son segundos de reloj.
 *
 * **Es un suelo, no un pronóstico.** No incluye lo que pasa entre ejercicios -cambiar
 * discos, buscar la mancuerna, contestar el feedback-, y por eso en la tarjeta se enseña
 * con una tilde delante.
 */
fun Training.estimatedSec(): Int = StepEngine.buildSteps(this).sumOf { it.estimatedSec }
