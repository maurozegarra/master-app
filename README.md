# MASTER

App Android (Kotlin + Jetpack Compose, Material Design 3) para crear y ejecutar
rutinas de entrenamiento con un player tipo timer con intervalos.

- Paquete: `com.maurozegarra.master`
- Dominio: `Training` > `Workout` > `Exercise` (los workouts pueden ser rotativos)
- Idioma: solo inglés
- Mínimo SDK: 26 (Android 8.0)

## Stack

- Kotlin + Jetpack Compose (BOM 2024.06)
- Material Design 3
- ViewModel + StateFlow
- Foreground Service para el player
- Persistencia: JSON serializado en SharedPreferences
- Sin frameworks de red ni DI

## Scripts

- `run-tests.ps1` — tests unitarios
- `verify-compile.ps1` — tests + compila release (sin instalar)
- `forge-status.ps1` — estado del to-do (lee `docs/forge-todo.json`)
- `build-debug.ps1` — tests, compila release, instala en device, bumpea versión
- `build-release.ps1` — release a GitHub Releases + update.json

## Documentación

- `AGENTS.md` — guía para asistentes de IA (stack, estructura, reglas, scripts)
- `docs/athletic-forge.md` — sistema de verificación y to-do
- `docs/forge-todo.json` — fuente de verdad del to-do
- `to-do.md` — generado por `forge-status.ps1` (no editar directamente)
- `.windsurf/workflows/` — workflows operativos (commit, build, diagnose, etc.)

## Entorno

- Gradle 9.4.1 via `gradlew`
- `JAVA_HOME = C:\Program Files\Android\Android Studio\jbr`
- Versionado: +1 por cada APK generado
- APK release firmado con clave debug (permite updates sin desinstalar)
