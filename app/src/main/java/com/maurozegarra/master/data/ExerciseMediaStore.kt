package com.maurozegarra.master.data

import android.content.Context
import com.maurozegarra.master.model.ExerciseMedia
import com.maurozegarra.master.model.ExerciseMediaJson

/**
 * Instrucciones por ejercicio del catálogo, persistidas como JSON en SharedPreferences
 * igual que el resto del estado.
 *
 * Los vídeos **no** pasan por aquí: son archivos en el directorio privado del app y los
 * gestiona [VideoCache]. Guardar además su nombre creaba dos fuentes de verdad —el mapa
 * decía que había vídeo y el archivo no aparecía— que es exactamente el fallo que costó
 * una sesión entera de diagnóstico.
 */
class ExerciseMediaStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("master", Context.MODE_PRIVATE)

    fun load(): Map<String, ExerciseMedia> {
        val raw = prefs.getString(KEY, null) ?: return emptyMap()
        return ExerciseMediaJson.decode(raw)
    }

    fun save(media: Map<String, ExerciseMedia>) {
        prefs.edit().putString(KEY, ExerciseMediaJson.encode(media)).apply()
    }

    private companion object {
        const val KEY = "exercise_media_json"
    }
}
