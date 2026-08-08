package com.maurozegarra.master.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import kotlin.math.pow

/**
 * Reproductor de beeps del player y previews de tonos del exercise editor.
 * USAGE_MEDIA, escalado perceptual en dB y ducking de la música.
 */
class AlarmPlayer(private val context: Context) {

    private var previewPlayer: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null

    private val audioManager: AudioManager
        get() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun stop() {
        abandonFocus()
    }

    // ---------- Previsualización (exercise editor) ----------

    /**
     * Reproduce un tono como vista previa: stream de media + USAGE_MEDIA + ducking
     * + volumen perceptual.
     */
    fun previewTone(uriStr: String, volume: Float) {
        stopPreview()
        try {
            requestFocus()
            val mp = MediaPlayer()
            mp.setAudioAttributes(mediaAttrs())
            mp.setDataSource(context, Uri.parse(uriStr))
            val vol = perceptualVolume(volume)
            mp.setVolume(vol, vol)
            mp.setOnPreparedListener { it.start() }
            mp.setOnCompletionListener {
                it.release()
                if (previewPlayer === it) previewPlayer = null
                abandonFocus()
            }
            mp.prepareAsync()
            previewPlayer = mp
        } catch (_: Exception) {
        }
    }

    /**
     * Reproduce un beep corto sin gestionar foco en cada llamada (evita ducking).
     * Crea un MediaPlayer por beep y lo libera al completar.
     */
    fun beepTone(uriStr: String) {
        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(mediaAttrs())
            mp.setDataSource(context, Uri.parse(uriStr))
            mp.setOnPreparedListener { it.start() }
            mp.setOnCompletionListener { it.release() }
            mp.setOnErrorListener { mp2, _, _ -> mp2.release(); true }
            mp.prepareAsync()
        } catch (_: Exception) {
        }
    }

    fun stopPreview() {
        try {
            previewPlayer?.stop()
            previewPlayer?.release()
        } catch (_: Exception) {
        }
        previewPlayer = null
        abandonFocus()
    }

    // ---------- Volumen / stream / foco ----------

    /**
     * Convierte el ajuste lineal (0..1) a una ganancia perceptual con una curva
     * en dB ([VOLUME_DB_RANGE]). 0% -> silencio; 100% -> 0 dB (máximo).
     */
    private fun perceptualVolume(setting: Float): Float {
        val x = setting.coerceIn(0f, 1f)
        if (x <= 0f) return 0f
        val db = (x - 1f) * VOLUME_DB_RANGE
        return 10.0.pow(db / 20.0).toFloat().coerceIn(0f, 1f)
    }

    /** Foco transitorio con ducking: baja la música mientras suena la alarma. */
    private fun requestFocus() {
        if (focus != null) return
        try {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(mediaAttrs())
                .build()
            audioManager.requestAudioFocus(req)
            focus = req
        } catch (_: Exception) {
        }
    }

    private fun abandonFocus() {
        val req = focus ?: return
        try {
            audioManager.abandonAudioFocusRequest(req)
        } catch (_: Exception) {
        }
        focus = null
    }

    private fun mediaAttrs() = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private companion object {
        /** Rango (dB) de la curva perceptual de volumen: 100% -> 0 dB, 0% -> -18 dB. */
        const val VOLUME_DB_RANGE = 18f
    }
}
