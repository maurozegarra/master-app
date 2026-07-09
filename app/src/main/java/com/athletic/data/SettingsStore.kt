package com.athletic.data

import android.content.Context
import com.athletic.model.AlarmConfig
import com.athletic.model.AppConfig
import com.athletic.model.AthleteConfig
import com.athletic.model.GeneralConfig
import org.json.JSONObject

/**
 * Persistencia MÍNIMA de ajustes de Athletic (general + player) con
 * SharedPreferences + JSON. Es la base para la pantalla de Ajustes de la Fase 5;
 * por ahora cubre lo que necesitan el player y su alarma.
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun loadConfig(): AppConfig {
        val raw = prefs.getString(KEY_CONFIG, null) ?: return AppConfig()
        return try {
            configFromJson(JSONObject(raw))
        } catch (_: Exception) {
            AppConfig()
        }
    }

    fun saveConfig(cfg: AppConfig) {
        prefs.edit().putString(KEY_CONFIG, configToJson(cfg).toString()).apply()
    }

    private fun configToJson(cfg: AppConfig): JSONObject = JSONObject()
        .put(
            "general",
            JSONObject()
                .put("accent", cfg.general.accent)
                .put("themeMode", cfg.general.themeMode),
        )
        .put(
            "athlete",
            JSONObject()
                .put("padPlayerClock", cfg.athlete.padPlayerClock)
                .put("alarm", alarmToJson(cfg.athlete.alarm)),
        )

    private fun configFromJson(o: JSONObject): AppConfig {
        val g = o.optJSONObject("general")
        val a = o.optJSONObject("athlete")
        val def = AppConfig()
        return AppConfig(
            general = GeneralConfig(
                accent = g?.optLong("accent", def.general.accent) ?: def.general.accent,
                themeMode = g?.optInt("themeMode", def.general.themeMode) ?: def.general.themeMode,
            ),
            athlete = AthleteConfig(
                padPlayerClock = a?.optBoolean("padPlayerClock", def.athlete.padPlayerClock)
                    ?: def.athlete.padPlayerClock,
                alarm = alarmFromJson(a?.optJSONObject("alarm")),
            ),
        )
    }

    private fun alarmToJson(c: AlarmConfig): JSONObject = JSONObject()
        .put("soundUri", c.soundUri)
        .put("soundName", c.soundName)
        .put("volume", c.volume.toDouble())
        .put("vibrationEnabled", c.vibrationEnabled)
        .put("vibrationPattern", c.vibrationPattern)
        .put("ignoreSilent", c.ignoreSilent)
        .put("headsetMode", c.headsetMode)

    private fun alarmFromJson(o: JSONObject?): AlarmConfig {
        if (o == null) return AlarmConfig()
        val def = AlarmConfig()
        return AlarmConfig(
            soundUri = if (o.isNull("soundUri")) null else o.optString("soundUri"),
            soundName = if (o.isNull("soundName")) null else o.optString("soundName"),
            volume = o.optDouble("volume", def.volume.toDouble()).toFloat(),
            vibrationEnabled = o.optBoolean("vibrationEnabled", def.vibrationEnabled),
            vibrationPattern = o.optInt("vibrationPattern", def.vibrationPattern),
            ignoreSilent = o.optBoolean("ignoreSilent", def.ignoreSilent),
            headsetMode = o.optInt("headsetMode", def.headsetMode),
        )
    }

    private companion object {
        const val KEY_CONFIG = "app_config_json"
    }
}
