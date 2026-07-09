# Hallazgos: inventario de la sección Athlete (mini-timer)

Inventario de lo que YA existe y funciona en `mini-timer`, base del carve-out.
Rutas relativas a `C:\Users\mzegarra_ide\code\mini-timer\app\src\main\java\com\minitimer`.

## 1. Modelo de dominio (`model/Workout.kt`)

Jerarquía de 3 niveles: **Training > Workout > Exercise**.

- `Training{ id, name, workouts:List<Workout>, createdAt, updatedAt }` — lo que se
  ejecuta de corrido en el player.
- `Workout{ id, name, exercises:List<Exercise>, rotating, rotationIndex, variants:List<WorkoutVariant> }`
  — bloque/agrupador. Si `rotating`, reproduce UNA de sus variantes según
  `rotationIndex`.
- `WorkoutVariant{ id, name, exercises }` — conjunto con nombre (ej. "Lower", "Running").
- `Exercise{ id, exerciseId, name, note, prepareSec, sets, workMode, workValue,
  restSec, restSkipOnLastSet, cooldownSec, weightType, barWeight, setList,
  prepareCfg, workCfg, restCfg, cooldownCfg }`.
- `WorkSet{ reps, weight }` — una serie (peso "crudo", su significado depende de `weightType`).
- `StageConfig{ color, display, alarm, finalCount, confirm }` — config por etapa.
- Enums: `WorkMode{TIME,REPS}`, `DisplayMode{COUNTDOWN,STATIC,COUNTUP}`,
  `ConfirmMode{AUTO,MANUAL}`, `WeightType{NONE,TOTAL,BARBELL,DUMBBELL}`.
- `ExerciseDef{ id, name, custom }` — definición de catálogo.
- `SessionLog{ id, trainingId, trainingName, completedAt }` — registro de sesión.
- Helpers: `activeVariant()`, `activeExercises()`, `activeName()`, `hasContent()`,
  `setAt(i)`, `weightTotal(set)`, `isWeighted`.

Colores por defecto de etapa: PREP naranja `0xFFE2641E`, WORK rojo `0xFFC0392B`,
REST azul `0xFF1565C0`, COOLDOWN plomo `0xFF455A64`.

## 2. Lógica / estado (`AthleteViewModel.kt`)

- Lista de `trainings` persistida + `customExercises` + navegación por estado
  (draft en edición del árbol completo hasta guardar).
- CRUD de Training: nuevo, editar, guardar (`canSaveTraining`), eliminar, mover,
  duplicar.
- CRUD de Workout: agregar, abrir, cerrar, eliminar, duplicar, mover.
- Rotativos: `makeWorkoutRotating`/`makeWorkoutSimple`, y variantes
  (`addVariant`, `openVariant`, `deleteVariant`, `duplicateVariant`, `moveVariant`).
- CRUD de Exercise dentro del contenedor activo (workout o variante):
  `pickExercise`, `openExercise`, `saveExercise`, `deleteExercise`,
  `duplicateExercise`, `moveExercise`.
- Catálogo: `catalog()` (base bilingüe + propios ordenados), `addCustomExercise`.
- Construcción de pasos del player: `buildSteps(training)` -> `List<PlayerStep>`.
- Player state: `openPlayer`, `startPlayerRun`, `pausePlayer`, `resumePlayer`,
  `nextStep`, `closePlayer`; OSD (`playerControlsVisible`, auto-ocultado);
  feedback de peso (`recordFeedback`, `weightSuggestions`).

## 3. Persistencia (`data/WorkoutStore.kt`)

- SharedPreferences `"athlete"` + JSON. Claves: `trainings_json`,
  `custom_exercises_json`, `sessions_json`, + flags de migración
  (`friki_seeded`, `master_v2_seeded`).
- Serialización completa de training/workout/variant/exercise/stage/set.
- Historial: `saveSessions`/`loadSessions`.
- Seeds de fábrica en `data/AthleteDefaults.kt` (trainings "Master" y "Friki Niki").
- Catálogo en `data/ExerciseCatalog.kt` + iconografía en `data/ExerciseIcons.kt`.

