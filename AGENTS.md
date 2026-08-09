# AGENTS.md

Guía para asistentes de IA (Devin, Cascade, Copilot, etc.) que trabajen en este repo.

## Antes de empezar

**Al iniciar una sesión de trabajo, corre `.\forge-status.ps1 -SkipTests`** y
reporta el estado (N/M hechos, qué items siguen pendientes) antes de proponer
trabajo. El to-do es la memoria del proyecto: úsalo para orientarte.

**Lee `docs/master-forge.md` antes de hacer cualquier cambio rastreable.**
El Forge es el sistema de verificación y to-do del proyecto. Define:

- El workflow del agente (4 escenarios: feature, fix, mantenimiento, estado).
- La convención de commits con IDs `TD-XXX` (`feat: TD-NNN ...`, `fix: TD-NNN ...`).
- El to-do estructurado: la fuente de verdad es `docs/forge-todo.json`, y
  `to-do.md` se genera automáticamente con `.\forge-status.ps1`.
- **Nunca edites `to-do.md` directamente**. Edita `docs/forge-todo.json` y regenera.

## Proyecto

**MASTER** — App Android (Kotlin + Jetpack Compose, Material 3) para crear y ejecutar rutinas de entrenamiento con un player tipo timer con intervalos.

- Paquete: `com.maurozegarra.master`
- Dominio: `Training` > `Workout` > `Exercise` (los workouts pueden ser rotativos)
- Idioma: solo inglés
- Mínimo SDK: 26 (Android 8.0)

## Stack

- Kotlin + Jetpack Compose (BOM 2024.06)
- Material Design 3
- ViewModel + StateFlow
- Koin 4.2.0 para inyección de dependencias
- Foreground Service para el player (notificaciones, restablecimiento tras muerte del proceso)
- Persistencia: JSON serializado en SharedPreferences
- Sin frameworks de red

## Estructura del repo

```
app/src/main/java/com/maurozegarra/master/
├── MainActivity.kt          # Entry point, Scaffold, navegación, update bar
├── MasterViewModel.kt       # ViewModel principal (estado, lógica de dominio)
├── SettingsViewModel.kt     # ViewModel de Ajustes
├── PlayerBus.kt             # Bus de estado/comandos del player (Service ↔ UI)
├── model/                   # Modelos: Workout, PlayerStep, Settings, StepEngine
├── data/                    # Persistencia: WorkoutStore, SettingsStore, MasterDefaults
│                            #   ExerciseCatalog, ExerciseIcons
├── audio/                   # AlarmPlayer (beeps del player + previews)
├── notify/                  # WorkoutPlayerService (foreground service del player)
├── ui/
│   ├── theme/               # AppTheme, colores, tipografía, Dimens
│   ├── master/              # Pantallas: lista, editor, player, historial, calendar
│   ├── MasterWordmark.kt   # Wordmark "MASTER" (Wallpoet)
│   ├── Reorderable.kt      # Drag-and-drop sin librerías
│   ├── WheelTimePicker.kt  # Selector de tiempo tipo rueda
│   ├── AnimatedGlowBorder.kt
│   └── CommonComponents.kt  # Botones, steppers, switches reutilizables
├── update/                  # UpdateChecker, UpdateDialog (barra de update in-app)
├── i18n/                    # Strings (solo EN)
└── util/                    # Format.kt

app/src/test/java/com/maurozegarra/master/model/
├── StepEngineTest.kt        # Tests de generación de pasos
├── WorkoutTest.kt           # Tests de dominio (rotación, pesos, hasContent)
├── PlayerStepTest.kt        # Tests de propiedades computadas (manual, estimatedSec)
└── RotationTest.kt          # Tests de rotación idempotente
```

## Scripts

### `run-tests.ps1` — Tests unitarios
Corre `gradlew test`. Falla si algún test no pasa. Usado por los demás scripts como puerta de verificación.

### `verify-compile.ps1` — Verificar antes de commit
Corre tests + `compileReleaseKotlin`. Sin instalar, sin bump de versión. Útil para iterar.

### `forge-status.ps1` — Estado del to-do
Lee `docs/forge-todo.json`, deriva el estado de cada item (tests + git log), genera `to-do.md`.
- `.\forge-status.ps1` — corre tests y genera el to-do.
- `.\forge-status.ps1 -SkipTests` — usa resultados existentes (instantáneo).

### `build-debug.ps1` — Iteración rápida
Corre tests, compila release (minificado), instala en el device, copia APK a `releases/` y a `/sdcard/Download/` del teléfono, bumpea versionCode.

```powershell
.\build-debug.ps1 -Message "feat: TD-NNN descripción del cambio"
```

**Lanzar el app tras instalar:** después de `build-debug.ps1` o de un `adb install`
manual, lanzar el app en el dispositivo para que el usuario no tenga que abrirlo
manualmente:

```powershell
adb -s <serial> shell monkey -p com.maurozegarra.master -c android.intent.category.LAUNCHER 1
```

