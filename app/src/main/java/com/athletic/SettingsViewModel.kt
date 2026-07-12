package com.athletic

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.athletic.data.SettingsStore
import com.athletic.model.AppConfig

/**
 * Estado observable de los ajustes (general + player) respaldado por [SettingsStore].
 * Cada cambio persiste de inmediato para que el servicio del player lea la config
 * actualizada al reproducir.
 */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SettingsStore(app)

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
}
