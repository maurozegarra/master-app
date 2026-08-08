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
            w.activeExercises().forEach { e ->
                if (e.prepareSec > 0) {
                    add(stageStep(StepKind.PREP, e, wName, wi, tw, durationSec = e.prepareSec, workoutBase = w.name, variant = wVariant, rotating = w.rotating))
                }
                val sets = e.sets.coerceAtLeast(1)
                for (s in 0 until sets) {
                    if (e.workMode == WorkMode.TIME) {
                        add(stageStep(StepKind.WORK, e, wName, wi, tw, durationSec = e.workValue, setIndex = s, totalSets = sets, timeBased = true, workoutBase = w.name, variant = wVariant, rotating = w.rotating))
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
                                secPerRep = e.secPerRep,
                            ),
                        )
                    }
                    val lastSet = s == sets - 1
                    if (e.restSec > 0 && !(e.restSkipOnLastSet && lastSet)) {
                        add(stageStep(StepKind.REST, e, wName, wi, tw, durationSec = e.restSec, setIndex = s, totalSets = sets, workoutBase = w.name, variant = wVariant, rotating = w.rotating))
                    }
                }
                if (e.cooldownSec > 0) {
                    add(stageStep(StepKind.COOLDOWN, e, wName, wi, tw, durationSec = e.cooldownSec, workoutBase = w.name, variant = wVariant, rotating = w.rotating))
                }
            }
        }
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
