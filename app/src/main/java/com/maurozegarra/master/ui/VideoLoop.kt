package com.maurozegarra.master.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Vídeo en bucle y sin sonido, para mostrar cómo se ejecuta un ejercicio.
 *
 * Va mudo a propósito: el audio pelearía con los beeps del player y obligaría a gestionar
 * el foco de audio, además de pisar la música del usuario. Un bucle mudo es lo que sirve
 * para recordar una técnica.
 *
 * [paused] sigue al estado del player: al pausar el entrenamiento el vídeo se detiene, en
 * vez de seguir girando sobre una pantalla atenuada.
 */
@Composable
fun VideoLoop(
    uri: Uri,
    paused: Boolean,
    modifier: Modifier = Modifier,
    onRenderingStart: () -> Unit = {},
) {
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                VideoView(ctx).apply {
                    setOnPreparedListener { mp ->
                        mp.isLooping = true
                        mp.setVolume(0f, 0f)
                        // "Preparado" no es "ya se ve": hasta el primer fotograma la
                        // superficie sigue en negro. Quien llama necesita este momento
                        // exacto para retirar lo que esté tapando el hueco.
                        mp.setOnInfoListener { _, what, _ ->
                            if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) onRenderingStart()
                            false
                        }
                        if (!paused) start()
                    }
                    setVideoURI(uri)
                }
            },
            update = { view ->
                if (paused) {
                    if (view.isPlaying) view.pause()
                } else if (!view.isPlaying) {
                    view.start()
                }
            },
            onRelease = { it.stopPlayback() },
        )
    }
}

/**
 * El vídeo de un ejercicio con la forma real del archivo: en el player mientras se
 * entrena, y quieto como miniatura en el editor.
 *
 * Solo se reproduce cuando [playing]. En el player eso es únicamente la etapa WORK: el
 * movimiento tiene sentido mientras se ejecuta el ejercicio, no mientras se cuenta atrás
 * para empezar. En las demás etapas se ve el primer fotograma, que sigue diciendo de qué
 * ejercicio se trata sin robar atención al reloj.
 *
 * El contenedor toma la relación de aspecto del propio archivo en vez de una altura fija:
 * con una altura pensada para vídeos horizontales, uno vertical saldría recortado o
 * dejaría dos franjas negras enormes. Se elige el mayor tamaño de esa proporción que
 * quepa en el hueco disponible, así que el reloj nunca se ve empujado fuera.
 *
 * `MediaMetadataRetriever` es parte del framework: evita añadir Coil o Media3 solo para
 * sacar un fotograma. La lectura va fuera del hilo principal porque abre y decodifica el
 * archivo.
 */
@Composable
fun ExerciseVideo(uri: Uri, playing: Boolean, paused: Boolean, modifier: Modifier = Modifier) {
    val info = rememberVideoInfo(uri)
    // Mientras se lee el archivo se asume horizontal, que es la forma mas comun: al
    // llegar el dato real el hueco se ajusta, y para un vertical el salto ocurre antes
    // de que el usuario empiece a moverse.
    val ratio = info?.ratio ?: (16f / 9f)
    // Se reinicia tambien al empezar a reproducir: cada vez que se entra en WORK hay un
    // VideoView nuevo, y vuelve a haber un hueco negro que tapar.
    var rendering by remember(uri, playing) { mutableStateOf(false) }

    // Sin fondo propio: hasta que hay algo que enseñar se ve el color de etapa, y no un
    // rectangulo negro. El video ya trae el suyo, y el contenedor tiene su misma forma,
    // asi que no hay franjas que rellenar.
    Box(
        modifier.aspectRatio(ratio, matchHeightConstraintsFirst = true),
        contentAlignment = Alignment.Center,
    ) {
        if (playing) {
            VideoLoop(uri, paused, Modifier.fillMaxSize()) { rendering = true }
        }
        // El fotograma quieto se queda ENCIMA del video hasta que este dibuja su primer
        // frame. Es lo que evita el parpadeo negro al entrar en WORK: VideoView es un
        // SurfaceView y su superficie esta en negro hasta ese momento.
        if (!playing || !rendering) {
            info?.frame?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

/** Primer fotograma y proporción del vídeo, leídos de una sola pasada. */
private class VideoInfo(val frame: Bitmap?, val ratio: Float)

@Composable
private fun rememberVideoInfo(uri: Uri): VideoInfo? {
    val ctx = LocalContext.current
    var info by remember(uri) { mutableStateOf<VideoInfo?>(null) }

    LaunchedEffect(uri) {
        info = withContext(Dispatchers.IO) { readVideoInfo(ctx, uri) }
    }

    DisposableEffect(uri) {
        onDispose { info = null }
    }

    return info
}

private fun readVideoInfo(ctx: Context, uri: Uri): VideoInfo? = runCatching {
    MediaMetadataRetriever().use { r ->
        r.setDataSource(ctx, uri)
        val frame = r.getFrameAtTime(0)
        // El fotograma ya viene girado, asi que sus lados son los que se van a ver. Solo
        // si no hay fotograma se recurre a los metadatos, y ahi si hay que aplicar la
        // rotacion a mano: un vertical grabado con el movil de lado declara ancho > alto.
        val ratio = if (frame != null && frame.height > 0) {
            frame.width.toFloat() / frame.height
        } else {
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull()
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull()
            val rotated = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull()?.let { it == 90 || it == 270 } ?: false
            if (w == null || h == null || w <= 0f || h <= 0f) null
            else if (rotated) h / w else w / h
        }
        ratio?.let { VideoInfo(frame, it) }
    }
}.getOrNull()

/**
 * Abre el vídeo en el reproductor del sistema, que ya trae controles completos: no hace
 * falta construir un reproductor propio solo para ver un clip entero.
 */
fun openVideoExternally(context: android.content.Context, uri: Uri) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
        )
    }
}
