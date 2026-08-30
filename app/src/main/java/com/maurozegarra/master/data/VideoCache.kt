package com.maurozegarra.master.data

import java.io.File

/**
 * Los vídeos en el dispositivo.
 *
 * Vive en `filesDir/videos/`, **excluido del respaldo** en `backup_rules.xml` y
 * `data_extraction_rules.xml`. La exclusión no es un detalle: son cien y pico megas
 * reconstruibles con una descarga, y el respaldo de Google tiene un tope de 25 MB por
 * app; pasarse descarta la copia entera, historial incluido, que es lo único de aquí que
 * no se recupera solo.
 *
 * `noBackupFilesDir` daría esa exclusión sin configurar nada, pero `FileProvider` no
 * sabe exponer ese directorio, y sin él el reproductor del sistema no podría abrir un
 * vídeo. Se prefiere declarar la exclusión a perder esa función.
 *
 * Dos carpetas separadas, y la separación es lo que hace segura la poda:
 *
 * - `repo/<exerciseId>.<rev>.mp4` — lo descargado del manifiesto. Al subir la revisión
 *   cambia el nombre, así que la versión nueva convive con la vieja hasta que se borra;
 *   nunca hay un archivo a medio reemplazar.
 * - `own/<exerciseId>.mp4` — el vídeo que el usuario asignó a mano, que **gana** sobre el
 *   del repositorio. En su propia carpeta, para que podar revisiones no pueda rozarlo.
 *
 * Recibe el directorio y no un `Context` para poder probarla de verdad contra una carpeta
 * temporal, en vez de comprobar solo cómo se arman los nombres.
 *
 * Los `exerciseId` se usan tal cual como nombre de archivo: son ids que genera el propio
 * app (`ex_cat_cow`, `custom_1724…`), sin separadores ni espacios.
 */
class VideoCache(private val dir: File) {

    private val repoDir get() = File(dir, "repo")
    private val ownDir get() = File(dir, "own")

    /** Dónde va esa revisión, esté descargada o no. */
    fun repoFile(exerciseId: String, rev: Int): File = File(repoDir, "$exerciseId.$rev.mp4")

    /** Dónde va el vídeo propio del usuario, exista o no. */
    fun ownFile(exerciseId: String): File = File(ownDir, "$exerciseId.mp4")

    /**
     * El vídeo listo para reproducir, o null si no hay ninguno.
     *
     * El propio gana: si el usuario se molestó en asignar el suyo, no queremos que una
     * publicación en el manifiesto se lo pise.
     */
    fun resolve(exerciseId: String, rev: Int?): File? =
        existing(ownFile(exerciseId)) ?: rev?.let { existing(repoFile(exerciseId, it)) }

    /** true si esa revisión ya está descargada y completa. */
    fun hasRepoVideo(exerciseId: String, rev: Int): Boolean = existing(repoFile(exerciseId, rev)) != null

    fun hasOwnVideo(exerciseId: String): Boolean = existing(ownFile(exerciseId)) != null

    fun deleteOwn(exerciseId: String): Boolean = ownFile(exerciseId).delete()

    /** Ejercicios con vídeo propio. Tienen estado aunque no salgan en el manifiesto. */
    fun ownExerciseIds(): List<String> = filesIn(ownDir).map { it.name.removeSuffix(".mp4") }

    /**
     * Borra las revisiones viejas de un ejercicio, dejando solo [keepRev].
     *
     * Se llama después de descargar la nueva, no antes: si se borra primero y la descarga
     * falla, el usuario se queda sin el vídeo que ya tenía.
     */
    fun dropOtherRevisions(exerciseId: String, keepRev: Int) {
        val keep = repoFile(exerciseId, keepRev).name
        filesIn(repoDir)
            .filter { it.name.startsWith("$exerciseId.") && it.name != keep }
            .forEach { it.delete() }
    }

    private fun filesIn(d: File): List<File> = d.listFiles()?.toList() ?: emptyList()

    /** Bytes que ocupan los vídeos descargados, para poder enseñarlo en Settings. */
    fun bytesUsed(): Long = (filesIn(repoDir) + filesIn(ownDir)).sumOf { it.length() }

    /** Borra lo descargado del manifiesto. Los vídeos propios no se tocan: no se recuperan solos. */
    fun clearDownloaded() {
        repoDir.deleteRecursively()
    }

    // Un archivo de cero bytes es basura de una descarga que no llego a nada: tratarlo
    // como valido dejaria al player intentando reproducir la nada.
    private fun existing(file: File): File? = file.takeIf { it.isFile && it.length() > 0L }
}
