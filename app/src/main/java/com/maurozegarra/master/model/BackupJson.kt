package com.maurozegarra.master.model

import org.json.JSONArray
import org.json.JSONObject

/** Todo lo que el usuario puede perder: es lo que entra y sale de un respaldo. */
data class BackupData(
    val trainings: List<Training>,
    val customExercises: List<ExerciseDef>,
    val sessions: List<SessionLog>,
    /**
     * Vídeos e instrucciones por ejercicio (formato 2). Solo el mapa: los vídeos son
     * archivos en `Movies/MASTER/` y sobreviven por su cuenta.
     */
    val exerciseMedia: Map<String, ExerciseMedia> = emptyMap(),
    /**
     * Las sesiones de los atletas que bajó el coach (formato 3, TD-126). Van en el respaldo
     * porque es por donde el asistente lee el historial: sin esto, el de NIKO seguiría sin
     * poder leerse aunque ya llegara al teléfono del coach.
     */
    val athleteSessions: List<AthleteSession> = emptyList(),
)

/**
 * Formato del archivo de respaldo. Puro a propósito (sin Context ni SharedPreferences)
 * para que el ida y vuelta se pueda testear desde la suite JVM: es la pieza que
 * decide si unos datos se recuperan o se pierden.
 */
object BackupJson {

    /**
     * Versión del formato. La 2 añadió `exerciseMedia`, la 3 `athleteSessions`; [decode]
     * acepta desde la 1, así que los respaldos viejos se siguen importando sin perder nada.
     */
    const val FORMAT = 3

    fun encode(data: BackupData, exportedAt: Long): String {
        val custom = JSONArray()
        data.customExercises.forEach {
            custom.put(JSONObject().put("id", it.id).put("name", it.name).put("custom", true))
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("exportedAt", exportedAt)
            .put("trainings", JSONArray(TrainingJson.encode(data.trainings)))
            .put("customExercises", custom)
            .put("sessions", JSONArray(SessionJson.encode(data.sessions)))
            .put("exerciseMedia", JSONObject(ExerciseMediaJson.encode(data.exerciseMedia)))
            .put("athleteSessions", JSONArray(SessionSync.encodeAthleteSessions(data.athleteSessions)))
            .toString(2)
    }

    /**
     * Devuelve null si el archivo no es un respaldo válido. Se valida todo antes de
     * construir el resultado: quien lo llama va a reemplazar los datos del usuario con
     * esto, y un archivo a medias sería peor que no importar nada.
     */
    fun decode(json: String): BackupData? {
        val root = try { JSONObject(json) } catch (_: Exception) { return null }
        if (root.optInt("format", 0) !in 1..FORMAT) return null

        val trainingsArr = root.optJSONArray("trainings") ?: return null
        val trainings = TrainingJson.decode(trainingsArr.toString())
        // decode() devuelve lista vacía ante JSON corrupto; distinguir "vacío de verdad"
        // de "no se pudo parsear" evita importar un respaldo roto como si fuera vacío.
        if (trainings.isEmpty() && trainingsArr.length() > 0) return null

        val custom = root.optJSONArray("customExercises")?.let { arr ->
            try {
                (0 until arr.length()).map {
                    val o = arr.getJSONObject(it)
                    ExerciseDef(id = o.getString("id"), name = o.getString("name"), custom = true)
                }
            } catch (_: Exception) {
                return null
            }
        } ?: emptyList()

        val sessions = root.optJSONArray("sessions")
            ?.let { SessionJson.decode(it.toString()) }
            ?: emptyList()

        // Ausente en los respaldos de formato 1: se importan igual, sin medios.
        val media = root.optJSONObject("exerciseMedia")
            ?.let { ExerciseMediaJson.decode(it.toString()) }
            ?: emptyMap()

        // Ausente antes del formato 3.
        val athletes = root.optJSONArray("athleteSessions")
            ?.let { SessionSync.decodeAthleteSessions(it.toString()) }
            ?: emptyList()

        return BackupData(
            trainings = trainings,
            customExercises = custom,
            sessions = sessions,
            exerciseMedia = media,
            athleteSessions = athletes,
        )
    }
}
