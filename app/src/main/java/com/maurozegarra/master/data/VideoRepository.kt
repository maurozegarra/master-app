package com.maurozegarra.master.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import com.maurozegarra.master.model.VideoManifest
import com.maurozegarra.master.model.VideoManifestJson
import com.maurozegarra.master.net.Downloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** En qué punto está el vídeo de un ejercicio. */
sealed interface VideoState {
    /** No hay vídeo para ese ejercicio: ni publicado ni propio. */
    data object None : VideoState

    /** Hay vídeo, falta traerlo. */
    data object Pending : VideoState

    data class Downloading(val progress: Float) : VideoState

    data class Ready(val file: File) : VideoState
}

/**
 * De dónde salen los vídeos: manifiesto publicado, caché en disco y descarga bajo demanda.
 *
 * El app trae los trainings, no los vídeos. Cada uno se descarga la primera vez que hace
 * falta —al revisar el training antes de hacerlo, o durante la corrida— y a partir de ahí
 * está en el dispositivo.
 *
 * **Nunca bloquea un entrenamiento.** Si el vídeo no está, el player enseña el emoji de
 * siempre y sigue. Un fallo de red no puede parar a alguien que está entrenando, así que
 * aquí no hay estado de error: lo que falla vuelve a [VideoState.Pending] y se reintenta
 * la próxima vez que alguien lo pida.
 *
 * Descarga **una cada vez**: bajar los siete vídeos de un workout a la vez satura el
 * enlace justo cuando hace falta el primero.
 */
