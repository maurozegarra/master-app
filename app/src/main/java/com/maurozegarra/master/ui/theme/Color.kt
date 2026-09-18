package com.maurozegarra.master.ui.theme

import androidx.compose.ui.graphics.Color

// Paleta base (modo oscuro). El acento es un PLACEHOLDER hasta definir el branding
// definitivo (ver to-do.md: "Paleta de acento").
val BG = Color(0xFF000000)
val SURFACE = Color(0xFF1C1E1F)
val TRACK = Color(0xFF2A2D2F)
/**
 * Texto principal del tema oscuro. **Blanco roto, no blanco puro.**
 *
 * `#FFFFFF` sobre `BG` negro es un contraste de 21:1 que produce halación y cansa la
 * vista, y este app se usa entrenando, a veces con mala luz. Bajarlo a `#E6E9EA` deja
 * 17.2:1 sobre el negro y 13.9:1 sobre `SURFACE`: el minimo AAA son 7:1, asi que sobra.
 *
 * Frio a propósito, de la misma familia que [TEXT_DIM] (`#9AA0A3`): un blanco neutro
 * cantaría al lado del resto de la paleta.
 */
val TEXT_PRIMARY = Color(0xFFE6E9EA)

val TEXT_DIM = Color(0xFF9AA0A3)
val TEXT_FADED = Color(0xFF5A5D5F)
val ON_ACCENT = Color(0xFF001316)

/** Acento por defecto (placeholder). */
const val DEFAULT_ACCENT = 0xFFFF5252

/** Acento rosa que activa el tema especial "Barbie". */
const val PINK_ACCENT = 0xFFFF69B4

/** Acento azul que activa el tema especial "Stitch". */
const val STITCH_ACCENT = 0xFF4A90D6

/**
 * Estados de una sesión, para las etiquetas de `StatusBadge`.
 *
 * Fijos y no derivados del acento: "completo" y "saltado" significan lo mismo lleve el
 * perfil el color que lleve, y si siguieran al acento dejarían de distinguirse entre sí.
 * Estaban sueltos como literales dentro de `HistoryScreen`, que es lo que los mantenía
 * fuera del tema y hacía que nadie supiera que existían.
 *
 * "Parcial" no está aquí: usa el acento a propósito, porque es el estado de lo que dejaste
 * a medias y conviene que lea como "tuyo".
 */
val STATUS_DONE = Color(0xFF4CAF50)
val STATUS_SKIPPED = Color(0xFFFFA000)

/**
 * Rojo de lo destructivo, para **texto**: el "Delete" de los diálogos de confirmación y
 * los avisos de error.
 *
 * Fijo y no derivado del acento, para que se lea como destructivo aunque el usuario elija
 * un acento rojizo —que es el caso del perfil MASTER—.
 *
 * Nació con los colores de las acciones del swipe (TD-039), que eran cuatro círculos
 * macizos: rojo borrar, naranja duplicar, azul editar, verde asignar. Esos tres se
 * borraron al pasar el panel a círculos huecos en blanco (TD-083): el rojo macizo grita
 * "algo va mal" aunque solo estés borrando a conciencia, y cuatro colores fuertes en una
 * fila pesaban más que la lista que acompañan. Aquí el rojo sigue teniendo sentido porque
 * es una palabra, no un bloque.
 */
val ACTION_DELETE = Color(0xFFD93A32)

/**
 * Los tres colores de "How did the weight feel?" en el historial (TD-118), con el sentido
 * que les dio el usuario el 17-sep-2026:
 *
 * - [FEEL_UP], verde: ligero. Luz verde para subir el peso o la dificultad.
 * - [FEEL_STEADY], ambar: justo. Se esta repitiendo la carga, y es una ADVERTENCIA: "ese
 *   ambar no deberia permanecer asi por meses, es una senal de que hay algo que modificar
 *   en el ejercicio". Justo hoy esta bien; justo siempre es un ejercicio estancado.
 * - [FEEL_DOWN], rojo: mucho peso, muy dificil.
 *
 * Fijos y no del acento por la misma razon que los estados de sesion: tienen que decir lo
 * mismo en cualquier perfil. Son los mismos valores que [STATUS_DONE], [STATUS_SKIPPED] y
 * [ACTION_DELETE], pero con su propio nombre: significan otra cosa, y si un dia cambia el
 * verde de "completo" no tiene por que cambiar el de "subele".
 */
val FEEL_UP = Color(0xFF4CAF50)
val FEEL_STEADY = Color(0xFFFFA000)
val FEEL_DOWN = Color(0xFFD93A32)
