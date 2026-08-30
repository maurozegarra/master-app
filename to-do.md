# To-Do - MASTER

> Generado automaticamente por `forge-status.ps1` desde `docs/forge-todo.json`.
> No editar directamente; actualizar el JSON y regenerar con `.\forge-status.ps1`.
> Convencion de commits: `feat: TD-XXX ...` / `fix: TD-XXX ...`.

Progreso: **36 / 54** hechos, 18 pendientes.

## Pendientes

### Feature

- [ ] **TD-053** Snapshot automatico de datos a almacenamiento compartido
  - Complemento de TD-009: el export manual solo salva si el usuario se acordo de correrlo. Escribir un snapshot JSON (trainings + sesiones) en almacenamiento compartido via MediaStore (Documents/MASTER/), reescrito en cada persist() o al terminar una sesion. Los archivos en Documents/ sobreviven a la desinstalacion, a diferencia de SharedPreferences y de Android/data/. Con eso una reinstalacion deja de ser destructiva sin depender de que el usuario recuerde exportar. Mantener las ultimas N copias con fecha en el nombre.
- [ ] **TD-051** Add from existing: reutilizar un workout de otro training
  - En el editor de training, un segundo AddButton 'Add from existing' abre una pantalla nueva (ChooseWorkoutScreen, calcada de ChooseExerciseScreen) que lista los workouts de los demas trainings agrupados por training de origen, con buscador. Al elegir uno se inserta una copia profunda con ids nuevos (Workout.deepCopy de TD-050) al final del draft y se abre para editar. ORDEN: primero los trainings con sesiones registradas, por fecha de la ultima sesion descendente (vm.sessions ya viene ordenada por completedAt desc); despues los nunca entrenados, por updatedAt desc. El criterio es el historial y no updatedAt porque la lista acumula trainings de prueba que nunca se ejercitaron y no deben desplazar al que si se usa. El header de cada grupo muestra cuando se entreno reutilizando dayLabel() de HistoryScreen, y 'Never trained' si no hay sesiones. Excluye el training en edicion y los workouts sin contenido (hasContent). Wiring: flag choosingWorkout en el ViewModel siguiendo el patron de choosingExercise, mas MainActivity (when de pantallas, titleFor, back handler) y strings chooseWorkout / addFromExisting / neverTrained. La funcion de orden va en model/ como funcion pura sobre List<Training> + List<SessionLog> para testearla sin ViewModel.
- [ ] **TD-044** Feedback haptico en el player (skip, check, pause)
  - Vibracion corta al hacer skip, check o pause en el player. Confirmacion tactil sin necesidad de mirar la pantalla. Usar VibrationEffect.createOneShot con duracion corta (~50ms) para no ser intrusivo. Solo en acciones del usuario, no en transiciones automaticas.
- [ ] **TD-043** Preview del siguiente ejercicio en REST
  - Durante los steps de REST, mostrar el glyph/icon del proximo ejercicio mas grande y prominente, no solo un label de texto. Ayuda a prepararse mentalmente para el siguiente ejercicio.
- [ ] **TD-042** Resumen de stats en Historial
  - Al abrir History, mostrar un header compacto con: total de sesiones, tiempo total acumulado, ejercicio mas frecuente. Datos derivados de vm.sessions.
- [ ] **TD-041** Ring de progreso en el player
  - Ademas de la barra lineal de progreso, un ring circular alrededor del clock que muestra cuantos del step actual ha transcurrido. Mas visual y moderno. Calcular fraccion con playerRemainingMs y la duracion total del step.
- [ ] **TD-040** Duracion estimada en TrainingCard
  - Mostrar duracion estimada (~12 min) junto a '3 workouts · 8 exercises' en la TrainingCard. Calcular sumando duraciones de todos los steps (PREP + WORK + REST + COOLDOWN) del training.
- [ ] **TD-039** Swipe-to-delete en TrainingCards
  - Deslizar la card a la izquierda para borrar (con dialogo de confirmacion), en vez de ir al menu de 3 puntos. Mas rapido y natural en movil. Usar SwipeToDismissBox de Material 3 con confirmacion via AlertDialog.
