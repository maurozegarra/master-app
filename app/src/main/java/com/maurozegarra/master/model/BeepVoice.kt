package com.maurozegarra.master.model

/**
 * La voz de los pitidos de ESTE teléfono (TD-171).
 *
 * Existe porque el usuario y NIKO entrenan juntos, cada uno con su teléfono, y con un solo
 * pitido para los dos "no sabemos si es el suyo o el mío" (26-sep). El sonido por etapa ya
 * existía, pero es del training; esto es del teléfono, así que cada uno suena distinto con
 * la misma rutina.
 *
 * Cada voz trae sus dos sonidos: el tic de cada segundo de la cuenta atrás y el aviso de
 * cambio de etapa. Las del app existen en cualquier teléfono. Las de Samsung son sonidos del
 * sistema que el usuario eligió de su teléfono: si el archivo no está -otro teléfono, otra
 * versión de One UI-, la voz cae a [CLASSIC]. Lo que se personalizó por etapa sigue mandando
 * sobre la voz.
 */
data class BeepVoice(
    val id: String,
    val label: String,
    val tick: Sound,
    val cue: Sound,
) {
    /** De dónde sale un sonido: un recurso del app (por nombre) o un archivo del sistema. */
    sealed interface Sound {
        data class Raw(val name: String) : Sound
        data class SystemFile(val path: String) : Sound
    }

    val needsSystemFile: Boolean get() = tick is Sound.SystemFile || cue is Sound.SystemFile
}

object BeepVoices {

    val CLASSIC = BeepVoice("classic", "Classic", BeepVoice.Sound.Raw("beep_second"), BeepVoice.Sound.Raw("beep_work"))

    private const val SAMSUNG_UI = "/system/media/audio/ui/"

    private fun samsung(id: String, label: String, file: String) =
        BeepVoice(id, label, BeepVoice.Sound.SystemFile(SAMSUNG_UI + file), BeepVoice.Sound.SystemFile(SAMSUNG_UI + file))

    /**
     * Todas, en el orden en que se ofrecen: primero las del app, que existen en cualquier
     * teléfono, y después las de Samsung.
     *
     * Las voces del app se distinguen por TONO y por RITMO, no por volumen: tienen que
     * reconocerse con los dos teléfonos sonando en la misma habitación.
     */
    val ALL: List<BeepVoice> = listOf(
        CLASSIC,
        BeepVoice("high", "High", BeepVoice.Sound.Raw("beep_high_tick"), BeepVoice.Sound.Raw("beep_high_cue")),
        BeepVoice("low", "Low", BeepVoice.Sound.Raw("beep_low_tick"), BeepVoice.Sound.Raw("beep_low_cue")),
        BeepVoice("double", "Double", BeepVoice.Sound.Raw("beep_double_tick"), BeepVoice.Sound.Raw("beep_double_cue")),
        samsung("samsung_volume", "Samsung · Volume", "TW_Volume_control.ogg"),
        samsung("samsung_volume_retro", "Samsung · Volume Retro", "TW_Volume_control_Retro.ogg"),
        samsung("samsung_dial_retro", "Samsung · Dial Retro", "Dial_Retro.ogg"),
        samsung("samsung_timepicker", "Samsung · Timepicker", "Timepicker_scroll.ogg"),
    )

    /** Las que se pueden elegir en este teléfono: las de Samsung solo si su archivo está. */
    fun available(exists: (String) -> Boolean): List<BeepVoice> =
        ALL.filter { v -> !v.needsSystemFile || listOf(v.tick, v.cue).all { it !is BeepVoice.Sound.SystemFile || exists(it.path) } }

    /**
     * La voz que suena. Si la guardada no existe -un id viejo- o su archivo de Samsung falta
     * en este teléfono, [CLASSIC]: el pitido tiene que sonar siempre.
     */
    fun resolve(id: String, exists: (String) -> Boolean): BeepVoice =
        available(exists).firstOrNull { it.id == id } ?: CLASSIC
}