class VideoRepository(
    context: Context,
    private val cache: VideoCache,
    private val settings: SettingsStore,
) {

    private val appCtx = context.applicationContext
    private val prefs = appCtx.getSharedPreferences("master", Context.MODE_PRIVATE)

    // Propio y no el del ViewModel: una descarga empezada en el preview tiene que seguir
    // viva al entrar al player y al salir de el.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val states = mutableStateMapOf<String, VideoState>()
    private val queue = ArrayDeque<String>()
    private var downloading = false

    private var manifest: VideoManifest? = null

    init {
        // El manifiesto guardado permite arrancar sin red sabiendo que hay descargado y
        // de donde vino; luego se refresca por detras.
        manifest = prefs.getString(KEY_MANIFEST, null)?.let { VideoManifestJson.decode(it) }
        rebuildStates()
        scope.launch { refreshManifest() }
    }

    /**
     * Estado observable por Compose.
     *
     * Lectura de un mapa y nada más: el player lo consulta en cada recomposición, varias
     * veces por segundo mientras corre el reloj, así que no puede tocar el disco. El mapa
     * se recalcula en los puntos donde algo cambia de verdad.
     */
    fun stateOf(exerciseId: String): VideoState = states[exerciseId] ?: VideoState.None

    /** El archivo listo para reproducir, o null. */
    fun fileFor(exerciseId: String): File? = (stateOf(exerciseId) as? VideoState.Ready)?.file

    /**
     * Pide el vídeo de un ejercicio. No hace nada si ya está o si no hay ninguno publicado.
     *
     * [urgent] lo pone al principio de la cola: es para el ejercicio que viene ahora, que
     * no puede esperar detrás de los seis que encoló el preview.
     */
    fun request(exerciseId: String, urgent: Boolean = false) {
        val state = stateOf(exerciseId)
        if (state is VideoState.Ready || state is VideoState.None) return
        if (queue.contains(exerciseId)) {
            if (urgent) {
                queue.remove(exerciseId)
                queue.addFirst(exerciseId)
            }
            return
        }
        if (urgent) queue.addFirst(exerciseId) else queue.addLast(exerciseId)
        pump()
    }

    fun requestAll(exerciseIds: List<String>) = exerciseIds.forEach { request(it) }

    /** Vuelve a leer el manifiesto publicado. Si no hay red, se queda con el guardado. */
    suspend fun refreshManifest() {
        val body = withContext(Dispatchers.IO) {
            runCatching { Downloader.fetchText(MANIFEST_URL) }.getOrNull()
        } ?: return
        val fresh = VideoManifestJson.decode(body) ?: return
        prefs.edit().putString(KEY_MANIFEST, body).apply()
        manifest = fresh
        // Un vídeo ya descargado puede haber subido de revisión: se reevalúa todo en vez
        // de confiar en el estado anterior.
        rebuildStates()
    }

    /** Recalcula el estado de todo lo que puede tener vídeo. Toca disco, así que se llama
     *  en los pocos momentos en que algo cambia, no al pintar. */
    fun rebuildStates() {
        val ids = manifest?.videos?.keys.orEmpty() + cache.ownExerciseIds()
        states.clear()
        ids.forEach { states[it] = resolveState(it) }
    }

    /** Tras asignar o quitar un vídeo propio, que no pasa por la cola de descargas. */
    fun refreshState(exerciseId: String) {
        states[exerciseId] = resolveState(exerciseId)
    }

    private fun resolveState(exerciseId: String): VideoState {
        val rev = manifest?.entry(exerciseId)?.rev
        val file = cache.resolve(exerciseId, rev)
        return when {
            file != null -> VideoState.Ready(file)
            rev != null -> VideoState.Pending
            else -> VideoState.None
        }
    }

    /**
     * Si toca descargar ahora mismo.
     *
     * Se mira lo **medido**, no si es wifi: un móvil compartiendo conexión también cuesta
     * dinero, y una wifi de hotel no es gratis por ser wifi. Sin red no se sabe, y no se
     * descarga.
     */
    private fun downloadsAllowed(): Boolean {
        if (settings.loadConfig().downloads.overMobileData) return true
        val cm = appCtx.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun pump() {
        if (downloading) return
        // La cola se queda intacta: cuando vuelva una red sin coste, el siguiente
        // request() la reanuda desde donde estaba.
        if (!downloadsAllowed()) return
        val next = queue.removeFirstOrNull() ?: return
        downloading = true
        scope.launch {
            try {
                download(next)
            } finally {
                downloading = false
                pump()
            }
        }
    }

    private suspend fun download(exerciseId: String) {
        val entry = manifest?.entry(exerciseId) ?: return
        val url = manifest?.urlFor(exerciseId) ?: return
        val target = cache.repoFile(exerciseId, entry.rev)
        states[exerciseId] = VideoState.Downloading(0f)

        val ok = withContext(Dispatchers.IO) {
            var lastPercent = -1
            runCatching {
                Downloader.download(url, target, entry.bytes) { p ->
                    // El progreso llega del hilo de descarga y el estado lo lee Compose,
                    // así que hay que volver al principal. Solo al cambiar el entero: si
                    // no, son miles de saltos de hilo para mover una barra 100 pasos.
                    val percent = (p * 100).toInt()
                    if (percent != lastPercent) {
                        lastPercent = percent
                        scope.launch { states[exerciseId] = VideoState.Downloading(p) }
                    }
                }
            }.onFailure { Log.w(TAG, "no se pudo descargar $exerciseId", it) }.isSuccess
        }

        if (ok) {
            // Se poda DESPUES de tener la nueva: al reves, una descarga fallida dejaria
            // al usuario sin el video que ya tenia.
            cache.dropOtherRevisions(exerciseId, entry.rev)
            states[exerciseId] = VideoState.Ready(target)
        } else {
            states[exerciseId] = VideoState.Pending
        }
    }

    private companion object {
        const val TAG = "VideoRepository"
        const val KEY_MANIFEST = "video_manifest_json"

        // Un solo sitio a proposito: TD-063 lo cambia por uno por usuario
        // (/users/<uid>/videos.json) tocando esta constante y nada mas.
        const val MANIFEST_URL =
            "https://raw.githubusercontent.com/maurozegarra/master-app/main/videos.json"
    }
}
