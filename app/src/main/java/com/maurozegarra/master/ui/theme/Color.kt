package com.maurozegarra.master.ui.theme

import androidx.compose.ui.graphics.Color

// Paleta base (modo oscuro). El acento es un PLACEHOLDER hasta definir el branding
// definitivo (ver to-do.md: "Paleta de acento").
val BG = Color(0xFF000000)
val SURFACE = Color(0xFF1C1E1F)
val TRACK = Color(0xFF2A2D2F)
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
