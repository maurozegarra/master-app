package com.maurozegarra.master.audio

import android.content.Context
import com.maurozegarra.master.R
import com.maurozegarra.master.data.SettingsStore
import com.maurozegarra.master.model.BeepVoice
import com.maurozegarra.master.model.BeepVoices
import java.io.File

/**
 * La voz de los pitidos convertida en lo que el reproductor entiende (TD-171).
 *
 * Los recursos van en un `when` con su R.raw y no buscados por nombre: el build de release
 * recorta los recursos que el código no nombra, y un sonido buscado por texto desaparecería
 * del APK sin avisar.
 */
object BeepVoiceAudio {

    fun exists(path: String): Boolean = runCatching { File(path).canRead() }.getOrDefault(false)

    fun current(context: Context): BeepVoice =
        BeepVoices.resolve(SettingsStore(context).loadConfig().masterConfig.beepVoice, ::exists)

    fun uri(context: Context, sound: BeepVoice.Sound): String = when (sound) {
        is BeepVoice.Sound.SystemFile -> "file://" + sound.path
        is BeepVoice.Sound.Raw -> "android.resource://${context.packageName}/" + when (sound.name) {
            "beep_high_tick" -> R.raw.beep_high_tick
            "beep_high_cue" -> R.raw.beep_high_cue
            "beep_low_tick" -> R.raw.beep_low_tick
            "beep_low_cue" -> R.raw.beep_low_cue
            "beep_double_tick" -> R.raw.beep_double_tick
            "beep_double_cue" -> R.raw.beep_double_cue
            "beep_work" -> R.raw.beep_work
            else -> R.raw.beep_second
        }
    }
}
