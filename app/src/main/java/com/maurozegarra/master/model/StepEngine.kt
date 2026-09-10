package com.maurozegarra.master.model

/**
 * Motor puro de generación de pasos: convierte un [Training] en la lista
 * aplanada de [PlayerStep] que reproduce el player. Sin dependencias de
 * Android (testeable con unit tests puros).
 */
object StepEngine {

    fun buildSteps(t: Training): List<PlayerStep> = buildList {
        val tw = t.workouts.size.coerceAtLeast(1)
        t.workouts.forEachIndexed { wi, w ->
            val wName = w.activeName()
            val wVariant = if (w.rotating) (w.activeVariant()?.name ?: "") else ""
            w.activeExercises().forEachIndexed { ei, e ->
                if (e.prepareSec > 0) {
                    add(stageStep(StepKind.PREP, e, wName, wi, tw, durationSec = e.prepareSec, workoutBase = w.name, variant = wVariant, rotating = w.rotating, exerciseIndex = ei))
                }
                val sets = e.sets.coerceAtLeast(1)
                for (s in 0 until sets) {
                    if (e.workMode == WorkMode.TIME) {
                        add(stageStep(StepKind.WORK, e, wName, wi, tw, durationSec = e.workValue, setIndex = s, totalSets = sets, timeBased = true, workoutBase = w.name, variant = wVariant, rotating = w.rotating, exerciseIndex = ei))
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
                                secPerRep = e.secPerRep, exerciseIndex = ei,
                            ),
                        )
                    }
                    val lastSet = s == sets - 1
                    if (e.restSec > 0 && !(e.restSkipOnLastSet && lastSet)) {
                        add(stageStep(StepKind.REST, e, wName, wi, tw, durationSec = e.restSec, setIndex = s, totalSets = sets, workoutBase = w.name, variant = wVariant, rotating = w.rotating, exerciseIndex = ei))
                    }
                }
                if (e.cooldownSec > 0) {
                    add(stageStep(StepKind.COOLDOWN, e, wName, wi, tw, durationSec = e.cooldownSec, workoutBase = w.name, variant = wVariant, rotating = w.rotating, exerciseIndex = ei))
                }
            }
        }
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

    private fun compareSteps(a: PlayerStep, b: PlayerStep): Int = compareValuesBy(
        a, b,
        { it.workoutIndex },
        { it.exerciseIndex },
        { stageGroup(it) },
        { if (stageGroup(it) == 1) it.setIndex else 0 },
        { if (it.kind == StepKind.REST) 1 else 0 },
    )

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
        )
    }

    private fun weightLabel(e: Exercise, s: WorkSet): String = when (e.weightType) {
        WeightType.BARBELL -> "${fmtKg(e.barWeight)} + ${fmtKg(s.weight)}"
        WeightType.DUMBBELL -> "2 × ${fmtKg(s.weight)}"
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
