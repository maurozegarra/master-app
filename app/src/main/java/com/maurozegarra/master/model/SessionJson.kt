package com.maurozegarra.master.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Encode/decode de [SessionLog] a JSON. Puro (sin Context): testeable en JVM.
 * Migración: sesiones viejas sin campos nuevos → defaults compatibles.
 */
object SessionJson {

    fun encode(list: List<SessionLog>): String {
        val arr = JSONArray()
        list.forEach { s ->
            val exercises = JSONArray()
            s.exercises.forEach { er ->
                val setsArr = JSONArray()
                er.sets.forEach { sr ->
                    setsArr.put(JSONObject()
                        .put("reps", sr.reps)
                        .put("weightKg", sr.weightKg)
                        .put("durationSec", sr.durationSec))
                }
                val erObj = JSONObject()
                    .put("exerciseId", er.exerciseId)
                    .put("name", er.name)
                    .put("workoutName", er.workoutName)
                    .put("workoutIndex", er.workoutIndex)
                    .put("setsCompleted", er.setsCompleted)
                    .put("totalSets", er.totalSets)
                    .put("sets", setsArr)
                    .put("timeBased", er.timeBased)
                    .put("totalExercisesInWorkout", er.totalExercisesInWorkout)
                if (er.feedbackDeltaKg != null) erObj.put("feedbackDeltaKg", er.feedbackDeltaKg)
                exercises.put(erObj)
            }
            arr.put(JSONObject()
                .put("id", s.id)
                .put("trainingId", s.trainingId)
                .put("trainingName", s.trainingName)
                .put("completedAt", s.completedAt)
                .put("startedAt", s.startedAt)
                .put("status", s.status.name)
                .put("durationSec", s.durationSec)
                .put("exercises", exercises))
        }
        return arr.toString()
    }

    fun decode(json: String): List<SessionLog> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val exercises = mutableListOf<ExerciseRecord>()
            o.optJSONArray("exercises")?.let { ea ->
                for (j in 0 until ea.length()) {
                    val eo = ea.getJSONObject(j)
                    val sets = mutableListOf<SetRecord>()
                    eo.optJSONArray("sets")?.let { sa ->
                        for (k in 0 until sa.length()) {
                            val so = sa.getJSONObject(k)
                            sets.add(SetRecord(
                                reps = so.optInt("reps", 0),
                                weightKg = so.optDouble("weightKg", 0.0),
                                durationSec = so.optInt("durationSec", 0),
                            ))
                        }
                    }
                    exercises.add(ExerciseRecord(
                        exerciseId = eo.optString("exerciseId", ""),
                        name = eo.optString("name", ""),
                        workoutName = eo.optString("workoutName", ""),
                        workoutIndex = eo.optInt("workoutIndex", 0),
                        setsCompleted = eo.optInt("setsCompleted", sets.size),
                        totalSets = eo.optInt("totalSets", sets.size),
                        sets = sets,
                        timeBased = eo.optBoolean("timeBased", true),
                        totalExercisesInWorkout = eo.optInt("totalExercisesInWorkout", 0),
                        feedbackDeltaKg = if (eo.has("feedbackDeltaKg"))
                            eo.optDouble("feedbackDeltaKg", 0.0) else null,
                    ))
                }
            }
            SessionLog(
                id = o.getLong("id"),
                trainingId = o.optLong("trainingId", 0L),
                trainingName = o.optString("trainingName", ""),
                completedAt = o.optLong("completedAt", 0L),
                startedAt = o.optLong("startedAt", 0L),
                status = runCatching { SessionStatus.valueOf(o.optString("status")) }
                    .getOrDefault(SessionStatus.COMPLETED),
                exercises = exercises,
                durationSec = o.optInt("durationSec", 0),
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}
