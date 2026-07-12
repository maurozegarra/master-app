# To-Do / Pendientes — Athletic

Al implementar cualquier elemento de UI, validar íconos/botones/componentes contra
Material Design 3 antes de proponerlos.

## Branding

### Reubicar wordmark  ← (solicitado)
- **Wordmark elegido = TIMES** (custom, vectorial). Assets y pipeline completos en
  `branding/wordmark/` (`TimesWordmark.kt`, `tools/`, `wallpoet_regular.ttf`,
  previews). El de Athlete (Neuropol) queda como referencia en
  `branding/wordmark/legacy-athlete/`.
- Falta **integrarlo en el proyecto Android**: portar `TimesWordmark.kt` al
  paquete/UI de Athletic y usarlo en el app bar.
- **Rebrand del texto (opcional)**: decidir si se mantiene "TIMES" o se regenera el
  wordmark con los glifos de "ATHLETIC" usando el pipeline `branding/wordmark/tools/`
  (mismas técnicas: puentear cortes de stencil, `\` como I, glifos rotados, etc.).
  Ver `branding/wordmark/README.md` para el flujo detallado.

### Ícono de launcher  ← (solicitado)
- Diseñar/definir el ícono del launcher de Athletic (aún por definir). En mini-timer
  no había ícono personalizado (usaba el del sistema).
- Entregar los `mipmap` (adaptive icon: foreground + background + monochrome para
  temas dinámicos) en las densidades correspondientes.
- Definir también la etiqueta visible del app (`android:label`).

### Paleta de acento  ← (solicitado)
- Definir el color de **acento** de Athletic (el que resalta la "M" del wordmark,
  botones primarios, progreso del player, etc.).
- Decidir si el acento es fijo o seleccionable por el usuario en Ajustes.
- Alinear con Material Design 3 (color scheme / tonal palette) y con los colores de
  etapa del player (PREP/WORK/REST/COOLDOWN) para evitar choques visuales.

## Funcionalidad (heredada de Athlete, aún sin UI)

### Volumen del beep por etapa — PENDIENTE
- La UI ya existe: `Stepper` de "Beep volume" por etapa en `ExerciseEditorScreen`
  (0-100%, step 5%), con progressive disclosure cuando `finalCount > 0`.
- El valor se persiste correctamente en `StageConfig.beepVolume` y llega al servicio
  (`PlayerStep.beepVolume`, verificado con logs ADB).
- **Problema**: `MediaPlayer.setVolume()` no tiene efecto real en el dispositivo
  (Samsung S24). El beep se reproduce a volumen del stream `USAGE_MEDIA` ignorando
  el valor de `setVolume()`. El control del usuario solo sigue el volumen del
  sistema, no el porcentaje configurado por etapa.
- **Soluciones a evaluar**:
  - (a) `VolumeShaper` (API 26+): aplica curva de volumen directamente en el
    AudioTrack subyacente del MediaPlayer.
  - (b) `AudioTrack` directo: decodificar OGG a PCM y atenuar muestras manualmente.
  - (c) ExoPlayer/Media3: `SimpleExoPlayer.setVolume()` usa `AudioTrack.setVolume()`
    que sí funciona (agrega dependencia).
  - (d) Manipular `STREAM_MUSIC` temporalmente: guardar/restaurar volumen del
    stream alrededor del beep (afecta a otras apps).

### Cue de transición — HECHO (parcial) / refactor
- **Antes**: `alarmCue()` usaba `WorkoutAlarm` → `AlarmPlayer.start()` con la
  config global de alarma de Ajustes (`SettingsStore.athlete.alarm`). Sonaba
  siempre, ignorando `StageConfig.alarm`.
- **Ahora**: `alarmCue(step)` respeta `step.alarm` (switch por etapa) y usa
  `step.beepSoundUri` / `step.beepVolume` con `beep_work.ogg` como sonido default.
  No suena al finalizar el workout (no hay etapa destino).
- **Huérfano**: `WorkoutAlarm.kt` y la config global de alarma de Ajustes
  (`SettingsScreen → Alarm & sound`) ya no se usan en el player. Quedan activos
  solo para el temporizador (timer tab). **Revisar**: la lógica de manejo de
  sonido de `AlarmPlayer` (volumen perceptual, audio focus, ducking, headset
  routing) puede servir para un proyecto de alarma separado. No eliminar hasta
  revisar.

### Historial de sesiones — HECHO (Fase 7)
- Se registra y persiste al completar un training (`WorkoutPlayerService.recordSession()`
  -> `WorkoutStore.saveSessions()`, `SessionLog{id, trainingId, trainingName, completedAt}`).
- UI: `ui/athlete/HistoryScreen.kt` lee `loadSessions()` y muestra los trainings
  completados agrupados por día (Today/Yesterday/fecha), con borrado individual y
  limpieza total con confirmación. Acceso desde la barra superior (raíz).
- Base para streak/dashboard (futuro).

### Reordenar por arrastre (drag-and-drop) — HECHO (Fase 7)
- `moveTraining`/`moveWorkout`/`moveVariant`/`moveExercise` conectados a UI de arrastre
  (long-press) vía `ui/Reorderable.kt`. Aplicado a la lista de trainings, editor de
  Training (workouts), lista de variantes y editor de Workout (ejercicios).

### Selector de tono de alarma — HECHO (parcial)
- `Ajustes → Alarm & sound → Alarm sound` abre el picker NATIVO filtrado a tonos de
  **notificación** (`RingtoneManager.ACTION_RINGTONE_PICKER`, `TYPE_NOTIFICATION`).
  Guarda `soundUri`/`soundName` en `AlarmConfig` (`SettingsViewModel.setSound`) y hace
  un preview con `previewVolume()` al elegir. El player ya usa `config.soundUri`
  (`AlarmPlayer.start`).
- **Pendiente — preview de tonos in-app**: el picker nativo previsualiza al tocar cada
  tono, pero conviene un preview propio consistente con "lo que pruebas es lo que suena"
  (mismo `USAGE_ALARM`, volumen perceptual y ducking vía `AlarmPlayer.previewTone`).
  Opciones: (a) botón "play" junto a la fila del tono que reproduzca el seleccionado; o
  (b) lista de tonos in-app (cursor `RingtoneManager` TYPE_NOTIFICATION) con selección +
  preview, sin salir de la app. Evaluar también el fallback por defecto: hoy si
  `soundUri` es null cae a `TYPE_ALARM`; decidir si el default debe ser notificación.

## Extracción (ver docs/hoja-de-ruta.md)

- Proyecto Android base (Gradle sin `gradlew`, Gradle 9.4.1 cacheado, JAVA_HOME jbr).
- Copiar núcleo (modelo, datos, servicio, PlayerBus) y UI de `ui/athlete/*`.
- Partir el monolito `i18n.Strings` y quedarse solo con las claves de Athlete.
- Podar `SettingsStore` (idioma, acento, `padPlayerClock`).
- Shell propio (Activity + navegación) reemplazando `MainActivity`/bottom nav.

## Sugerencias extra (propuestas, a validar)

1. **Nombre de paquete y app**: fijar `com.athletic` (o similar) y el nombre visible
   antes de copiar código, para no renombrar dos veces.
2. **Clean break de datos**: no migrar prefs de mini-timer; empezar con persistencia
   nueva y, si acaso, un importador opcional más adelante.
3. **Respaldo/exportación**: mini-timer tenía backup por SAF (`BackupManager`).
   Evaluar si Athletic necesita export/import de trainings (JSON) desde el día 1.
4. **Selector de tema claro/oscuro** explícito en Ajustes (mini-timer dependía del
   sistema); decidir si Athletic ofrece Auto/Claro/Oscuro.
5. **Test del motor**: agregar pruebas de la máquina de pasos y de la rotación
   idempotente (`advancedWorkouts`) al portar el servicio; es la lógica de mayor
   valor y la más fácil de romper.
6. **Catálogo de ejercicios**: revisar/expandir `ExerciseCatalog` y su iconografía
   (`ExerciseIcons`) pensando en el app independiente, no solo en el seed "Master".
7. **Animaciones de ejercicio**: el diseño reservaba un placeholder para animación
   por ejercicio; definir si entra en el roadmap (sin depender de libs de red).
8. **Onboarding / estado vacío**: al ser app independiente, cuidar el primer arranque
   (seed de ejemplos vs vacío + tutorial breve).
9. **Accesibilidad**: `contentDescription` en íconos del player y áreas táctiles
   >=48dp (varios botones del player usan Box clicable).
10. **Wear/pantalla siempre encendida**: el player a pantalla completa podría
    beneficiarse de keep-screen-on durante la corrida (evaluar).
