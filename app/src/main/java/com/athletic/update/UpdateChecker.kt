package com.athletic.update

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val changelog: String,
    val minVersionCode: Int,
)

object UpdateChecker {

    private const val UPDATE_URL =
        "https://raw.githubusercontent.com/maurozegarra/master-app/main/update.json"

    fun getCurrentVersionCode(context: Context): Int {
        val pm = context.packageManager
        val info = pm.getPackageInfo(context.packageName, 0)
        return info.versionCode
    }

    fun getCurrentVersionName(context: Context): String {
        val pm = context.packageManager
        val info = pm.getPackageInfo(context.packageName, 0)
        return info.versionName ?: ""
    }

    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val conn = (URL("$UPDATE_URL?t=${System.currentTimeMillis()}").openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 10000
                useCaches = false
                setRequestProperty("Cache-Control", "no-cache")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            Log.d("UpdateChecker", "Response: $body")
            val json = JSONObject(body)
            val info = UpdateInfo(
                versionCode = json.getInt("versionCode"),
                versionName = json.getString("versionName"),
                apkUrl = json.getString("apkUrl"),
                changelog = json.optString("changelog", ""),
                minVersionCode = json.optInt("minVersionCode", 1),
            )
            val current = getCurrentVersionCode(context)
            Log.d("UpdateChecker", "remote=${info.versionCode} current=$current")
            if (info.versionCode > current) info else null
        } catch (e: Exception) {
            Log.e("UpdateChecker", "Error checking update", e)
            null
        }
    }

    fun isForceUpdate(info: UpdateInfo, context: Context): Boolean {
        return getCurrentVersionCode(context) < info.minVersionCode
    }
}
