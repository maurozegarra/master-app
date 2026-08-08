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
            SetRecord(reps = step.reps, weightKg = step.weightTotal, durationSec = step.durationSec)
        } else {
            SetRecord(reps = step.reps, weightKg = step.weightTotal, durationSec = 0)
        }
        putSet(step, setRecord)
    }

    fun onWorkStepSkipped(step: PlayerStep) {
        if (step.kind != StepKind.WORK) return
        val setRecord = if (step.timeBased) {
            SetRecord(reps = step.reps, weightKg = step.weightTotal, durationSec = step.durationSec, skipped = true)
        } else {
            SetRecord(reps = step.reps, weightKg = step.weightTotal, durationSec = 0, skipped = true)
        }
        putSet(step, setRecord)
    }

    private fun putSet(step: PlayerStep, setRecord: SetRecord) {
        val key = ExerciseKey(step.ownerExerciseId, step.workoutIndex)
        val setMap = sets.getOrPut(key) { mutableMapOf() }
        setMap[step.setIndex] = setRecord
        val orderedSets = (0 until step.totalSets).mapNotNull { setMap[it] }
        val completedCount = orderedSets.count { !it.skipped }
        records[key] = ExerciseRecord(
            exerciseId = step.ownerExerciseId,
            name = step.ownerName,
            workoutName = step.workoutName,
            workoutIndex = step.workoutIndex,
            setsCompleted = completedCount,
            totalSets = step.totalSets,
            sets = orderedSets,
            timeBased = step.timeBased,
            totalExercisesInWorkout = totalExercisesByWorkout[step.workoutIndex] ?: 0,
            status = deriveStatus(orderedSets, step.totalSets),
        )
    }

    fun setFeedback(exerciseId: String, workoutIndex: Int, deltaKg: Double) {
        val key = ExerciseKey(exerciseId, workoutIndex)
        records[key]?.let { existing ->
            records[key] = existing.copy(feedbackDeltaKg = deltaKg)
        }
    }

    private fun deriveStatus(orderedSets: List<SetRecord>, totalSets: Int): ExerciseStatus {
        if (orderedSets.isEmpty()) return ExerciseStatus.PARTIAL
        val allSkipped = orderedSets.all { it.skipped }
        if (allSkipped) return ExerciseStatus.SKIPPED
        val completedCount = orderedSets.count { !it.skipped }
        return if (completedCount >= totalSets) ExerciseStatus.COMPLETED else ExerciseStatus.PARTIAL
    }

    fun build(): List<ExerciseRecord> =
        records.values.map { er ->
            val key = ExerciseKey(er.exerciseId, er.workoutIndex)
            val orderedSets = sets[key]?.let { sm ->
                (0 until er.totalSets).mapNotNull { sm[it] }
            } ?: er.sets
            er.copy(
                setsCompleted = orderedSets.count { !it.skipped },
                sets = orderedSets,
                status = deriveStatus(orderedSets, er.totalSets),
            )
        }.sortedWith(compareBy({ it.workoutIndex }, { it.name }))

    fun isEmpty(): Boolean = records.isEmpty()

    fun clear() {
        records.clear()
        sets.clear()
    }
}
