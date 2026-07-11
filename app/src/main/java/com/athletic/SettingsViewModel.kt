package com.athletic

import android.app.Application
import android.media.RingtoneManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.athletic.audio.AlarmPlayer
import com.athletic.data.SettingsStore
import com.athletic.model.AlarmConfig
import com.athletic.model.AlarmSound
import com.athletic.model.AppConfig

/**
 * Estado observable de los ajustes (general + player) respaldado por [SettingsStore].
 * Cada cambio persiste de inmediato para que el servicio del player lea la config
 * actualizada al reproducir. Mantiene un [AlarmPlayer] propio para las previews de
 * volumen/vibración de la pantalla de Ajustes ("lo que pruebas es lo que suena").
 */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SettingsStore(app)
    private val alarmPlayer = AlarmPlayer(app)

    var config by mutableStateOf(store.loadConfig())
        private set

    private fun update(newCfg: AppConfig) {
        config = newCfg
        store.saveConfig(newCfg)
    }

    // ---------- General ----------
    fun setAccent(accent: Long) = update(config.copy(general = config.general.copy(accent = accent)))
    fun setThemeMode(mode: Int) = update(config.copy(general = config.general.copy(themeMode = mode)))

    // ---------- Player ----------
    fun setPadPlayerClock(v: Boolean) =
        update(config.copy(athlete = config.athlete.copy(padPlayerClock = v)))

    // ---------- Alarma ----------
    private fun setAlarm(a: AlarmConfig) =
        update(config.copy(athlete = config.athlete.copy(alarm = a)))

    fun setSound(uri: String?, name: String?) =
        setAlarm(config.athlete.alarm.copy(soundUri = uri, soundName = name))
    fun setVolume(v: Float) = setAlarm(config.athlete.alarm.copy(volume = v))
    fun setVibrationEnabled(v: Boolean) = setAlarm(config.athlete.alarm.copy(vibrationEnabled = v))
    fun setVibrationPattern(i: Int) = setAlarm(config.athlete.alarm.copy(vibrationPattern = i))
    fun setIgnoreSilent(v: Boolean) = setAlarm(config.athlete.alarm.copy(ignoreSilent = v))
    fun setHeadsetMode(m: Int) = setAlarm(config.athlete.alarm.copy(headsetMode = m))

    // ---------- Previews ----------
    fun previewVolume() = alarmPlayer.previewVolume(config.athlete.alarm)
    fun previewVibration() = alarmPlayer.previewVibration(config.athlete.alarm.vibrationPattern)
    fun stopPreview() = alarmPlayer.stopPreview()

    fun previewTone(uri: String, volume: Float) = alarmPlayer.previewTone(uri, volume)

    /** Lista de tonos de alarma disponibles en el dispositivo. */
    fun loadAlarmSounds(): List<AlarmSound> {
        val ctx = getApplication<Application>()
        val result = mutableListOf<AlarmSound>()
        try {
            val rm = RingtoneManager(ctx).apply { setType(RingtoneManager.TYPE_NOTIFICATION) }
            val cursor = rm.cursor
            while (cursor.moveToNext()) {
                val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
                val uri = rm.getRingtoneUri(cursor.position)
                if (title != null && uri != null) {
                    result.add(AlarmSound(title, uri.toString()))
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    override fun onCleared() {
        alarmPlayer.stop()
        alarmPlayer.stopPreview()
    }
}
