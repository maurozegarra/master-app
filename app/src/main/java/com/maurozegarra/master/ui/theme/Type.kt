package com.maurozegarra.master.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Tipografía base (fuentes por defecto del sistema). Las fuentes de branding
// (Wallpoet para el wordmark TIMES) se integrarán en la fase de branding.
//
// headlineSmall es el título de los AlertDialog. Por defecto mide 24 sp y quedaba más grande
// que el título de la pantalla de atrás (20 sp en negrita, SettingsScaffold): el diálogo
// depende de la pantalla y no puede gritar más que ella (4-oct). Se cambia aquí, una vez,
// para todos los diálogos del app.
private val base = Typography()

val AppTypography = base.copy(
    headlineSmall = base.headlineSmall.copy(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
)
