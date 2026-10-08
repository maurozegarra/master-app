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

    /**
     * Las alarmas (TD-175). Sin guardar todavía, se leen del horario de antes -día -> hora,
     * con su interruptor general- para que el teléfono que ya la usaba siga igual. Sin nada
     * de nada, las dos por defecto APAGADAS: el app llega a otros teléfonos, y ninguno debe
     * empezar a sonar solo por actualizar.
     */
    fun schedule(): MorningSchedule = alarmsOnly().copy(holidays = holidays())

    /** Los feriados vigentes (7-oct): los del Perú, menos los quitados, más los agregados. */
    fun holidays(today: java.time.LocalDate = java.time.LocalDate.now()): Set<java.time.LocalDate> =
        Holidays.of(today.year, dates(KEY_HOL_ADDED), dates(KEY_HOL_REMOVED))

    /** Marca o desmarca [day] como feriado. */
    fun setHoliday(day: java.time.LocalDate, on: Boolean) {
        val nacional = Holidays.name(day) != null
        val added = dates(KEY_HOL_ADDED).toMutableSet()
        val removed = dates(KEY_HOL_REMOVED).toMutableSet()
        if (on) {
            removed -= day
            if (!nacional) added += day
        } else {
            added -= day
            if (nacional) removed += day
        }
        prefs.edit()
            .putStringSet(KEY_HOL_ADDED, added.map { it.toString() }.toSet())
            .putStringSet(KEY_HOL_REMOVED, removed.map { it.toString() }.toSet())
            .apply()
    }

    private fun dates(key: String): Set<java.time.LocalDate> =
        (prefs.getStringSet(key, emptySet()) ?: emptySet())
            .mapNotNull { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }.toSet()

    private fun alarmsOnly(): MorningSchedule {
        prefs.getString(KEY_ALARMS, null)?.let { raw ->
            runCatching { return decodeAlarms(raw) }
        }
        val antes = prefs.getBoolean(KEY_ENABLED, false)
        val raw = prefs.getString(KEY_SCHEDULE, null) ?: return MorningSchedule.default(antes)
        return runCatching {
            val o = JSONObject(raw)
            MorningSchedule.fromDays(
                DayOfWeek.entries.associateWith { d ->
                    if (!o.has(d.name) || o.isNull(d.name)) null else LocalTime.parse(o.getString(d.name))
                },
                antes,
            )
        }.getOrDefault(MorningSchedule.default(antes))
    }

    fun saveSchedule(s: MorningSchedule) {
        prefs.edit().putString(KEY_ALARMS, encodeAlarms(s)).apply()
    }

    /** Hasta cuándo está pospuesta, o 0. Mientras lo esté, manda sobre el horario. */
    var snoozedUntil: Long
        get() = prefs.getLong(KEY_SNOOZE, 0L)
        set(v) = prefs.edit().putLong(KEY_SNOOZE, v).apply()

    /** Un día que no suena, puesto con "Skip tomorrow" (ISO), o null. */
    var skipDate: java.time.LocalDate?
        get() = prefs.getString(KEY_SKIP, null)?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
        set(v) = prefs.edit().putString(KEY_SKIP, v?.toString()).apply()

    /** El aviso para ir a dormir (TD-160). Apagado hasta que se enciende. */
    fun bedtime(): BedtimeConfig = BedtimeConfig(
        enabled = prefs.getBoolean(KEY_BED_ON, false),
        sleepMin = prefs.getInt(KEY_BED_SLEEP, 450),
        leadMin = prefs.getInt(KEY_BED_LEAD, 30),
    )

    fun saveBedtime(c: BedtimeConfig) {
        prefs.edit().putBoolean(KEY_BED_ON, c.enabled).putInt(KEY_BED_SLEEP, c.sleepMin).putInt(KEY_BED_LEAD, c.leadMin).apply()
    }

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
        private const val KEY_ALARMS = "alarms"
        private const val KEY_BED_ON = "bed_enabled"
        private const val KEY_BED_SLEEP = "bed_sleep_min"
        private const val KEY_BED_LEAD = "bed_lead_min"
        private const val KEY_HOL_ADDED = "holidays_added"
        private const val KEY_HOL_REMOVED = "holidays_removed"

        fun encodeAlarms(s: MorningSchedule): String {
            val a = JSONArray()
            s.alarms.forEach { al ->
                a.put(
                    JSONObject()
                        .put("id", al.id)
                        .put("time", al.time.toString())
                        .put("days", JSONArray(al.days.sortedBy { it.value }.map { it.name }))
                        .put("enabled", al.enabled),
                )
            }
            return a.toString()
        }

        fun decodeAlarms(json: String): MorningSchedule {
            val a = JSONArray(json)
            return MorningSchedule(
                (0 until a.length()).map { i ->
                    val o = a.getJSONObject(i)
                    val d = o.getJSONArray("days")
                    MorningAlarmSpec(
                        id = o.getLong("id"),
                        time = LocalTime.parse(o.getString("time")),
                        days = (0 until d.length()).mapNotNull { k -> runCatching { DayOfWeek.valueOf(d.getString(k)) }.getOrNull() }.toSet(),
                        enabled = o.optBoolean("enabled", true),
                    )
                },
            )
        }

        fun encode(list: List<MorningEntry>): String {
            val a = JSONArray()
            list.forEach { e ->
                val o = JSONObject().put("date", e.date)
                e.painOnWaking?.let { o.put("painOnWaking", it) }
                e.answeredAt?.let { o.put("answeredAt", it) }
                e.easedAt?.let { o.put("easedAt", it) }
                e.fadeMinutes?.let { o.put("fadeMinutes", it) }
                e.bedAt?.let { o.put("bedAt", it) }
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
                    bedAt = if (o.has("bedAt")) o.optLong("bedAt") else null,
                )
            }
        }.getOrDefault(emptyList())
    }
}
