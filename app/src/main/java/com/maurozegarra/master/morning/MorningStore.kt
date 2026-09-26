package com.maurozegarra.master.morning

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Lo que guarda la alarma, en su PROPIO archivo de preferencias (TD-151).
 *
 * Aparte de `WorkoutStore` para que el módulo se pueda sacar entero: si en la semana de
 * prueba estorba, se lleva su archivo y MASTER no pierde nada suyo.
 */
class MorningStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(v) = prefs.edit().putBoolean(KEY_ENABLED, v).apply()

    fun schedule(): MorningSchedule {
        val raw = prefs.getString(KEY_SCHEDULE, null) ?: return MorningSchedule.DEFAULT
        return runCatching {
            val o = JSONObject(raw)
            MorningSchedule(
                DayOfWeek.entries.associateWith { d ->
                    if (!o.has(d.name) || o.isNull(d.name)) null else LocalTime.parse(o.getString(d.name))
                },
            )
        }.getOrDefault(MorningSchedule.DEFAULT)
    }

    fun saveSchedule(s: MorningSchedule) {
        val o = JSONObject()
        DayOfWeek.entries.forEach { d -> o.put(d.name, s.at(d)?.toString() ?: JSONObject.NULL) }
        prefs.edit().putString(KEY_SCHEDULE, o.toString()).apply()
    }

    /** Hasta cuándo está pospuesta, o 0. Mientras lo esté, manda sobre el horario. */
    var snoozedUntil: Long
        get() = prefs.getLong(KEY_SNOOZE, 0L)
        set(v) = prefs.edit().putLong(KEY_SNOOZE, v).apply()

    /** Un día que no suena, puesto con "Skip tomorrow" (ISO), o null. */
    var skipDate: java.time.LocalDate?
        get() = prefs.getString(KEY_SKIP, null)?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
        set(v) = prefs.edit().putString(KEY_SKIP, v?.toString()).apply()

    fun entries(): List<MorningEntry> = decode(prefs.getString(KEY_ENTRIES, "[]") ?: "[]")

    fun saveEntries(list: List<MorningEntry>) {
        prefs.edit().putString(KEY_ENTRIES, encode(list)).apply()
    }

    /** Las mañanas tal como van al respaldo. */
    fun encoded(): String = encode(entries())

    /** Las del respaldo, al importarlo. Un campo roto o ausente no borra las que hay. */
    fun restore(json: String) {
        val leidas = decode(json)
        if (leidas.isNotEmpty()) saveEntries(leidas)
    }

    companion object {
        private const val FILE = "morning"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_SCHEDULE = "schedule"
        private const val KEY_SNOOZE = "snoozed_until"
        private const val KEY_ENTRIES = "entries"
        private const val KEY_SKIP = "skip_date"

        fun encode(list: List<MorningEntry>): String {
            val a = JSONArray()
            list.forEach { e ->
                val o = JSONObject().put("date", e.date)
                e.painOnWaking?.let { o.put("painOnWaking", it) }
                e.answeredAt?.let { o.put("answeredAt", it) }
                e.easedAt?.let { o.put("easedAt", it) }
                e.fadeMinutes?.let { o.put("fadeMinutes", it) }
                a.put(o)
            }
            return a.toString()
        }

        fun decode(json: String): List<MorningEntry> = runCatching {
            val a = JSONArray(json)
            (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                MorningEntry(
                    date = o.optString("date").takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                    painOnWaking = if (o.has("painOnWaking")) o.optInt("painOnWaking") else null,
                    answeredAt = if (o.has("answeredAt")) o.optLong("answeredAt") else null,
                    easedAt = if (o.has("easedAt")) o.optLong("easedAt") else null,
                    // Solo cuenta cuando no hay horas: si las hay, fadeMinutes las recalcula.
                    fadeMin = if (o.has("fadeMinutes") && !(o.has("answeredAt") && o.has("easedAt"))) o.optInt("fadeMinutes") else null,
                )
            }
        }.getOrDefault(emptyList())
    }
}
