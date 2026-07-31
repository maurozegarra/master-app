# Hoja de ruta: carve-out de Athlete -> Athletic

Estrategia: **extraer y podar** (no reescribir de cero). Preservar la lógica
probada de mini-timer y reemplazar solo el shell (app, navegación, ajustes, i18n).

> Estado general: **carve-out completado, compilando y publicando releases**.
> Fases 0–8 ejecutadas; hay 110+ releases en GitHub (última v1.0.110, código en
> `versionCode 111`). Quedan pendientes de verificación funcional y limpieza
> (ver "Pendientes" al final).

## Decisiones de producto (DEFINIDAS)

1. **Timer simple suelto**: NO. El **player cubre todo por ahora**; no se incluye
   un cronómetro independiente.
2. **Identidad/branding — wordmark**: se usa el **wordmark "MASTER"** (vectorial
   derivado de Wallpoet, `ui/MasterWordmark.kt`), con la "M" y "E" en color de
   acento y "AST" y "R" en color de texto. El pipeline original de TIMES vive en
   `branding/wordmark/` como referencia; el de Athlete (Neuropol) en
   `branding/wordmark/legacy-athlete/`. Ícono de launcher y paleta de acento:
   **definidos** (ver Fase 6).
3. **Idioma**: **solo inglés** (se descarta el bilingüe; `Strings` se simplifica a EN).
4. **Alcance del MVP**: **todo**. Entran todas las features actuales de Athlete
   (ver sección 5 de `hallazgos-athlete.md`), más los pendientes de `to-do.md`.

## Fases de extracción

### Fase 0 — Scaffold (HECHA)
- [x] Repo `athletic` + git init.
- [x] Documentación de hallazgos + hoja de ruta.
- [x] Traer wordmark TIMES completo (pipeline + `TimesWordmark.kt` + fuente) a
      `branding/wordmark/` como referencia. El wordmark final usado en la app es
      "MASTER" (`ui/MasterWordmark.kt`), ver Fase 6.
- [x] Definir decisiones de producto (arriba).

### Fase 1 — Proyecto Android base (HECHA — compila)
- [x] Estructura Gradle (sin `gradlew`, Gradle 9.4.1 cacheado, JAVA_HOME jbr; proxy
      JFrog + token de `~/.npmrc` en `settings.gradle.kts`). `verify-compile.ps1` incluido.
- [x] `build.gradle.kts` con Compose + Material3 (mismas versiones que mini-timer).
- [x] Paquete **`com.athletic`** + `AndroidManifest` (permisos foreground service
      specialUse, notificaciones, vibrate, wake_lock). Ícono de launcher adaptativo
      agregado en la Fase 6.
- [x] Tema propio (`Theme.kt` con `AppColors`/`AthleticTheme`, `Dims`, `Type.kt`).
- [x] `MainActivity` placeholder ("ATHLETIC") — verificado con `compileReleaseKotlin`
      (BUILD SUCCESSFUL). Acento e idioma: placeholder rojo, textos en inglés.

### Fase 2 — Núcleo de dominio y datos (HECHA — compila)
- [x] `model/Workout.kt` (Training/Workout/Variant/Exercise/WorkSet/StageConfig +
      enums + helpers + `ExerciseDef`/`SessionLog`), `model/PlayerStep.kt` (`PlayerStep`/`StepKind`).
- [x] `data/WorkoutStore.kt`, `AthleteDefaults.kt`, `ExerciseCatalog.kt`, `ExerciseIcons.kt`.
- [x] `PlayerBus`/`PlayerCommand`/`PlayerSnapshot`.
- Nota: copia fiel ajustando paquete a `com.athletic`. El catálogo sigue bilingüe
  ES/EN (se poda a solo-EN en Fase 5). Verificado con `compileReleaseKotlin`.

### Fase 3 — Servicio y alarma (HECHA — compila)
- [x] `notify/WorkoutPlayerService.kt` (máquina de pasos, foreground specialUse,
      restauración tras muerte del proceso, rotación idempotente). `WorkoutAlarm.kt`
      fue eliminado; el cue de transición vive dentro del servicio (`alarmCue(step)`).
- [x] Dependencias traídas para el motor: `audio/AlarmPlayer.kt` (copia fiel,
      usado como motor de beeps del player vía `beepTone()` + previews del editor),
      `model/Settings.kt` REDUCIDO (`AppConfig` general+athlete, `AlarmConfig` quedó
      como código muerto tras eliminar la card de alarma de Ajustes) y
      `data/SettingsStore.kt` MÍNIMO (base para la Fase 5). Drawables de notificación
      copiados (`ic_stat_timer`, `ic_notif_play/pause/close`). Servicio registrado en el manifest.
- Podas al copiar: se quitó `BackupManager` (fuera de alcance) y se reemplazó `I18n`
  por textos en inglés (decisión solo-EN). Sin tocar la lógica del motor.
