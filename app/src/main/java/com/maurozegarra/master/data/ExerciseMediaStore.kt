package com.maurozegarra.master.data

import android.content.Context
import android.net.Uri
import com.maurozegarra.master.model.ExerciseMedia
import com.maurozegarra.master.model.ExerciseMediaJson

/**
 * Vídeos e instrucciones por ejercicio del catálogo.
 *
 * El mapa vive en SharedPreferences como el resto del estado, pero los vídeos van a
 * `Movies/MASTER/` vía [SharedFiles], donde sobreviven a una desinstalación.
 * Por eso el mapa guarda solo el **nombre** del archivo: al reinstalar e importar el
 * respaldo, los vídeos siguen ahí y se vuelven a encontrar por nombre.
 */
class ExerciseMediaStore(context: Context, private val files: SharedFiles) {

    private val prefs = context.applicationContext
        .getSharedPreferences("master", Context.MODE_PRIVATE)

    fun load(): Map<String, ExerciseMedia> {
        val raw = prefs.getString(KEY, null) ?: return emptyMap()
        return ExerciseMediaJson.decode(raw)
    }

    fun save(media: Map<String, ExerciseMedia>) {
        prefs.edit().putString(KEY, ExerciseMediaJson.encode(media)).apply()
    }

    /** Uri reproducible del vídeo de un ejercicio, o null si no tiene o falta el archivo. */
    fun videoUri(media: ExerciseMedia?): Uri? {
        val name = media?.videoFile?.takeIf { it.isNotBlank() } ?: return null
        return files.findVideo(name)
    }

    /**
     * Copia el vídeo elegido por el usuario a `Movies/MASTER/` y devuelve el
     * nombre con el que quedó, o null si falló.
     *
     * El nombre se deriva del exerciseId y no del archivo original: así reemplazar el
     * vídeo de un ejercicio sobrescribe el anterior en vez de acumular huérfanos. Se
     * guarda el nombre que devuelve [SharedFiles.writeVideo], que puede diferir del
     * pedido si MediaProvider tuvo que renombrar por colisión.
     */
    fun importVideo(exerciseId: String, source: () -> java.io.InputStream?): String? =
        files.writeVideo("$exerciseId.mp4", source)

    fun deleteVideo(media: ExerciseMedia?) {
        val name = media?.videoFile?.takeIf { it.isNotBlank() } ?: return
        files.deleteVideo(name)
    }

    val canStoreVideos: Boolean get() = files.isAvailable

    private companion object {
        const val KEY = "exercise_media_json"
    }
}
