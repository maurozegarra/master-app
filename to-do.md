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