- [ ] **TD-014** Keep-screen-on durante la corrida (opcional)
  - El player a pantalla completa podria beneficiarse de keep-screen-on
- [ ] **TD-013** Accesibilidad (opcional)
  - contentDescription en iconos del player y areas tactiles >=48dp
- [ ] **TD-012** Onboarding / estado vacio (opcional)
  - Cuidar el primer arranque: seed de ejemplos vs vacio + tutorial breve
- [ ] **TD-011** Animaciones de ejercicio (opcional)
  - Placeholder para animacion por ejercicio; definir si entra en el roadmap
- [ ] **TD-010** Catalogo de ejercicios expandido (opcional)
  - Revisar/expandir ExerciseCatalog y ExerciseIcons pensando en el app independiente
- [ ] **TD-009** Export/import de datos a JSON (PRIORITARIO)
  - Ya no es opcional. El 29-ago-2026 una reinstalacion por firma incompatible borro trainings e historial del dispositivo y no hubo forma de recuperarlos: el backup de Google se sobrescribio con la instalacion limpia seis segundos despues, y la copia de Samsung Cloud del 24-ago tampoco los tenia. Sin export no hay red de seguridad. Alcance: exportar trainings + sesiones + ejercicios custom a un unico JSON via SAF (ACTION_CREATE_DOCUMENT), e importar con ACTION_OPEN_DOCUMENT reemplazando todo previa confirmacion. Reutilizar la serializacion que ya existe en WorkoutStore y SessionJson. Entrada en la pantalla de Settings.

### Fix

- [ ] **TD-052** build-debug.ps1 no debe desinstalar automaticamente
  - El script tenia un fallback que, si el install fallaba, corria 'adb uninstall' y reinstalaba. El 29-ago-2026 eso destruyo trainings e historial: la PC nueva firmaba con otro debug.keystore, el install fallo por INSTALL_FAILED_UPDATE_INCOMPATIBLE y el script desinstalo sin preguntar. Cambio: capturar la salida del install, y ante fallo abortar explicando la causa probable (firma por maquina), como comparar huellas y que desinstalar borra los datos de forma irreversible. La decision de borrar es del usuario, no del script.
- [ ] **TD-050** Fix preventivo: la copia de un workout no clona sus variantes
  - duplicateWorkout y duplicateTraining hacen src.copy(id=newId(), exercises=...) sin mencionar variants, y al ser data class la copia arrastra la misma lista: un workout rotativo duplicado conserva los ids de sus WorkoutVariant y de los Exercise dentro de ellas. Hoy no se manifiesta porque todos los lookups estan acotados por workout (editingVariant resuelve dentro de editingWorkout), StepEngine no usa esos ids y las key de Compose son por lista. Es preventivo: TD-033 planea navegacion por ruta con SavedStateHandle, que direccionaria una variante por id sin el contexto del workout, y ahi dos ids iguales resuelven al objeto equivocado en silencio. Solucion: extension Workout.deepCopy(newId: () -> Long) en model/Workout.kt que reasigna ids de workout, exercises, variants y exercises de cada variante, y resetea rotationIndex a 0; los dos duplicados existentes pasan a usarla conservando su duplicateName(). Test de invariante en WorkoutTest (no de reproduccion: no hay fallo observable hoy).

### Mantenimiento

- [ ] **TD-054** Regla: verificar firma antes de instalar y nunca desinstalar sin autorizacion
  - Documentar en AGENTS.md, en Reglas de oro: (1) antes de instalar en un dispositivo que ya tiene el app, verificar que la firma del APK coincide (apksigner verify --print-certs vs keytool sobre ~/.android/debug.keystore); (2) NUNCA correr adb uninstall sin autorizacion explicita del usuario, porque borra trainings e historial; (3) el debug.keystore es por maquina y no esta en el repo: al cambiar de PC hay que copiarlo, o todos los releases publicados dejan de poder instalarse encima; (4) procedimiento de emergencia si se borran datos: 'adb shell bmgr enabled false' ANTES de abrir el app, para que el backup automatico no suba la instalacion limpia encima de la copia buena en la nube (fue lo que impidio recuperar los datos el 29-ago-2026).
