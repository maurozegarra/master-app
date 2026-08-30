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

/** Ajustes específicos del player. */
data class MasterConfig(
    /** Reloj del player con ceros a la izquierda: "00:30" en vez de "30". */
    val padPlayerClock: Boolean = false,
)

/** Ajustes de descarga de vídeos. */
data class DownloadsConfig(
    /**
     * Permitir descargas con datos móviles.
     *
     * Por defecto sí: un training entero son unos 20 MB, molesto pero asumible, y con la
     * opción contraria el usuario abriría un training fuera de casa y no se descargaría
     * nada sin ninguna explicación visible. Quien quiera reservarlo para wifi lo apaga.
     */
    val overMobileData: Boolean = true,
)

/** Configuración completa de MASTER: bloque general + player + descargas. */
data class AppConfig(
    val general: GeneralConfig = GeneralConfig(),
    val masterConfig: MasterConfig = MasterConfig(),
    val downloads: DownloadsConfig = DownloadsConfig(),
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
