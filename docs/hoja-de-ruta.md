# Hoja de ruta: carve-out de Athlete -> Athletic

Estrategia: **extraer y podar** (no reescribir de cero). Preservar la lógica
probada de mini-timer y reemplazar solo el shell (app, navegación, ajustes, i18n).

## Decisiones de producto (DEFINIDAS)

1. **Timer simple suelto**: NO. El **player cubre todo por ahora**; no se incluye
   un cronómetro independiente.
2. **Identidad/branding — wordmark**: se usa el **wordmark de TIMES** (custom,
   vectorial derivado de Wallpoet), NO el de Athlete (que era solo la fuente
   Neuropol + ícono). Traído completo con su pipeline a `branding/wordmark/`
   (el de Athlete queda como referencia en `branding/wordmark/legacy-athlete/`).
   Pendiente: decidir si el texto se rebrandiza a "ATHLETIC" (regenerar con esos
   glifos) o se mantiene el look TIMES. Ícono de launcher y paleta: por definir.
3. **Idioma**: **solo inglés** (se descarta el bilingüe; `Strings` se simplifica a EN).
4. **Alcance del MVP**: **todo**. Entran todas las features actuales de Athlete
   (ver sección 5 de `hallazgos-athlete.md`), más los pendientes de `to-do.md`.

## Fases de extracción

### Fase 0 — Scaffold (hecho)
- [x] Repo `athletic` + git init.
- [x] Documentación de hallazgos + hoja de ruta.
- [x] Traer wordmark TIMES completo (pipeline + `TimesWordmark.kt` + fuente).
- [x] Definir decisiones de producto (arriba).

### Fase 1 — Proyecto Android base (HECHA — compila)
- [x] Estructura Gradle (sin `gradlew`, Gradle 9.4.1 cacheado, JAVA_HOME jbr; proxy
      JFrog + token de `~/.npmrc` en `settings.gradle.kts`). `verify-compile.ps1` incluido.
- [x] `build.gradle.kts` con Compose + Material3 (mismas versiones que mini-timer).
- [x] Paquete **`com.athletic`** + `AndroidManifest` (permisos foreground service
      specialUse, notificaciones, vibrate, wake_lock). Sin ícono de launcher aún.
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
      restauración tras muerte del proceso, rotación idempotente) + `WorkoutAlarm`.
- [x] Dependencias traídas para el motor: `audio/AlarmPlayer.kt` (copia fiel),
      `model/Settings.kt` REDUCIDO (`AlarmConfig`/vibración/tema/`AppConfig` general+athlete)
      y `data/SettingsStore.kt` MÍNIMO (base para la Fase 5). Drawables de notificación
      copiados (`ic_stat_timer`, `ic_notif_play/pause/close`). Servicio registrado en el manifest.
- Podas al copiar: se quitó `BackupManager` (fuera de alcance) y se reemplazó `I18n`
  por textos en inglés (decisión solo-EN). Sin tocar la lógica del motor.
- [ ] Pendiente de VERIFICACIÓN en dispositivo (segundo plano, pantalla apagada,
      muerte de proceso, rotación) — requiere UI de la Fase 4 para lanzar el player.

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
- [x] `SettingsStore` reducido (acento, tema, `padPlayerClock`, alarma del player;
      sin selector de idioma).
- [x] Pantalla de Ajustes mínima (`ui/settings/SettingsScreen.kt`) MD3:
      - General: selector de acento (`ACCENT_COLORS`) + modo de tema (Auto/Light/Dark).
      - Player: `padPlayerClock`.
      - Alarma: volumen (slider con preview), vibración (switch + patrón + preview),
        ignorar silencio, salida con audífonos.
      - `SettingsViewModel` persiste al vuelo y dirige acento/tema del `AthleticTheme`;
        preview de audio vía `AlarmPlayer` ("lo que pruebas es lo que suena").
      - Acceso: engranaje en la barra superior (solo en la raíz).
- Verificado con `compileReleaseKotlin`. Sin prueba en dispositivo (a pedido).

### Fase 6 — Branding (COMPLETADA — compila; sin prueba en dispositivo)
- [x] Wordmark **TIMES** integrado en la barra superior (`ui/TimesWordmark.kt`,
      copiado de `branding/wordmark/` al paquete `com.athletic.ui`). Se muestra en la
      raíz; la M resalta en color de acento. Niveles internos usan título contextual.
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
- [x] Paleta de acento: se mantiene `ACCENT_COLORS`; el acento de MARCA (ícono/splash)
      es rojo `#FF5252` (= `DEFAULT_ACCENT`). Validado con `processReleaseResources`.
- Nota: al ser ícono nuevo, para probarlo conviene REINSTALAR (el launcher suele
  cachear el ícono); actualizar encima puede no refrescarlo.

### Fase 7 — Pendientes de producto (HECHA — compila; sin prueba en dispositivo)
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
- [ ] Pendiente: verificación en dispositivo (historial y arrastre).

### Fase 8 — Build y verificación
- [ ] Compilar (`compileReleaseKotlin`), corregir imports/paquete.
- [ ] Generar APK release + versionado.
- [ ] Probar en dispositivo (instalación limpia por persistencia nueva).

## Riesgos y notas

- El punto más delicado del carve-out es **partir `i18n.Strings`** sin perder claves.
- El servicio y la rotación son la lógica de mayor valor: **no tocar** su
  comportamiento durante la copia; solo ajustar paquete/imports.
- Persistencia: es un **clean break** (datos nuevos); no migrar prefs de mini-timer.
