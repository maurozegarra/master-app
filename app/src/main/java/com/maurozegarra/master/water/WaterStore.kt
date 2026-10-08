package com.maurozegarra.master.water

import android.content.Context

/**
 * Lo que guarda el agua (TD-190), en su propio archivo de preferencias, como la alarma: si
 * el módulo se saca, se lleva su archivo y MASTER no pierde nada suyo.
 */
class WaterStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // unique: repara las tomas repetidas en el mismo instante que ya estén guardadas (8-oct).
    fun logs(): List<WaterLog> = WaterPlan.unique(WaterPlan.decode(prefs.getString(KEY_LOGS, "[]") ?: "[]"))

    fun saveLogs(list: List<WaterLog>) {
        prefs.edit().putString(KEY_LOGS, WaterPlan.encode(list)).apply()
    }

    /**
     * Carga una vez las tomas del 4-oct de su app de antes ([WaterPlan.seed4Oct]). No pisa
     * nada: una que ya este anotada en el mismo minuto se deja.
     */
    fun seedOnce(zone: java.time.ZoneId) {
        if (prefs.getBoolean(KEY_SEEDED, false)) return
        // Solo el mismo 4-oct: el app llega a otros telefonos (el de NIKO) y sus tomas no son
        // de nadie mas. Otro dia, se marca y no se carga nada.
        if (java.time.LocalDate.now(zone) != java.time.LocalDate.of(2026, 10, 4)) {
            prefs.edit().putBoolean(KEY_SEEDED, true).apply()
            return
        }
        val hay = logs()
        val nuevas = WaterPlan.seed4Oct(zone).filter { s -> hay.none { kotlin.math.abs(it.at - s.at) < 60_000 } }
        saveLogs((hay + nuevas).sortedBy { it.at })
        prefs.edit().putBoolean(KEY_SEEDED, true).apply()
    }

    /** Las tomas tal como van al respaldo. */
    fun encoded(): String = WaterPlan.encode(logs())

    /** Las del respaldo, al importarlo. Vacío o roto no borra lo que hay. */
    fun restore(json: String) {
        WaterPlan.decode(json).takeIf { it.isNotEmpty() }?.let(::saveLogs)
    }

    private companion object {
        const val FILE = "water"
        const val KEY_LOGS = "logs"
        const val KEY_SEEDED = "seeded_4oct"
    }
}
