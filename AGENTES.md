# AGENTES.md

Guía para asistentes de IA (Cascade, Copilot, etc.) que trabajen en este repo.

## Proyecto

**Athletic** — App Android (Kotlin + Jetpack Compose, Material 3) para crear y ejecutar rutinas de entrenamiento con un player tipo timer con intervalos.

- Paquete: `com.athletic`
- Dominio: `Training` > `Workout` > `Exercise` (los workouts pueden ser rotativos)
- Idioma: solo inglés
- Mínimo SDK: 26 (Android 8.0)

## Stack

- Kotlin + Jetpack Compose (BOM 2024.06)
- Material Design 3
- ViewModel + StateFlow
- Foreground Service para el player (notificaciones, restablecimiento tras muerte del proceso)
- Persistencia: JSON serializado en SharedPreferences
- Sin frameworks de red ni DI

## Estructura del repo

```
app/src/main/java/com/athletic/
├── MainActivity.kt          # Entry point, Scaffold, navegación, update bar
├── AthleteViewModel.kt      # ViewModel principal (estado, lógica de dominio)
├── model/                   # Modelos: Training, Workout, Exercise, etc.
├── store/                   # Persistencia (WorkoutStore, SessionStore)
├── service/                 # Foreground service del player
├── ui/
│   ├── theme/               # AppTheme, colores, tipografía
│   ├── athlete/             # Pantallas: lista, editor, player, historial
│   ├── CommonComponents.kt  # Componentes reutilizables
│   └── Components.kt
├── update/                  # UpdateChecker, UpdateDialog (barra de update)
├── i18n/                    # Strings (solo EN)
└── util/                    # Utilidades
```

## Scripts de build

### `build-debug.ps1` — Iteración rápida
Compila release (minificado), instala en el device, copia APK a `releases/` y a `/sdcard/Download/` del teléfono, bumpea versionCode.

```powershell
.\build-debug.ps1 -Message "feat: descripción del cambio"
```

### `build-release.ps1` — Release a GitHub
Compila release, sube APK a GitHub Releases, actualiza `update.json`, bumpea versionCode, commit + push.

```powershell
.\build-release.ps1 -Message "release: descripción"
```

## Reglas de oro

- **No hacer commit sin autorización explícita del usuario.**
- **No crear archivos temporales** (screenshots, scripts de prueba) en el repo.
- **Usar `build-debug.ps1`** para cada iteración: incrementa versión, instala, copia a Download.
- **Minificar siempre**: el APK release pesa ~1.2MB vs ~16MB en debug.
- **Firma estable**: el release se firma con la clave debug para permitir updates sin desinstalar.
- **Versionado**: +1 por cada APK generado. `versionCode` y `versionName` en `app/build.gradle.kts`.
- **Idioma**: solo inglés en la app. Comunicación con el usuario en español.
- **No agregar comentarios** al código a menos que se pida explícitamente.
- **Preferir ediciones mínimas** sobre reescrituras grandes.

## Dispositivo de prueba

- Samsung Galaxy S26 Ultra
- Conexión ADB inalámbrica: `192.168.18.128:33201`
- Densidad: 450 dpi
- Resolución: 1080x2340

## Update in-app

- `update.json` en la raíz del repo define la versión disponible.
- `UpdateChecker.kt` hace fetch desde GitHub raw con cache-buster.
- `UpdateDialog.kt` renderiza una barra inferior no-modal estilo Telegram.
- Flujo: detecta update → muestra barra → tap descarga APK → tap instala.