- [ ] Pendiente de VERIFICACIÓN en dispositivo (segundo plano, pantalla apagada,
      muerte de proceso, rotación) — ver "Pendientes".

### Fase 4 — UI (copia + poda de dependencias compartidas) (HECHA — compila)
- [x] Componentes compartidos que usa Athlete: `ui/CommonComponents.kt`
      (`AppPrimaryButton`/`AppOutlineButton`/`AppStepButton`/`AppStepper`/`SwitchRow`),
      `ui/AnimatedGlowBorder.kt` (+`glowColors`), `util/Format.kt` y `ui/WheelTimePicker.kt`
      (extraído de `TimerApp`, sin `JetBrainsMono`).
- [x] `ui/athlete/*` completo (8 archivos: router `AthleteScreen`, editores de
      training/workout/variant/exercise, `ChooseExerciseScreen`, `PlayerScreen`,
      `AthleteComponents`) + `AthleteViewModel`. Paquete `com.athletic`.
- [x] i18n en inglés (`com.athletic.i18n.Strings`, instancia única `EN`);
      `AthleteViewModel.lang()` fija `"en"` (English-only).
- [x] Shell propio: `MainActivity` monta `AthleteScreen` con `Scaffold`+`TopAppBar`,
      back contextual (`goBack`) y `BackHandler`. Verificado con `compileReleaseKotlin`.
- [ ] Pendiente: verificación en dispositivo (junto con Fase 3).

### Fase 5 — i18n y ajustes (poda) (HECHA — compila)
- [x] i18n PODADO: `com.athletic.i18n.Strings` quedó **solo en inglés** y solo con las
      claves que usa la UI de Athlete + Ajustes (se descartaron Timer/Clock/Backup/tabs).
      Set de claves derivado de los usos reales de `t.` en el código.
- [x] `SettingsStore` reducido (acento, tema, `padPlayerClock`; sin selector de idioma,
      sin alarma global del player).
- [x] Pantalla de Ajustes (`ui/settings/SettingsScreen.kt`) MD3:
      - General: selector de acento (`ACCENT_COLORS`, 6 colores) + modo de tema
        (Auto/Light/Dark). Los acentos `PINK_ACCENT` y `STITCH_ACCENT` bloquean el
        tema (fuerzan un modo fijo).
      - Player: `padPlayerClock`.
      - `SettingsViewModel` persiste al vuelo y dirige acento/tema del `AthleticTheme`.
      - Acceso: engranaje en la barra superior (solo en la raíz).
- Nota: la card "Alarm & sound" (volumen, vibración, ignorar silencio, audífonos)
  fue **eliminada**. El control de volumen por etapa se removió (los beeps se
  normalizan a -14 LUFS en el archivo). El selector de tono de beep vive en el
  exercise editor (`BeepSoundPickerDialog`).
- Verificado con `compileReleaseKotlin`.

### Fase 6 — Branding (HECHA — compila)
- [x] Wordmark **"MASTER"** integrado en la barra superior (`ui/MasterWordmark.kt`,
      fuente `wallpoet_athletic`). Se muestra en la raíz; la "M" y "E" resaltan en
      color de acento, "AST" y "R" en color de texto. Niveles internos usan título
      contextual. (El `TimesWordmark.kt` del pipeline de branding NO se portó a la app.)
- [x] Ícono de launcher adaptativo (aprobado por preview): fondo NEGRO, hexágono negro
      con contorno rojo fino (`strokeWidth` 1) rotado -12° (como el ícono legacy de la
      pestaña Athlete) y la "M" de Wallpoet en ROJO SÓLIDO (mismo glifo del wordmark,
      path `M_D` reescalado al lienzo 108). Recursos:
      `drawable/ic_launcher_background|foreground|monochrome.xml`,
      `mipmap-anydpi-v26/ic_launcher(_round).xml`; cableado en el manifest
      (`android:icon`/`roundIcon`). Monocromo para íconos temáticos (Android 13+).
- [x] Splash (Android 12+): `values-v31/themes.xml` con fondo negro e ícono
      `drawable/ic_splash_icon.xml` que coincide con el launcher (hexágono con contorno
      rojo + M roja sólida).
- [x] Paleta de acento: `ACCENT_COLORS` (6 colores seleccionables en Ajustes); el acento
      de MARCA (ícono/splash) es rojo `#FF5252` (= `DEFAULT_ACCENT`).
- Nota: al ser ícono nuevo, para probarlo conviene REINSTALAR (el launcher suele
  cachear el ícono); actualizar encima puede no refrescarlo.

### Fase 7 — Pendientes de producto (HECHA — compila)
- [x] Historial de sesiones: `AthleteViewModel` (estado `sessions` + open/close/
      refresh/delete/clear) y `ui/athlete/HistoryScreen.kt` (MD3): lista agrupada por
      día (Today/Yesterday), borrado individual y limpieza total con confirmación.
      Integrado en el router (`AthleteScreen`) y en `MainActivity` (back, título
      contextual, acción de historial en la barra superior). Lee `loadSessions()`;
      el registro ya lo hace `WorkoutPlayerService.recordSession()`.
