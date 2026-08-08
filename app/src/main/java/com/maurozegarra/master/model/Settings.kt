package com.maurozegarra.master.model

// Modelo de ajustes para MASTER: solo lo que usa el player y su alarma.
// La UI de Ajustes se completa en la Fase 5.

/** Modo de tema de la app. */
const val THEME_AUTO = 0
const val THEME_LIGHT = 1
const val THEME_DARK = 2

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
)

/** Configuración completa de MASTER: bloque general + player. */
data class AppConfig(
    val general: GeneralConfig = GeneralConfig(),
    val athlete: AthleteConfig = AthleteConfig(),
)

/** Color de acento con etiqueta legible. */
data class AccentColor(val argb: Long, val label: String)

/** Paleta de colores de acento disponibles. */
val ACCENT_COLORS: List<AccentColor> = listOf(
    AccentColor(0xFF3DDC84, "Green"),
    AccentColor(0xFFA06CFF, "Purple"),
    AccentColor(0xFF9E9E9E, "Gray"),
    AccentColor(0xFF4A90D6, "GALA"),
    AccentColor(0xFFFF69B4, "NIKO"),
    AccentColor(0xFFFF5252, "MASTER"),
)
