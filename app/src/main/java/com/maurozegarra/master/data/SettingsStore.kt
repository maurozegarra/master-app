package com.maurozegarra.master.data

import android.content.Context
import com.maurozegarra.master.model.AppConfig
import com.maurozegarra.master.model.DownloadsConfig
import com.maurozegarra.master.model.MasterConfig
import com.maurozegarra.master.model.GeneralConfig
import org.json.JSONObject

/**
 * Persistencia de ajustes de MASTER (general + player) con
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
            "masterConfig",
            JSONObject()
                .put("padPlayerClock", cfg.masterConfig.padPlayerClock)
                .put("beepVolume", cfg.masterConfig.beepVolume),
        )
        .put(
            "downloads",
            JSONObject()
                .put("overMobileData", cfg.downloads.overMobileData),
        )

    private fun configFromJson(o: JSONObject): AppConfig {
        val g = o.optJSONObject("general")
        val a = o.optJSONObject("masterConfig") ?: o.optJSONObject("athlete")
        val d = o.optJSONObject("downloads")
        val def = AppConfig()
        return AppConfig(
            general = GeneralConfig(
                accent = g?.optLong("accent", def.general.accent) ?: def.general.accent,
                themeMode = g?.optInt("themeMode", def.general.themeMode) ?: def.general.themeMode,
            ),
            masterConfig = MasterConfig(
                padPlayerClock = a?.optBoolean("padPlayerClock", def.masterConfig.padPlayerClock)
                    ?: def.masterConfig.padPlayerClock,
                // Un ajuste guardado antes de que existiera este campo cae a 100: sonaban así.
                beepVolume = (a?.optInt("beepVolume", def.masterConfig.beepVolume) ?: def.masterConfig.beepVolume)
                    .coerceIn(0, 100),
            ),
            downloads = DownloadsConfig(
                overMobileData = d?.optBoolean("overMobileData", def.downloads.overMobileData)
                    ?: def.downloads.overMobileData,
            ),
        )
    }

    private companion object {
        const val KEY_CONFIG = "app_config_json"
    }
}
