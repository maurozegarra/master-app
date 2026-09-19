package com.maurozegarra.master.model

/**
 * Acumulador puro de pasos WORK completados/skipped → [ExerciseRecord].
 * Sin dependencias de Android: testeable en JVM.
 *
 * Uso: llamar [onWorkStepCompleted] o [onWorkStepSkipped] por cada paso WORK
 * que el player ya pasó. Al terminar, [build] devuelve los registros acumulados.
 */
class SessionRecorder {

    private val records = mutableMapOf<ExerciseKey, ExerciseRecord>()
    private val sets = mutableMapOf<ExerciseKey, MutableMap<Int, SetRecord>>()

    /**
     * Lo marcado en "How did the weight feel?", por serie (TD-117).
     *
     * Va APARTE de [records] y [sets] porque ahí no sobrevivía: el registro de un ejercicio
     * nace al completar su primera serie y se reconstruye entero en cada serie siguiente, y
     * la tarjeta se toca DURANTE la serie. Lo marcado en la primera se descartaba por no
     * haber registro todavía, y lo marcado después se borraba al terminar esa serie. En
     * todo el historial no había quedado ni un toque. Guardado aquí, espera a su serie y se
     * le pega al armar el registro, sea cual sea el orden.
     */
    private val feedback = mutableMapOf<ExerciseKey, MutableMap<Int, Double>>()

    /**
     * La velocidad que el usuario puso de verdad, cuando difiere de la prescrita (TD-124).
     *
     * Mismo sitio aparte y por la misma razón que [feedback]: se toca mientras la serie
     * corre, y el registro de esa serie todavía no existe o se va a rehacer.
     */
    private val speed = mutableMapOf<ExerciseKey, MutableMap<Int, Double>>()
    private var totalExercisesByWorkout = mutableMapOf<Int, Int>()

    private data class ExerciseKey(
        val exerciseId: String,
        val workoutIndex: Int,
    )

    fun setTotalExercisesByWorkout(map: Map<Int, Int>) {
        totalExercisesByWorkout.clear()
        totalExercisesByWorkout.putAll(map)
    }

    fun onWorkStepCompleted(step: PlayerStep) {
        if (step.kind != StepKind.WORK) return
        val setRecord = if (step.timeBased) {
            SetRecord(reps = step.reps, weightKg = step.weightTotal, durationSec = step.durationSec, speedKmh = step.speedKmh)
        } else {
            SetRecord(reps = step.reps, weightKg = step.weightTotal, durationSec = 0, speedKmh = step.speedKmh)
        }
        putSet(step, setRecord)
    }

    fun onWorkStepSkipped(step: PlayerStep) {
        if (step.kind != StepKind.WORK) return
        val setRecord = if (step.timeBased) {
            SetRecord(reps = step.reps, weightKg = step.weightTotal, durationSec = step.durationSec, skipped = true, speedKmh = step.speedKmh)
        } else {
            SetRecord(reps = step.reps, weightKg = step.weightTotal, durationSec = 0, skipped = true, speedKmh = step.speedKmh)
        }
        putSet(step, setRecord)
    }

    private fun putSet(step: PlayerStep, setRecord: SetRecord) {
        val key = ExerciseKey(step.ownerExerciseId, step.workoutIndex)
        val setMap = sets.getOrPut(key) { mutableMapOf() }
        setMap[step.setIndex] = setRecord
        val orderedSets = orderedWithFeedback(key, setMap, step.totalSets)
        val completedCount = orderedSets.count { !it.skipped }
        records[key] = ExerciseRecord(
            exerciseId = step.ownerExerciseId,
            name = step.ownerName,
            workoutName = step.workoutName,
            workoutIndex = step.workoutIndex,
            exerciseIndex = step.exerciseIndex,
            setsCompleted = completedCount,
            totalSets = step.totalSets,
            sets = orderedSets,
            timeBased = step.timeBased,
            totalExercisesInWorkout = totalExercisesByWorkout[step.workoutIndex] ?: 0,
            status = deriveStatus(orderedSets, step.totalSets),
        )
    }

