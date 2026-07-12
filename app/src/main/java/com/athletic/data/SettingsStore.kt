package com.athletic.data

import android.content.Context
import com.athletic.model.AppConfig
import com.athletic.model.AthleteConfig
import com.athletic.model.GeneralConfig
import org.json.JSONObject

/**
 * Persistencia de ajustes de Athletic (general + player) con
 * SharedPreferences + JSON.
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
                .put("padPlayerClock", cfg.athlete.padPlayerClock),
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
            ),
        )
    }

    private companion object {
        const val KEY_CONFIG = "app_config_json"
    }
}
