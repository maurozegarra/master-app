package com.maurozegarra.master.model

/**
 * Acumulador puro de pasos WORK completados → [ExerciseRecord].
 * Sin dependencias de Android: testeable en JVM.
 *
 * Uso: llamar [onWorkStepCompleted] por cada paso WORK que el player ya pasó
 * (índice < actual). Al terminar, [build] devuelve los registros acumulados.
 */
class SessionRecorder {

    private val records = mutableMapOf<ExerciseKey, ExerciseRecord>()
    private val sets = mutableMapOf<ExerciseKey, MutableList<SetRecord>>()
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
        val key = ExerciseKey(step.ownerExerciseId, step.workoutIndex)
        val setRecord = if (step.timeBased) {
            SetRecord(reps = step.reps, weightKg = step.weightTotal, durationSec = step.durationSec)
        } else {
            SetRecord(reps = step.reps, weightKg = step.weightTotal, durationSec = 0)
        }
        sets.getOrPut(key) { mutableListOf() }.add(setRecord)
        records[key] = ExerciseRecord(
            exerciseId = step.ownerExerciseId,
            name = step.ownerName,
            workoutName = step.workoutName,
            workoutIndex = step.workoutIndex,
            setsCompleted = sets[key]!!.size,
            totalSets = step.totalSets,
            sets = sets[key]!!.toList(),
            timeBased = step.timeBased,
            totalExercisesInWorkout = totalExercisesByWorkout[step.workoutIndex] ?: 0,
        )
    }

    fun setFeedback(exerciseId: String, workoutIndex: Int, deltaKg: Double) {
        val key = ExerciseKey(exerciseId, workoutIndex)
        records[key]?.let { existing ->
            records[key] = existing.copy(feedbackDeltaKg = deltaKg)
        }
    }

    fun build(): List<ExerciseRecord> =
        records.values.sortedWith(compareBy({ it.workoutIndex }, { it.name }))

    fun isEmpty(): Boolean = records.isEmpty()

    fun clear() {
        records.clear()
        sets.clear()
    }
}