- [ ] **TD-033** Arquitectura: Repository interfaces + MVI + Navigation + Testing
  - Fases 2-5 del plan en docs/plan-arquitectura.md. (2) Repository interfaces: TrainingRepository, SessionRepository, SettingsRepository como interfaces, WorkoutStore y SettingsStore las implementan, ViewModels reciben interfaces por constructor. (3) MVI: MasterState/MasterAction/MasterEvent, StateFlow + Channel, onAction() en vez de metodos sueltos, composables reciben state + onAction. (4) Compose Navigation type-safe con SavedStateHandle, migrar flags de navegacion del ViewModel a rutas. (5) Testing con Turbine + fakes: FakeTrainingRepository, FakeSessionRepository, FakeSettingsRepository, tests del ViewModel. Cada fase deja la app funcional y se ejecuta una a la vez.

## Hechos

### Branding

- [x] **TD-008** Rebrand del texto del wordmark (opcional)

### Bug

- [x] **TD-015** Fix drag-reorder en lista de trainings

### Feature

- [x] **TD-048** Icono de la barra de estado solo en segundo plano (estilo YouTube)
- [x] **TD-037** Tap en dia del calendario abre sheet con sesiones de ese dia
- [x] **TD-035** Atenuar pantalla del player cuando el timer esta en pausa
- [x] **TD-028** Skip por ejercicio en el player
- [x] **TD-021** Historial por ejercicio + sesiones parciales

### Fix

- [x] **TD-049** Probar color naranja en el Now Bar
- [x] **TD-046** DaySheet: mostrar training expandido a nivel workout al abrir
- [x] **TD-045** Aumentar atenuacion de pausa en el player (0.35 -> 0.55)
- [x] **TD-038** Unificar indicadores del calendario a 8dp borde 1dp
- [x] **TD-036** Boton play transparente en TrainingCard
- [x] **TD-034** Confirmacion de delete con nombre en TrainingCard y SessionRow
- [x] **TD-031** Mover Clear history al TopAppBar de History
- [x] **TD-027** Historial agrupado por workout con badge parcial/total
- [x] **TD-026** Fix: WeekCalendar no se actualiza en vivo tras terminar training
- [x] **TD-025** Fix: circulo indicador de WeekCalendar muy pequeno
- [x] **TD-024** Ripple solo en chevron de WorkoutGroupCard
- [x] **TD-023** Fix: tap en card abre editor en vez de preview

### Forge

- [x] **TD-004** CI local integrado en scripts de build
- [x] **TD-003** Tests de dominio (Workout, PlayerStep) - verificado por tests
- [x] **TD-002** Tests de rotacion idempotente - verificado por tests
- [x] **TD-001** Tests del motor de pasos (StepEngine) - verificado por tests

### Mantenimiento

- [x] **TD-047** Documentacion: regla post-build en AGENTS.md + actualizar master-forge.md
- [x] **TD-030** Arquitectura: Koin DI
- [x] **TD-029** Regla: TD es done solo cuando el usuario aprueba tras probar en dispositivo
- [x] **TD-022** AGENTS.md como gatekeeper del harness
- [x] **TD-020** Eliminar referencias a entorno corporativo y mini-timer
- [x] **TD-007** Decidir sobre branding/icons.html sin trackear
- [x] **TD-006** Limpieza de codigo muerto

### Rebrand

- [x] **TD-032** Rebrand residual: eliminar todas las referencias a Athlete
- [x] **TD-019** Actualizar documentacion del rebrand
- [x] **TD-018** Renombrar repo de GitHub y actualizar URLs
- [x] **TD-017** Crear proyecto nuevo com.maurozegarra.master y migrar codigo

### Testing

- [x] **TD-016** Verificar self-update en dispositivo
- [x] **TD-005** Verificacion funcional en dispositivo
