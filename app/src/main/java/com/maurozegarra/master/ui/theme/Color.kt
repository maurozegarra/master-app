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

// Colores de las acciones del swipe (TD-039). Fijos y no derivados del acento: el rojo
// de borrar debe leerse como destructivo aunque el usuario elija un acento rojizo.
val ACTION_EDIT = Color(0xFF2F6FED)
val ACTION_DUPLICATE = Color(0xFFE2861E)
val ACTION_DELETE = Color(0xFFD93A32)
