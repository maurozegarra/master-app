package com.maurozegarra.master.data

import android.content.Context
import com.maurozegarra.master.model.AthleteBody
import com.maurozegarra.master.model.BodyEntry
import com.maurozegarra.master.model.BodyLog
import org.json.JSONObject

/**
 * Los pesajes (TD-169), en su propio archivo de preferencias: los propios, los de los
 * atletas que bajó el coach, y lo que hace falta para subirlos sin repetir.
 *
 * Aparte de [WorkoutStore] como la alarma: es un registro del cuerpo, no del entrenamiento,
 * y así su respaldo y su sincronización no tocan nada de lo otro.
 */
class BodyStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun mine(): List<BodyEntry> = BodyLog.decode(prefs.getString(KEY_MINE, "[]") ?: "[]")

    fun saveMine(list: List<BodyEntry>) {
        prefs.edit().putString(KEY_MINE, BodyLog.encode(list)).apply()
    }

    fun athletes(): List<AthleteBody> = BodyLog.decodeAthletes(prefs.getString(KEY_ATHLETES, "[]") ?: "[]")

    fun saveAthletes(list: List<AthleteBody>) {
        prefs.edit().putString(KEY_ATHLETES, BodyLog.encodeAthletes(list)).apply()
    }

    /** Día -> huella de lo que se subió de ese día. */
    fun ledger(): Map<String, Int> = runCatching {
        val o = JSONObject(prefs.getString(KEY_LEDGER, "{}") ?: "{}")
        o.keys().asSequence().associateWith { o.getInt(it) }
    }.getOrDefault(emptyMap())

    fun markUploaded(date: String, fingerprint: Int) {
        val o = JSONObject(ledger()).put(date, fingerprint)
        prefs.edit().putString(KEY_LEDGER, o.toString()).apply()
    }

    fun forgetUploaded(date: String) {
        val o = JSONObject(ledger()).apply { remove(date) }
        prefs.edit().putString(KEY_LEDGER, o.toString()).apply()
    }

    /** Días borrados aquí que hay que borrar también en el servidor. */
    fun pendingDeletes(): Set<String> = prefs.getStringSet(KEY_DELETES, emptySet()).orEmpty()

    fun queueDelete(date: String) {
        prefs.edit().putStringSet(KEY_DELETES, pendingDeletes() + date).apply()
    }

    fun deleted(date: String) {
        prefs.edit().putStringSet(KEY_DELETES, pendingDeletes() - date).apply()
    }

    /** Si ya se sembró la serie de los documentos. Una sola vez: lo que se edite después no vuelve. */
    var seeded: Boolean
        get() = prefs.getBoolean(KEY_SEEDED, false)
        set(v) = prefs.edit().putBoolean(KEY_SEEDED, v).apply()

    /** El sábado en que ya se recordó el pesaje (ISO), para no avisar cada quince minutos. */
    var remindedOn: String?
        get() = prefs.getString(KEY_REMINDED, null)
        set(v) = prefs.edit().putString(KEY_REMINDED, v).apply()

    /** Lo propio y lo de los atletas, tal como va al respaldo. */
    fun encodedMine(): String = BodyLog.encode(mine())

    fun encodedAthletes(): String = BodyLog.encodeAthletes(athletes())

    /** Lo del respaldo, al importarlo. Un campo vacío o roto no borra lo que hay. */
    fun restore(mine: String, athletes: String) {
        BodyLog.decode(mine).takeIf { it.isNotEmpty() }?.let(::saveMine)
        BodyLog.decodeAthletes(athletes).takeIf { it.isNotEmpty() }?.let(::saveAthletes)
    }

    private companion object {
        const val FILE = "body"
        const val KEY_MINE = "mine"
        const val KEY_ATHLETES = "athletes"
        const val KEY_LEDGER = "upload_ledger"
        const val KEY_DELETES = "pending_deletes"
        const val KEY_SEEDED = "seeded_v1"
        const val KEY_REMINDED = "reminded_on"
    }
}