## 4. Motor en segundo plano (`notify/WorkoutPlayerService.kt` + `WorkoutAlarm`)

- Servicio foreground (`specialUse`) que sobrevive a segundo plano, pantalla
  apagada y cierre de la Activity.
- Máquina de estados: cuenta regresiva + auto-avance, o avance manual.
- Cue de alarma en transiciones y al terminar (`WorkoutAlarm`, ~2 s).
- Notificación con paso/cuenta y acciones (Pausar/Reanudar, Siguiente/Hecho, Cerrar).
- Restauración tras muerte del proceso (prefs `athlete_player`).
- Rotación **independiente por workout** con idempotencia (`advancedWorkouts`).
- Registro de sesión al completar (`recordSession`).
- Canal `mini_timer_workout_v1` (IMPORTANCE_LOW, sin sonido/vibración propios).
- Comunicación con la UI vía `PlayerBus`/`PlayerCommand`/`PlayerSnapshot` (paquete raíz).

## 5. UI (`ui/athlete/*`)

- `AthleteScreen.kt` — router por estado + lista de Trainings (tarjeta con play,
  menú Editar/Duplicar/Eliminar, estado vacío).
- `TrainingEditorScreen.kt` — nombre + workouts + menú (rotativo/simple, duplicar, eliminar).
- `VariantListScreen.kt` — editor de workout rotativo (variantes).
- `WorkoutEditorScreen.kt` — nombre + ejercicios (menú duplicar/eliminar).
- `ChooseExerciseScreen.kt` — catálogo con buscador + crear ejercicio propio.
- `ExerciseEditorScreen.kt` — pestañas Simple/Advanced; peso por serie; config por etapa.
- `PlayerScreen.kt` — vistas Preview (tarjetas colapsables + timeline), Running
  (fondo por fase, glow border, progreso, reloj/reps, feedback, controles OSD) y
  Finished (felicitación + sugerencias de peso).
- `AthleteComponents.kt` — `STAGE_COLORS`, steppers, `ExerciseGlyph`, `DurationStepper`, etc.

## 6. Acoplamiento con el resto de mini-timer (a resolver en el carve-out)

**Propio y aislado** (se lleva tal cual): todo lo de las secciones 1–5.

**Compartido, genérico y copiable**:
- Tema `ui.theme.AppTheme` / `Theme.kt` / `Dims`.
- Componentes UI: `AppStepper`, `AppStepButton`, `WheelTimePicker`, `SwitchRow`,
  `AppPrimaryButton`, `AppOutlineButton`, `AnimatedGlowBorder`/`glowColors`.
- Helpers `util`: `formatRemaining`, `formatPlayerClock`, `pad2`, `formatClock`.

**Acoplamiento incómodo (requiere trabajo)**:
- `i18n.Strings` es un **monolito** que mezcla strings de Timer + Athlete + Clock +
  Water. Hay que extraer solo lo de Athlete.
- `data.SettingsStore` — Athlete usa un subconjunto: `general.language`, acento,
  `athlete.padPlayerClock`. Hay que podarlo.
- `MainActivity` + bottom nav + `TimerViewModel` son el shell de mini-timer (no se
  llevan; se reemplazan por un shell propio de Athletic).

## 7. Branding / wordmark

En mini-timer coexistían dos "wordmarks":

- **TIMES** (sección Times): wordmark **custom vectorial** derivado a mano de la
  fuente **Wallpoet** mediante un pipeline Python offline (`tools/wallpoet_extract.py`
  + `tools/wordmark_build.py`), con el resultado incrustado en `ui/TimesWordmark.kt`.
  **Es el wordmark elegido para Athletic** (ver `branding/wordmark/README.md`).
- **ATHLETE** (sección Athlete): solo la fuente **Neuropol** (`neuropol_nova_regular.ttf`)
  para el título, más un ícono hexagonal con "M" en Wallpoet (`AthleteTabIcon`).
  Se conserva como referencia en `branding/wordmark/legacy-athlete/`.
