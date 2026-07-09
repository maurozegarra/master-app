package com.athletic.notify

import android.content.Context
import com.athletic.audio.AlarmPlayer
import com.athletic.data.SettingsStore

/**
 * "Cue" corto de alarma para las transiciones del player. Delega en el
 * [AlarmPlayer] compartido usando la alarma PROPIA del player
 * (`config.athlete.alarm`), de modo que "lo que pruebas es lo que suena". No
 * hace bucle: es un aviso breve.
 */
class WorkoutAlarm(private val context: Context) {

    private val player = AlarmPlayer(context)

    fun playCue() {
        val alarm = SettingsStore(context).loadConfig().athlete.alarm
        player.start(alarm, loop = false)
    }

    fun stop() = player.stop()
}
