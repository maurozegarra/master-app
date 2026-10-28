package com.maurozegarra.master.timer

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Lo que guarda el timer (TD-195), en su propio archivo, como el agua y la alarma.
 *
 * [runs] avisa a la franja del tablero de cada cambio: los timers también cambian desde fuera
 * de la pantalla (la notificación, la alarma que termina), y la franja tiene que enterarse sin
 * esperar a volver a abrirla.
 */
class TimerStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun runs(): List<TimerRun> = TimerPlan.decodeRuns(prefs.getString(KEY_RUNS, "[]") ?: "[]")

    fun saveRuns(list: List<TimerRun>) {
        prefs.edit().putString(KEY_RUNS, TimerPlan.encodeRuns(list)).apply()
        state.value = list
    }

    fun recents(): List<RecentTimer> = TimerPlan.decodeRecents(prefs.getString(KEY_RECENTS, "[]") ?: "[]")

    fun saveRecents(list: List<RecentTimer>) {
        prefs.edit().putString(KEY_RECENTS, TimerPlan.encodeRecents(list)).apply()
    }

    fun alarmRecents(): List<RecentAlarm> = TimerPlan.decodeAlarmRecents(prefs.getString(KEY_ALARM_RECENTS, "[]") ?: "[]")

    fun saveAlarmRecents(list: List<RecentAlarm>) {
        prefs.edit().putString(KEY_ALARM_RECENTS, TimerPlan.encodeAlarmRecents(list)).apply()
    }

    /** La pestaña del + con la que se abre: la última que se usó (TD-200). */
    var alarmTab: Boolean
        get() = prefs.getBoolean(KEY_ALARM_TAB, false)
        set(v) = prefs.edit().putBoolean(KEY_ALARM_TAB, v).apply()

    companion object {
        private const val FILE = "timers"
        private const val KEY_RUNS = "runs"
        private const val KEY_RECENTS = "recents"
        private const val KEY_ALARM_RECENTS = "alarm_recents"
        private const val KEY_ALARM_TAB = "alarm_tab"

        private val state = MutableStateFlow<List<TimerRun>?>(null)

        /** Los timers de ahora, para la franja. Se carga del disco la primera vez. */
        fun runs(context: Context): StateFlow<List<TimerRun>?> {
            if (state.value == null) state.value = TimerStore(context).runs()
            return state
        }
    }
}
