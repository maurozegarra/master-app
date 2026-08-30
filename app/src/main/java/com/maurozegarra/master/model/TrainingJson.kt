package com.maurozegarra.master.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Serialización de trainings a JSON. Vive fuera de WorkoutStore —que necesita un
 * Context— para poder testearse desde la suite JVM, igual que [SessionJson].
 */
object TrainingJson {

    fun encode(items: List<Training>): String {
        val arr = JSONArray()
        items.forEach { arr.put(toJson(it)) }
        return arr.toString()
    }

    fun decode(json: String): List<Training> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
    } catch (_: Exception) {
        emptyList()
    }

    fun toJson(tr: Training): JSONObject {
        val workouts = JSONArray()
        tr.workouts.forEach { workouts.put(workoutToJson(it)) }
        return JSONObject()
            .put("id", tr.id)
            .put("uid", tr.uid)
            .put("assigned", tr.assigned)
            .put("name", tr.name)
            .put("createdAt", tr.createdAt)
            .put("updatedAt", tr.updatedAt)
            .put("workouts", workouts)
    }

    fun fromJson(o: JSONObject): Training {
        val workouts = mutableListOf<Workout>()
        o.optJSONArray("workouts")?.let { wa ->
            for (i in 0 until wa.length()) workouts.add(workoutFromJson(wa.getJSONObject(i)))
        }
        return Training(
            id = o.getLong("id"),
            // Los trainings guardados antes de que existiera el uid llegan sin el; se lo
            // pone WorkoutStore al cargar, que es quien puede persistirlo.
            uid = o.optString("uid", ""),
            assigned = o.optBoolean("assigned", false),
            name = o.optString("name", ""),
            workouts = workouts,
            createdAt = o.optLong("createdAt", 0L),
            updatedAt = o.optLong("updatedAt", 0L),
        )
    }

    private fun workoutToJson(w: Workout): JSONObject {
        val exercises = JSONArray()
        w.exercises.forEach { exercises.put(exerciseToJson(it)) }
        val variants = JSONArray()
        w.variants.forEach { variants.put(variantToJson(it)) }
        return JSONObject()
            .put("id", w.id)
            .put("name", w.name)
            .put("exercises", exercises)
            .put("rotating", w.rotating)
            .put("rotationIndex", w.rotationIndex)
            .put("variants", variants)
    }

    private fun workoutFromJson(o: JSONObject): Workout {
        val exercises = mutableListOf<Exercise>()
        o.optJSONArray("exercises")?.let { ea ->
            for (i in 0 until ea.length()) exercises.add(exerciseFromJson(ea.getJSONObject(i)))
        }
        val variants = mutableListOf<WorkoutVariant>()
        o.optJSONArray("variants")?.let { va ->
            for (i in 0 until va.length()) variants.add(variantFromJson(va.getJSONObject(i)))
        }
        return Workout(
            id = o.getLong("id"),
            name = o.optString("name", ""),
            exercises = exercises,
            rotating = o.optBoolean("rotating", false),
            rotationIndex = o.optInt("rotationIndex", 0),
            variants = variants,
        )
    }

    private fun variantToJson(v: WorkoutVariant): JSONObject {
        val exercises = JSONArray()
        v.exercises.forEach { exercises.put(exerciseToJson(it)) }
        return JSONObject()
            .put("id", v.id)
            .put("name", v.name)
            .put("exercises", exercises)
    }

    private fun variantFromJson(o: JSONObject): WorkoutVariant {
        val exercises = mutableListOf<Exercise>()
        o.optJSONArray("exercises")?.let { ea ->
            for (i in 0 until ea.length()) exercises.add(exerciseFromJson(ea.getJSONObject(i)))
        }
        return WorkoutVariant(
            id = o.getLong("id"),
            name = o.optString("name", ""),
            exercises = exercises,
        )
    }

    private fun exerciseToJson(e: Exercise): JSONObject {
        val sets = JSONArray()
        e.setList.forEach { sets.put(JSONObject().put("reps", it.reps).put("weight", it.weight)) }
        return JSONObject()
            .put("id", e.id)
            .put("exerciseId", e.exerciseId)
            .put("name", e.name)
            .put("note", e.note)
            .put("prepareSec", e.prepareSec)
            .put("sets", e.sets)
            .put("workMode", e.workMode.name)
            .put("workValue", e.workValue)
            .put("secPerRep", e.secPerRep)
            .put("restSec", e.restSec)
            .put("restSkipOnLastSet", e.restSkipOnLastSet)
            .put("cooldownSec", e.cooldownSec)
            .put("weightType", e.weightType.name)
            .put("barWeight", e.barWeight)
            .put("setList", sets)
            .put("prepareCfg", stageToJson(e.prepareCfg))
            .put("workCfg", stageToJson(e.workCfg))
            .put("restCfg", stageToJson(e.restCfg))
            .put("cooldownCfg", stageToJson(e.cooldownCfg))
    }

    private fun exerciseFromJson(o: JSONObject): Exercise {
        val setList = mutableListOf<WorkSet>()
        o.optJSONArray("setList")?.let { sa ->
            for (i in 0 until sa.length()) {
                val s = sa.getJSONObject(i)
                setList.add(WorkSet(reps = s.optInt("reps", 12), weight = s.optDouble("weight", 0.0)))
            }
        }
        return Exercise(
            id = o.getLong("id"),
            exerciseId = o.optString("exerciseId", ""),
            name = o.optString("name", ""),
            note = o.optString("note", ""),
            prepareSec = o.optInt("prepareSec", 0),
            sets = o.optInt("sets", 1),
            workMode = runCatching { WorkMode.valueOf(o.optString("workMode")) }.getOrDefault(WorkMode.TIME),
            workValue = o.optInt("workValue", 30),
            secPerRep = o.optInt("secPerRep", 3),
            restSec = o.optInt("restSec", 30),
            restSkipOnLastSet = o.optBoolean("restSkipOnLastSet", true),
            cooldownSec = o.optInt("cooldownSec", 0),
            weightType = runCatching { WeightType.valueOf(o.optString("weightType")) }.getOrDefault(WeightType.NONE),
            barWeight = o.optDouble("barWeight", 20.0),
            setList = setList,
            prepareCfg = stageFromJson(o.optJSONObject("prepareCfg"), StageConfig.COLOR_PREPARE, 3),
            workCfg = stageFromJson(o.optJSONObject("workCfg"), StageConfig.COLOR_WORK, 0),
            restCfg = stageFromJson(o.optJSONObject("restCfg"), StageConfig.COLOR_REST, 3),
            cooldownCfg = stageFromJson(o.optJSONObject("cooldownCfg"), StageConfig.COLOR_COOLDOWN, 0),
        )
    }

    private fun stageToJson(c: StageConfig): JSONObject {
        val o = JSONObject()
            .put("color", c.color)
            .put("display", c.display.name)
            .put("alarm", c.alarm)
            .put("finalCount", c.finalCount)
            .put("confirm", c.confirm.name)
        if (c.beepSoundUri != null) o.put("beepSoundUri", c.beepSoundUri)
        if (c.beepSoundName != null) o.put("beepSoundName", c.beepSoundName)
        return o
    }

    private fun stageFromJson(o: JSONObject?, defColor: Long, defFinal: Int): StageConfig {
        if (o == null) return StageConfig(color = defColor, finalCount = defFinal)
        return StageConfig(
            color = o.optLong("color", defColor),
            display = runCatching { DisplayMode.valueOf(o.optString("display")) }.getOrDefault(DisplayMode.COUNTDOWN),
            alarm = o.optBoolean("alarm", true),
            finalCount = o.optInt("finalCount", defFinal),
            confirm = runCatching { ConfirmMode.valueOf(o.optString("confirm")) }.getOrDefault(ConfirmMode.AUTO),
            beepSoundUri = o.optString("beepSoundUri", "").takeIf { it.isNotBlank() && it != "null" },
            beepSoundName = o.optString("beepSoundName", "").takeIf { it.isNotBlank() && it != "null" },
        )
    }
}
