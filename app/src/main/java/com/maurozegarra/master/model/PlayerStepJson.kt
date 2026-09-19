package com.maurozegarra.master.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Encode/decode de los pasos del player. Puro, sin Android: se prueba en JVM (TD-129).
 *
 * Los pasos viajan del app al servicio del player como JSON -por el Intent al arrancar y
 * en las preferencias para sobrevivir a que el sistema mate el proceso-, así que **todo lo
 * que el player necesita tiene que pasar por aquí**. Lo que no se escriba, el servicio no
 * lo tiene.
 *
 * Vivía dentro de WorkoutPlayerService, escrito campo a campo, y se había quedado atrás:
 * cuando la caminata ganó velocidad (TD-124), `speedKmh` no se agregó a la lista, el
 * servicio recibió la caminata sin ella y la sesión del 19-sep se registró sin velocidad.
 * Faltaban además `workoutBaseName`, `variantName`, `rotating` y `secPerRep`. El test que lo
 * acompaña construye un paso con TODOS los campos distintos de su valor por defecto: el
 * próximo campo que se agregue a [PlayerStep] y no se agregue aquí, lo rompe.
 */
object PlayerStepJson {

    fun encode(list: List<PlayerStep>): String {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(
                JSONObject()
                    .put("kind", s.kind.name)
                    .put("title", s.title)
                    .put("note", s.note)
                    .put("ownerName", s.ownerName)
                    .put("ownerExerciseId", s.ownerExerciseId)
                    .put("exerciseIndex", s.exerciseIndex)
                    .put("showVideo", s.showVideo)
                    .put("workoutName", s.workoutName)
                    .put("workoutIndex", s.workoutIndex)
                    .put("totalWorkouts", s.totalWorkouts)
                    .put("setIndex", s.setIndex)
                    .put("totalSets", s.totalSets)
                    .put("durationSec", s.durationSec)
                    .put("reps", s.reps)
                    .put("timeBased", s.timeBased)
                    .put("display", s.display.name)
                    .put("confirm", s.confirm.name)
                    .put("finalCount", s.finalCount)
                    .apply { if (s.beepSoundUri != null) put("beepSoundUri", s.beepSoundUri) }
                    .put("alarm", s.alarm)
                    .put("colorArgb", s.colorArgb)
                    .put("weighted", s.weighted)
                    .put("weightTotal", s.weightTotal)
                    .put("weightLabel", s.weightLabel)
                    .put("workoutBaseName", s.workoutBaseName)
                    .put("variantName", s.variantName)
                    .put("rotating", s.rotating)
                    .put("secPerRep", s.secPerRep)
                    .apply { s.speedKmh?.let { put("speedKmh", it) } }
                    .put("weightType", s.weightType.name)
                    .put("barWeight", s.barWeight)
                    .put("dumbbellCount", s.dumbbellCount),
            )
        }
        return arr.toString()
    }

    fun decode(json: String): List<PlayerStep> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            PlayerStep(
                kind = StepKind.valueOf(o.getString("kind")),
                title = o.optString("title", ""),
                note = o.optString("note", ""),
                ownerName = o.optString("ownerName", ""),
                ownerExerciseId = o.optString("ownerExerciseId", ""),
                exerciseIndex = o.optInt("exerciseIndex", 0),
                showVideo = o.optBoolean("showVideo", true),
                workoutName = o.optString("workoutName", ""),
                workoutIndex = o.optInt("workoutIndex", 0),
                totalWorkouts = o.optInt("totalWorkouts", 1),
                setIndex = o.optInt("setIndex", 0),
                totalSets = o.optInt("totalSets", 1),
                durationSec = o.optInt("durationSec", 0),
                reps = o.optInt("reps", 0),
                timeBased = o.optBoolean("timeBased", true),
                display = runCatching { DisplayMode.valueOf(o.optString("display")) }.getOrDefault(DisplayMode.COUNTDOWN),
                confirm = runCatching { ConfirmMode.valueOf(o.optString("confirm")) }.getOrDefault(ConfirmMode.AUTO),
                finalCount = o.optInt("finalCount", 0),
                beepSoundUri = o.optString("beepSoundUri", "").takeIf { it.isNotBlank() && it != "null" },
                alarm = o.optBoolean("alarm", true),
                colorArgb = o.optLong("colorArgb", 0xFF2E9E5BL),
                weighted = o.optBoolean("weighted", false),
                weightTotal = o.optDouble("weightTotal", 0.0),
                weightLabel = o.optString("weightLabel", ""),
                workoutBaseName = o.optString("workoutBaseName", ""),
                variantName = o.optString("variantName", ""),
                rotating = o.optBoolean("rotating", false),
                secPerRep = o.optInt("secPerRep", 3),
                speedKmh = if (o.has("speedKmh")) o.optDouble("speedKmh", 0.0) else null,
                weightType = runCatching { WeightType.valueOf(o.optString("weightType")) }.getOrDefault(WeightType.NONE),
                barWeight = o.optDouble("barWeight", 0.0),
                dumbbellCount = o.optInt("dumbbellCount", 2),
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}
