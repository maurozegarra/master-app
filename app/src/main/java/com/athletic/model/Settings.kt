package com.athletic.model

// Modelo de ajustes REDUCIDO para Athletic (carve-out): solo lo que usa el player
// y su alarma. El monolito de mini-timer (Timer/Clock/Water) se descarta; la UI de
// Ajustes se completa en la Fase 5.

/** Modo de salida de audio cuando hay audífonos conectados. */
const val HEADSET_ONLY = 0
const val SPEAKER_AND_HEADSET = 1

/** Modo de tema de la app. */
const val THEME_AUTO = 0
const val THEME_LIGHT = 1
const val THEME_DARK = 2

/**
 * Configuración de alarma/sonido de la pestaña (sonido, volumen, vibración y
 * comportamiento frente a silencio/audífonos). Bloque reutilizable e independiente.
 */
data class AlarmConfig(
    /** URI del tono elegido; null = sonido de alarma por defecto del sistema. */
    val soundUri: String? = null,
    /** Nombre legible del tono elegido, para mostrarlo en ajustes. */
    val soundName: String? = null,
    /** Volumen de la alarma, 0f..1f (curva perceptual en dB al reproducir). */
    val volume: Float = 0.25f,
    /** Vibrar al sonar. */
    val vibrationEnabled: Boolean = false,
    /** Índice del patrón de vibración en [VIBRATION_PATTERNS]. */
    val vibrationPattern: Int = 0,
    /** Sonar aunque el equipo esté en silencio / No molestar. */
    val ignoreSilent: Boolean = true,
    /** Salida de audio con audífonos: [HEADSET_ONLY] o [SPEAKER_AND_HEADSET]. */
    val headsetMode: Int = HEADSET_ONLY,
)

/** Un tono de alarma disponible para seleccionar. */
data class AlarmSound(val name: String, val uri: String)

/** Ajustes generales, comunes a toda la app. */
data class GeneralConfig(
    val accent: Long = 0xFFFF5252,
    /** Tema: [THEME_AUTO] (sigue el sistema), [THEME_LIGHT] o [THEME_DARK]. */
    val themeMode: Int = THEME_AUTO,
)

/** Ajustes específicos del player (pestaña Athlete). */
data class AthleteConfig(
    /** Reloj del player con ceros a la izquierda: "00:30" en vez de "30". */
    val padPlayerClock: Boolean = false,
    /** Alarma propia del player. */
    val alarm: AlarmConfig = AlarmConfig(),
)

/** Configuración completa de Athletic: bloque general + player. */
data class AppConfig(
    val general: GeneralConfig = GeneralConfig(),
    val athlete: AthleteConfig = AthleteConfig(),
)

/** Patrón de vibración con nombre y tiempos (ms) para waveform en bucle. */
data class VibrationPattern(val name: String, val timings: LongArray)

/**
 * Patrones de vibración disponibles. Cada arreglo son tiempos en ms que se
 * repiten en bucle: el primer valor es la espera inicial, luego alternan
 * vibración/silencio.
 */
val VIBRATION_PATTERNS: List<VibrationPattern> = listOf(
    VibrationPattern("Simple", longArrayOf(0, 500, 300)),
    VibrationPattern("Zig-Zig", longArrayOf(0, 200, 120, 200, 500)),
    VibrationPattern("Zig-zig-zig", longArrayOf(0, 180, 100, 180, 100, 180, 500)),
    VibrationPattern("Tap", longArrayOf(0, 70, 350)),
    VibrationPattern("Knock", longArrayOf(0, 60, 90, 60, 600)),
    VibrationPattern("Heartbeat", longArrayOf(0, 130, 110, 260, 600)),
    VibrationPattern("Bounce", longArrayOf(0, 320, 220, 220, 150, 140, 90, 100, 60, 500)),
    VibrationPattern("Dubstep", longArrayOf(0, 220, 90, 90, 90, 380, 110, 200, 350)),
    VibrationPattern("Gallop", longArrayOf(0, 80, 90, 80, 260, 500)),
)

/** Paleta de colores de acento disponibles. */
val ACCENT_COLORS: List<Long> = listOf(
    0xFF4AC0D6,
    0xFF4A90D6,
    0xFF3DDC84,
    0xFFA06CFF,
    0xFF9E9E9E,
    0xFFFF69B4,
    0xFFFF5252,
)
