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

    /** Marca de migracion: si ya se limpiaron los ejercicios propios sueltos (TD-087). */
    fun isLumbarCleanupDone(): Boolean = prefs.getBoolean(KEY_LUMBAR_CLEANUP, false)
    fun setLumbarCleanupDone() { prefs.edit().putBoolean(KEY_LUMBAR_CLEANUP, true).apply() }

    /**
     * Marca de migracion: si ya se anoto la sesion del 13-sep-2026 (TD-090).
     *
     * La clave lleva `_v2` porque la v1 se dio por hecha sin anotar nada: se topo con la
     * corrida de prueba de cuatro minutos de ese mismo dia y se callo. Cambiar la clave es
     * como se repite una siembra que quedo mal, igual que master_v2 y master_v3.
     */
    fun isFirstSessionSeeded(): Boolean = prefs.getBoolean(KEY_FIRST_SESSION, false)
    fun setFirstSessionSeeded() { prefs.edit().putBoolean(KEY_FIRST_SESSION, true).apply() }

    /** Marca de migracion: si ya se reescribieron las indicaciones de la caminata (TD-091). */
    fun isWalkNoteUpdated(): Boolean = prefs.getBoolean(KEY_WALK_NOTE_V2, false)
    fun setWalkNoteUpdated() { prefs.edit().putBoolean(KEY_WALK_NOTE_V2, true).apply() }

    /** Marca de migracion: si la sesion reconstruida ya quedo marcada como tal (TD-101). */
    fun isFirstSessionMarked(): Boolean = prefs.getBoolean(KEY_FIRST_SESSION_MARKED, false)
    fun setFirstSessionMarked() { prefs.edit().putBoolean(KEY_FIRST_SESSION_MARKED, true).apply() }

    /** Marca de migracion: si las sesiones lumbares viejas ya se reordenaron (TD-102). */
    fun isLumbarSessionsReordered(): Boolean = prefs.getBoolean(KEY_SESSIONS_REORDERED, false)
    fun setLumbarSessionsReordered() { prefs.edit().putBoolean(KEY_SESSIONS_REORDERED, true).apply() }

    /**
     * Revision de la rutina lumbar ya aplicada en este dispositivo (TD-103).
     *
     * Sustituye a las tres marcas que habia -lumbar_seeded, lumbar_bad_day_seeded y
     * hip_glute_loaded-: un numero que avanza dice lo mismo que ellas y ademas cubre todos
     * los cambios que vengan, sin una marca nueva por cada uno. 0 = ninguna todavia.
     */
    fun lumbarRevision(): Int = prefs.getInt(KEY_LUMBAR_REVISION, 0)
    fun setLumbarRevision(rev: Int) { prefs.edit().putInt(KEY_LUMBAR_REVISION, rev).apply() }

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
        const val KEY_LUMBAR_CLEANUP = "lumbar_cleanup_done"
        const val KEY_FIRST_SESSION = "lumbar_first_session_seeded_v2"
        const val KEY_WALK_NOTE_V2 = "walk_instructions_v2"
        const val KEY_LUMBAR_REVISION = "lumbar_revision"
        const val KEY_FIRST_SESSION_MARKED = "first_session_marked"
        const val KEY_SESSIONS_REORDERED = "lumbar_sessions_reordered"
        const val MAX_SESSIONS = 200
    }
}
