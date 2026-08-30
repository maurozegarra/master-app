package com.maurozegarra.master.ui

import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
fun VideoLoop(uri: Uri, paused: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                VideoView(ctx).apply {
                    setOnPreparedListener { mp ->
                        mp.isLooping = true
                        mp.setVolume(0f, 0f)
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
 * Primer fotograma del vídeo, para usarlo como miniatura.
 *
 * `MediaMetadataRetriever` es parte del framework: evita añadir Coil o Media3 solo para
 * esto. La extracción va fuera del hilo principal porque abre y decodifica el archivo.
 */
@Composable
fun VideoThumbnail(uri: Uri, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var frame by remember(uri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uri) {
        frame = withContext(Dispatchers.IO) {
            runCatching {
                MediaMetadataRetriever().use { r ->
                    r.setDataSource(ctx, uri)
                    r.getFrameAtTime(0)
                }
            }.getOrNull()
        }
    }

    DisposableEffect(uri) {
        onDispose { frame = null }
    }

    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        frame?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

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