- [x] Drag-reorder (long-press) sin librerías: `ui/Reorderable.kt`
      (`DragDropState`/`rememberDragDropState`/`Modifier.dragContainer`/`DraggableItem`,
      items arrastrables filtrados por `contentType = ReorderableContentType`).
      Aplicado a trainings (`AthleteScreen`), workouts (`TrainingEditorScreen`),
      variantes (`VariantListScreen`) y ejercicios (`WorkoutEditorScreen`), cableado a
      `moveTraining`/`moveWorkout`/`moveVariant`/`moveExercise`.
- [x] Selector de tono de beep in-app (`BeepSoundPickerDialog` en el exercise editor):
      lista tonos de notificación del sistema (`RingtoneManager.TYPE_NOTIFICATION`) con
      preview al seleccionar. Guarda `beepSoundUri`/`beepSoundName` en `StageConfig`.
- [ ] Pendiente: verificación en dispositivo (historial y arrastre).

### Fase 8 — Build y verificación
- [x] Compilar debug (`:app:assembleDebug`) OK — BUILD SUCCESSFUL, APK `app-debug.apk` (~15 MB).
- [x] **Smoke test en dispositivo** (Samsung SM-S948B, One UI; ADB Wi-Fi). Verificado en pantalla:
      shell raíz (wordmark MASTER + trainings sembrados Master/Friki Niki), detalle de training
      (workouts, iconos de ejercicio, variantes rotativas), arranque del player (máquina de pasos:
      ejercicio + reps + Done) y modo inmersivo (tap oculta/muestra chrome).
- [x] Compilar release (`compileReleaseKotlin`/`assembleRelease`) con minify+shrink; ProGuard OK.
- [x] Generar APK release + versionado. Hay 110+ releases publicadas en GitHub (última v1.0.110).
- [x] Self-update in-app + auto-upload a GitHub Releases (`update/UpdateChecker.kt`,
      `update/UpdateDialog.kt`, barra inferior no-modal estilo Telegram, `update.json` en la raíz).
- [ ] Verificación funcional manual completa: avance de pasos (Done), countdown de ejercicios
      cronometrados (p. ej. Cardio), pausa/reanudar, segundo plano y muerte de proceso, registro en
      Historial y drag-reorder. (El `input tap` por ADB no acierta bien botones pegados a la barra de
      navegación; se completa manualmente.)

### Post-Fase 8 — Features agregadas tras el carve-out (HECHAS)
- [x] Weekly calendar con swipe + slide animation (`WeekCalendar` en `AthleteScreen`):
      muestra los días de la semana con las sesiones completadas marcadas; navegación
      entre semanas por swipe horizontal (commit `7699836`).
- [x] MiniPlayer flotante en la lista de trainings (`MiniPlayer` en `AthleteScreen`):
      barra inferior con el training en reproducción, tap abre el player a pantalla completa.
- [x] Nuevo ícono de launcher (commit `ac91836`): hexágono con contorno rojo + M de Wallpoet roja.

## Pendientes (consolidado)

- [ ] **Verificación funcional manual completa en dispositivo**: avance de pasos,
      countdown cronometrado, pausa/reanudar, segundo plano y muerte de proceso,
      registro en Historial, drag-reorder, weekly calendar, mini-player, self-update.
- [ ] **Limpieza de código muerto**: `AlarmConfig`, `VIBRATION_PATTERNS`,
      `VibrationPattern`, `HEADSET_ONLY`, `SPEAKER_AND_HEADSET`, `AlarmSound` (parcial),
      y los métodos `AlarmPlayer.start(config)` / `AlarmPlayer.previewVolume(config)`
      no se usan tras eliminar la card "Alarm & sound" de Ajustes. Solo `beepTone()` y
      `stopPreview()` están vivos. `AlarmSound` sí se usa en `BeepSoundPickerDialog`.
- [ ] **`branding/icons.html` sin trackear**: preview HTML del launcher icon
      (herramienta de diseño). Decidir si se commitea o se ignora.
- [ ] **Rebrand del texto del wordmark (opcional)**: decidir si se mantiene "MASTER"
      o se regenera con los glifos de "ATHLETIC" usando el pipeline
      `branding/wordmark/tools/` (mismas técnicas: puentear cortes de stencil, `\` como
      I, glifos rotados, etc.). Ver `branding/wordmark/README.md` para el flujo detallado.

## Riesgos y notas

- El punto más delicado del carve-out es **partir `i18n.Strings`** sin perder claves.
- El servicio y la rotación son la lógica de mayor valor: **no tocar** su
  comportamiento durante la copia; solo ajustar paquete/imports.
- Persistencia: es un **clean break** (datos nuevos); no migrar prefs de mini-timer.
