package com.maurozegarra.master.data

import android.content.Context
import com.maurozegarra.master.model.ExerciseDef
import com.maurozegarra.master.model.BackupData
import com.maurozegarra.master.model.BackupJson
import com.maurozegarra.master.model.SessionJson
import com.maurozegarra.master.model.SessionLog
import com.maurozegarra.master.model.Training
import com.maurozegarra.master.model.TrainingJson
import com.maurozegarra.master.model.withUids
import org.json.JSONArray
import org.json.JSONObject

/** Qué trajo un respaldo importado, para poder reportarlo al usuario. */
data class ImportSummary(val trainings: Int, val sessions: Int)

/**
 * Persistencia de trainings, ejercicios propios e historial
 * con SharedPreferences + JSON.
 */
class WorkoutStore(context: Context, private val media: ExerciseMediaStore) {

    private val appCtx = context.applicationContext
    private val prefs = appCtx
        .getSharedPreferences("master", Context.MODE_PRIVATE)

    init {
        migrateFromLegacyKey()
    }

    private fun migrateFromLegacyKey() {
        if (prefs.contains(KEY_TRAININGS)) return
        val legacy = appCtx.getSharedPreferences("athlete", Context.MODE_PRIVATE)
        val all = legacy.all
        if (all.isEmpty()) return
        prefs.edit().apply {
            all.forEach { (k, v) ->
                when (v) {
                    is String -> putString(k, v)
                    is Boolean -> putBoolean(k, v)
                    is Int -> putInt(k, v)
                    is Long -> putLong(k, v)
                    is Float -> putFloat(k, v)
                    is Set<*> -> @Suppress("UNCHECKED_CAST") putStringSet(k, v as Set<String>)
                }
            }
        }.apply()
    }

    // ---------- Trainings ----------

    /**
     * Nada se guarda sin [Training.uid]. Es el único punto de escritura, así que aquí se
     * cubren de una vez los defaults sembrados y los trainings de un respaldo antiguo.
     */
    fun saveTrainings(items: List<Training>) {
        prefs.edit().putString(KEY_TRAININGS, TrainingJson.encode(items.withUids { newUid() })).apply()
    }

    /** true si nunca se ha guardado la lista de trainings (instalación limpia). */
    fun isFirstRun(): Boolean = !prefs.contains(KEY_TRAININGS)

    /** Marca de migración: si ya se sembró el training "Friki Niki" (una sola vez). */
    fun isFrikiSeeded(): Boolean = prefs.getBoolean(KEY_FRIKI_SEEDED, false)
    fun setFrikiSeeded() { prefs.edit().putBoolean(KEY_FRIKI_SEEDED, true).apply() }

    /** Marca de migración: si ya se aplicó la versión v2 del training "Master". */
    fun isMasterV2Seeded(): Boolean = prefs.getBoolean(KEY_MASTER_V2, false)
    fun setMasterV2Seeded() { prefs.edit().putBoolean(KEY_MASTER_V2, true).apply() }

    /** Marca de migración: si ya se aplicó la versión v3 del training "Master". */
    fun isMasterV3Seeded(): Boolean = prefs.getBoolean(KEY_MASTER_V3, false)
    fun setMasterV3Seeded() { prefs.edit().putBoolean(KEY_MASTER_V3, true).apply() }

    fun loadTrainings(): List<Training> {
        val raw = prefs.getString(KEY_TRAININGS, null) ?: return emptyList()
        val items = TrainingJson.decode(raw)
        val filled = items.withUids { newUid() }
        // Solo se reescribe si alguno cambio: guardar en cada carga seria un efecto
        // secundario gratuito en la operacion mas frecuente del store.
        if (filled != items) saveTrainings(filled)
        return filled
    }

    /** Identidad estable de un training, independiente del dispositivo (ver [Training.uid]). */
    fun newUid(): String = java.util.UUID.randomUUID().toString()

    // ---------- Ejercicios propios (creados por el usuario) ----------

    fun saveCustomExercises(items: List<ExerciseDef>) {
        val arr = JSONArray()
        items.forEach { e ->
            arr.put(JSONObject().put("id", e.id).put("name", e.name).put("custom", true))
        }
        prefs.edit().putString(KEY_CUSTOM_EXERCISES, arr.toString()).apply()
    }

    fun loadCustomExercises(): List<ExerciseDef> {
        val raw = prefs.getString(KEY_CUSTOM_EXERCISES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                ExerciseDef(id = o.getString("id"), name = o.getString("name"), custom = true)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ---------- Historial de sesiones ----------

    fun saveSessions(items: List<SessionLog>) {
        val trimmed = if (items.size > MAX_SESSIONS) items.take(MAX_SESSIONS) else items
        prefs.edit().putString(KEY_SESSIONS, SessionJson.encode(trimmed)).apply()
    }

    fun addSession(log: SessionLog) {
        val list = loadSessions().toMutableList()
        list.add(0, log)
        saveSessions(list)
    }

    fun loadSessions(): List<SessionLog> {
        val raw = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
        return SessionJson.decode(raw)
    }

    // ---------- Respaldo: export / import ----------

    /**
     * Vuelca todos los datos del usuario (trainings, ejercicios propios e historial) a
     * un único JSON.
     *
     * Existe porque desinstalar la app borra SharedPreferences sin vuelta atrás, y el
     * backup automático de Android no es red de seguridad: el 29-ago-2026 una
     * reinstalación limpia subió su propio estado vacío a la nube seis segundos después
     * de instalarse y pisó la única copia buena que había. Un archivo que el usuario
     * controla es lo único que sobrevive a eso.
     */
    fun exportJson(): String = BackupJson.encode(
        BackupData(
            trainings = loadTrainings(),
            customExercises = loadCustomExercises(),
            sessions = loadSessions(),
            exerciseMedia = media.load(),
        ),
        exportedAt = System.currentTimeMillis(),
    )

    /**
     * Reemplaza todos los datos con los del respaldo. Devuelve el resumen de lo
     * importado, o null si el archivo no es un respaldo válido.
     *
     * Todo se parsea y valida ANTES de escribir: un archivo corrupto no debe dejar los
     * datos a medias, que sería peor que no importar.
     */
    fun importJson(json: String): ImportSummary? {
        val data = BackupJson.decode(json) ?: return null
        saveTrainings(data.trainings)
        saveCustomExercises(data.customExercises)
        saveSessions(data.sessions)
        // Solo instrucciones: los videos no viajan en el respaldo, se vuelven a descargar
        // del manifiesto en cuanto hagan falta.
        media.save(data.exerciseMedia)
        return ImportSummary(trainings = data.trainings.size, sessions = data.sessions.size)
    }

    private companion object {
        const val KEY_TRAININGS = "trainings_json"
        const val KEY_CUSTOM_EXERCISES = "custom_exercises_json"
        const val KEY_SESSIONS = "sessions_json"
        const val KEY_FRIKI_SEEDED = "friki_seeded"
        const val KEY_MASTER_V2 = "master_v2_seeded"
        const val KEY_MASTER_V3 = "master_v3_seeded"
        const val MAX_SESSIONS = 200
    }
}