    /** Lo marcado en la serie [setIndex]; si se vuelve a tocar, gana el último toque. */
    fun setFeedback(exerciseId: String, workoutIndex: Int, setIndex: Int, deltaKg: Double) {
        feedback.getOrPut(ExerciseKey(exerciseId, workoutIndex)) { mutableMapOf() }[setIndex] = deltaKg
    }

    /** La velocidad que se puso en la serie [setIndex]; gana sobre la prescrita. */
    fun setSpeed(exerciseId: String, workoutIndex: Int, setIndex: Int, kmh: Double) {
        speed.getOrPut(ExerciseKey(exerciseId, workoutIndex)) { mutableMapOf() }[setIndex] = kmh
    }

    private fun orderedWithFeedback(key: ExerciseKey, setMap: Map<Int, SetRecord>, totalSets: Int): List<SetRecord> {
        val marked = feedback[key].orEmpty()
        val puesta = speed[key].orEmpty()
        return ordered(
            setMap.mapValues { (i, sr) ->
                var r = sr
                marked[i]?.let { r = r.copy(feedbackDeltaKg = it) }
                puesta[i]?.let { r = r.copy(speedKmh = it) }
                r
            },
            totalSets,
        )
    }

    private fun deriveStatus(orderedSets: List<SetRecord>, totalSets: Int): ExerciseStatus {
        if (orderedSets.isEmpty()) return ExerciseStatus.PARTIAL
        val allSkipped = orderedSets.all { it.skipped }
        if (allSkipped) return ExerciseStatus.SKIPPED
        val completedCount = orderedSets.count { !it.skipped }
        return if (completedCount >= totalSets) ExerciseStatus.COMPLETED else ExerciseStatus.PARTIAL
    }

    /**
     * Los registros en el orden en que los manda la rutina, no en el que se completaron.
     *
     * Ordenar por nombre -como se hacia hasta TD-099- dejaba el historial contando una
     * sesion que nadie hizo: dentro de los tres de McGill el bird dog salia primero por la
     * B, y el curl-up, que es el que abre, cuarto.
     *
     * Y no vale con no ordenar y confiar en el orden de insercion: quien salta hacia
     * delante y vuelve atras registra los ejercicios en un orden que no es el de la rutina.
     */
    fun build(): List<ExerciseRecord> =
        records.values.map { er ->
            val key = ExerciseKey(er.exerciseId, er.workoutIndex)
            val orderedSets = sets[key]?.let { orderedWithFeedback(key, it, er.totalSets) } ?: er.sets
            er.copy(
                setsCompleted = orderedSets.count { !it.skipped },
                sets = orderedSets,
                feedbackDeltaKg = orderedSets.lastOrNull { it.feedbackDeltaKg != null }?.feedbackDeltaKg
                    ?: er.feedbackDeltaKg,
                status = deriveStatus(orderedSets, er.totalSets),
            )
        }.sortedWith(compareBy({ it.workoutIndex }, { it.exerciseIndex }))

    /**
     * Las series en orden, recorriendo **hasta la última registrada** aunque el plan diga
     * menos.
     *
     * Recorrer solo `0 until totalSets` perdía trabajo ya hecho: si a mitad de corrida se
     * baja el número de series por debajo de las que ya se hicieron —que es justo lo que
     * permite editar el training en marcha—, las de más desaparecían del historial. Lo que
     * se hizo, se hizo.
     */
    private fun ordered(setMap: Map<Int, SetRecord>, totalSets: Int): List<SetRecord> {
        val last = setMap.keys.maxOrNull() ?: -1
        return (0 until maxOf(totalSets, last + 1)).mapNotNull { setMap[it] }
    }

    fun isEmpty(): Boolean = records.isEmpty()

    fun clear() {
        records.clear()
        sets.clear()
        feedback.clear()
        speed.clear()
    }
}