### `build-release.ps1` — Release a GitHub
Corre tests, compila release, sube APK a GitHub Releases, actualiza `update.json`, bumpea versionCode, commit + push.

```powershell
.\build-release.ps1 -Message "release: TD-NNN descripción"
```

## Reglas de oro

- **No implementar sin autorización explícita del usuario.** Registrar el TD, mostrar qué se va a hacer y esperar confirmación antes de tocar código.
- **Checklist pre-fix (antes de proponer cualquier cambio):**
  1. **¿Estoy parcheando el síntoma o arreglando la causa?** — Investigar el flujo completo antes de tocar código. No asumir que el primer punto de falla es la causa.
  2. **¿Qué casos no estoy viendo?** — Listar edge cases: go-back, cierre abrupto, REST/PREP/COOLDOWN steps, ejercicios de 1 set, rotating workouts, timer vs reps. Si no los veo, preguntarle al usuario.
  3. **¿Necesito más información antes de tocar código?** — Si no se entiende el flujo completo, agregar logging de diagnóstico y probar en dispositivo antes de proponer fix. No parchar a ciegas.
- **No hacer commit sin autorización explícita del usuario.**
- **Nada se declara done sin `.\verify-compile.ps1` verde** (tests + compila release). Aplica a cualquier item que toque código.
- **Un TD es `done` cuando el usuario lo aprueba tras probar en el dispositivo**, no cuando compila. Mientras esté `pending`, los ajustes (fixes y refinements de forma) son parte del mismo TD. Cambios posteriores a la aprobación = nuevo TD.
- **No crear archivos temporales** (screenshots, scripts de prueba) en el repo.
- **Usar `build-debug.ps1`** para cada iteración: incrementa versión, instala, copia a Download.
- **Lanzar el app tras instalar** con `adb shell monkey -p com.maurozegarra.master -c android.intent.category.LAUNCHER 1` para que el usuario pueda probar de inmediato.
- **Reportar tras instalar:** tras cada `build-debug.ps1` o instalación, reportar al usuario: número de versión instalada, qué TD se probó, y pasos concretos para verificar el cambio en el dispositivo (qué mirar, qué interactuar).
- **Push = GitHub Release.** Cuando el usuario pida "push", proponer `build-release.ps1` (sube APK a GitHub Releases + actualiza `update.json` + commit + push), no `git push` solo.
- **Minificar siempre**: el APK release pesa ~1.2MB vs ~16MB en debug.
- **Firma estable**: el release se firma con la clave debug para permitir updates sin desinstalar.
- **Versionado**: +1 por cada APK generado. `versionCode` y `versionName` en `app/build.gradle.kts`.
- **Idioma**: solo inglés en la app. Comunicación con el usuario en español.
- **Comentarios en código**: documentar el porqué de decisiones no obvias, trade-offs, workarounds y restricciones del dominio. No comentar lo que el código ya dice por sí solo. Los comentarios deben responder a "¿por qué esto es así?" y no a "¿qué hace esto?". Priorizar comentarios en lógica de negocio, protocolos, y código que pueda parecer contraintuitivo a primera vista.
- **Cada cambio de comportamiento va con test.** Si tocas el motor, agregas o actualizas el test correspondiente.
- **Commits referencian el to-do**: `feat: TD-NNN ...`, `fix: TD-NNN ...`, `chore: TD-NNN ...`.
- **Commits atómicos**: un commit = un cambio lógico (ver `.windsurf/workflows/commit.md`).
- **Sin firma ni co-author en commits.** NUNCA incluir "Generated with Devin", "Co-Authored-By: Devin", ni ninguna variante. El commit es del usuario, no del asistente.
- **No se borra un test para que pase el build.** Si un test falla, se arregla el código o se cambia el test con justificación explícita.

## Dispositivo de prueba

- Samsung Galaxy S26 Ultra
- ADB inalámbrico vía mDNS: descubrir con `adb mdns services` y conectar con
  `adb connect <host:puerto>` reportado ahí. El puerto cambia entre reinicios;
  no hardcodear IP ni puerto.
- Densidad: 450 dpi
- Resolución: 1080x2340

## Update in-app

- `update.json` en la raíz del repo define la versión disponible.
- `UpdateChecker.kt` hace fetch desde GitHub raw con cache-buster.
- `UpdateDialog.kt` renderiza una barra inferior no-modal estilo Telegram.
- Flujo: detecta update → muestra barra → tap descarga APK → tap instala.

## Documentación

- `docs/master-forge.md` — sistema de verificación y to-do (leer antes de cambiar).
- `docs/forge-todo.json` — fuente de verdad del to-do (no editar `to-do.md` directamente).
- `docs/hoja-de-ruta.md` — historial del proyecto (Fases 0–8).
- `.windsurf/workflows/` — workflows operativos genéricos (commit atómico, validación, convenciones, build).
- `.devin/workflows/` — workflows específicos del proyecto (foreground service, etc.).
