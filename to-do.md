# To-Do - MASTER

> Generado automaticamente por `forge-status.ps1` desde `docs/forge-todo.json`.
> No editar directamente; actualizar el JSON y regenerar con `.\forge-status.ps1`.
> Convencion de commits: `feat: TD-XXX ...` / `fix: TD-XXX ...`.

Progreso: **61 / 77** hechos, 16 pendientes.

## Pendientes

### Feature

- [ ] **TD-077** Como reproduce Freeletics sus videos: zoom, loop parcial y velocidad
  - NOTA PARA INVESTIGAR, no hay nada que implementar todavia. Al ver los videos extraidos de Freeletics (38 mp4 1080x1920, 8.8 a 43.7 s, sin cifrar en /sdcard/Android/data/com.freeletics.lite/files/Downloads) el usuario observa que la app NO se limita a reproducirlos enteros en bucle: a unos les hace zoom, a otros les repite solo un tramo concreto, y en algunos cambia la velocidad de reproduccion. O sea que junto al archivo hay parametros de presentacion por ejercicio -recorte, region de interes, tramo del bucle, velocidad- que aqui no tenemos: MASTER hoy reproduce el archivo entero en bucle a 1x (ui/VideoLoop.kt, VideoLoop/ExerciseVideo). El mapa hash->ejercicio y esos parametros viven en /data/data/com.freeletics.lite, que no es accesible sin root y con el APK no debuggable run-as tampoco vale, asi que no se pudo confirmar como los guardan. A DECIDIR MAS ADELANTE si a MASTER le interesa algo de eso; lo mas barato y lo que mas se notaria seria el tramo de bucle (in/out por ejercicio) para que un clip largo no obligue a ver la entrada y la salida cada vuelta. Contexto: se genero al armar el training de prueba On Your Marks.
- [ ] **TD-076** Volumen de los pitidos ajustable en Ajustes
  - El usuario oye los pitidos 'ligeramente mas alto que la musica' y quiere bajarlos un poco; recordaba que eso tenia un porcentaje. LO QUE HABIA: los pitidos de la corrida (AlarmPlayer.beepTone, desde WorkoutPlayerService playBeep y alarmCue) no aplicaban NINGUN volumen, asi que sonaban al 100 % del volumen multimedia, el mismo canal que Spotify. La curva perceptual en dB de AlarmPlayer existia, pero solo la usaba la vista previa de tonos del editor, y fijada a 1f, o sea tambien al 100 %. En el historial de este proyecto nunca hubo un campo de volumen en el modelo; el porcentaje que el usuario recordaba, si acaso, venia del app anterior. Y un comentario en ExerciseEditorScreen hablaba de 'el control de volumen y sonido del beep' cuando solo habia selector de sonido: de los que en un escaneo rapido hacen concluir cosas que no son; se corrigio y ahora remite al ajuste global. DECISION DEL USUARIO entre un nivel fijo mas bajo o un ajuste: el ajuste, para afinarlo el mismo en el telefono en vez de recompilar. LA SOLUCION: MasterConfig.beepVolume en %, default 100 -como sonaban-, guardado en SettingsStore, y en Ajustes -> Player un SegmentedRow con 100/85/70/55/40 % sobre la curva que ya existia (85 son unos -3 dB, 70 unos -5). Es uno solo para todos los pitidos, no por etapa. Al tocar un nivel suena un pitido a ese nivel, que es la unica forma de afinarlo contra la musica sin arrancar un training; se le pasa el nivel elegido en vez de leerlo de Ajustes porque se llama en el mismo toque que lo guarda. El servicio lee el nivel en CADA pitido y no al arrancar, para que un cambio con una corrida en marcha se note en el siguiente. La vista previa del editor tambien usa el nivel de Ajustes, para que no suene distinto que en el training. Los pitidos siguen sin pedir foco de audio: se oyen encima de la musica sin bajarla, que es lo que el usuario quiere.
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

### Fix

- [ ] **TD-064** Fix: quitar rotativo a un workout borra todas las variantes menos la primera
  - makeWorkoutSimple conserva los ejercicios de la PRIMERA variante y descarta el resto: w.copy(rotating = false, exercises = w.variants.firstOrNull()?.exercises, variants = emptyList()). Un workout rotativo con 3 variantes pierde dos sin aviso y sin deshacer. EL COMPORTAMIENTO DESEADO, en palabras del usuario: 'rotativo es que los workouts rotan, si le quito el rotativo deberia simplemente no rotar, no borrar nada'. O sea que el flag gobierna COMO se recorren las variantes, no DONDE viven los ejercicios; quitarlo no puede ser una operacion destructiva. Se descarto anadir un dialogo de confirmacion: confirmar una perdida de datos no deseada no arregla que la perdida no deba ocurrir. El bug lleva ahi desde que existe la funcion y no se habia notado. NO ES SOLO ESTE FIX: al revisarlo el usuario pregunto 'cual es la interfaz para hacer de un workout una variante, no la ubico', y esa pregunta abre el modelo entero de workout/variante, que hoy tiene dos representaciones distintas para lo mismo (exercises sueltos cuando es simple, exercises dentro de variants cuando es rotativo) y es de donde nace la perdida de datos al convertir. Revisar el modelo y los flujos de conversion en su propio espacio antes de tocar codigo. CASO DE REFERENCIA, senalado por el usuario: el rotativo 'Strength' del training MASTER funciona como se espera y es el que hay que mirar al abordarlo. Precision de vocabulario: el usuario lo describe como 'dentro de Strength hay 2 workouts, uno lower y otro upper, cada uno con sus ejercicios independientes', pero en el modelo Strength es UN workout con rotating=true y dos VARIANTES, Lower (12 ejercicios) y Upper (5). Lo que el usuario llama workout ahi es lo que el codigo llama variante, y esa distancia entre el vocabulario del usuario y el del modelo es parte de lo que hay que resolver. Comprobado en sus datos del 30-ago-2026: MASTER tiene tambien 'Cardio' rotativo con 4 variantes (Rope Jumping, Tire Jumping, Shadow Boxing, Running), donde quitar el rotativo hoy borraria tres. Con Strength borraria los 5 ejercicios de Upper.

### Mantenimiento

- [ ] **TD-071** Llegar al video e instrucciones de un training asignado sin duplicarlo
  - A la ficha de video e instrucciones (ExerciseMediaCard) no se llega desde un training asignado. Vive solo dentro de ExerciseEditorScreen, y a ese se entra por Edit -> workout -> ejercicio; un training asignado no ofrece Edit, solo Duplicate. El usuario ya tiene salida -duplicar el training y editar la copia- y le parece bien la regla, asi que esto no bloquea a nadie. Pero queda anotado porque es dano colateral: esa regla existe para proteger la ESTRUCTURA del training, que la sincronizacion si pisa, y el video y las instrucciones no corren ese riesgo porque viven aparte, por exerciseId del catalogo, y la sincronizacion no los toca nunca. Si algun dia molesta, el sitio natural es la vista previa: tocar un training asignado ya abre PreviewView con sus workouts y ejercicios, y desde ahi se podria entrar al material de cada uno sin reabrir la edicion. Salio al revisar TD-070.
- [ ] **TD-066** Opcional: script para publicar perfiles y asignaciones desde la PC
  - TD-063 dejo el app recibiendo asignaciones, pero los archivos users.json y users/<id>.json se generaron A MANO desde un backup del dispositivo. Asi no es usable: cada cambio de asignacion exige que alguien edite JSON. Falta un publish-profiles.ps1 que lea los trainings de una fuente -el export del usuario, o docs/ si se decide tenerlos versionados-, cruce una tabla de asignaciones tipo docs/assignments.json ({ 'niko': ['<uid>', '<uid>'] }) y genere los archivos listos para publicar, con la misma mecanica que build-release.ps1. Ojo con dos cosas al escribirlo: el uid de cada training publicado tiene que ser ESTABLE entre publicaciones, porque es la clave con la que el dispositivo empareja y conserva el id local que enlaza el historial; y publicar una lista vacia para alguien le retira sus trainings asignados, asi que conviene que el script avise de cuantos quita antes de escribir. BAJA A OPCIONAL con TD-067: asignar pasa a hacerse desde el telefono contra Supabase, asi que este script deja de ser el camino y queda como herramienta alterna para cuando estes en la PC, y solo si despues de TD-067 sigue haciendo falta.
- [ ] **TD-033** Arquitectura: Repository interfaces + MVI + Navigation + Testing
  - Fases 2-5 del plan en docs/plan-arquitectura.md. (2) Repository interfaces: TrainingRepository, SessionRepository, SettingsRepository como interfaces, WorkoutStore y SettingsStore las implementan, ViewModels reciben interfaces por constructor. (3) MVI: MasterState/MasterAction/MasterEvent, StateFlow + Channel, onAction() en vez de metodos sueltos, composables reciben state + onAction. (4) Compose Navigation type-safe con SavedStateHandle, migrar flags de navegacion del ViewModel a rutas. (5) Testing con Turbine + fakes: FakeTrainingRepository, FakeSessionRepository, FakeSettingsRepository, tests del ViewModel. Cada fase deja la app funcional y se ejecuta una a la vez.

## Hechos

### Branding

- [x] **TD-008** Rebrand del texto del wordmark (opcional)

### Bug

- [x] **TD-075** Fix: el video del ejercicio pausa la musica (Spotify) al reproducirse
- [x] **TD-015** Fix drag-reorder en lista de trainings

### Feature

- [x] **TD-073** Editar el training mientras se esta ejecutando
- [x] **TD-070** Ver u ocultar el video es cosa de cada ejercicio en su training
- [x] **TD-068** Entrega automatica de asignaciones con aviso, y deslizar para refrescar
- [x] **TD-067** Asignar trainings desde el telefono
- [x] **TD-063** Motor de recepcion de trainings asignados
- [x] **TD-062** Repositorio remoto de videos con descarga bajo demanda y cache
- [x] **TD-061** Refinar la presentacion del video en el player
- [x] **TD-059** Instrucciones paso a paso por ejercicio
- [x] **TD-058** Video instructivo por ejercicio
- [x] **TD-057** Swipe-to-reveal en WorkoutRow y VariantRow + quitar el chevron inutil
- [x] **TD-053** Snapshot automatico de datos a almacenamiento compartido
- [x] **TD-051** Add from existing: reutilizar un workout de otro training
- [x] **TD-048** Icono de la barra de estado solo en segundo plano (estilo YouTube)
- [x] **TD-039** Swipe-to-reveal en TrainingCards, en reemplazo del menu de 3 puntos
- [x] **TD-037** Tap en dia del calendario abre sheet con sesiones de ese dia
- [x] **TD-035** Atenuar pantalla del player cuando el timer esta en pausa
- [x] **TD-028** Skip por ejercicio en el player
- [x] **TD-021** Historial por ejercicio + sesiones parciales
- [x] **TD-009** Export/import de datos a JSON (PRIORITARIO)

### Fix

- [x] **TD-065** Fix: build-release.ps1 borraria el release de videos al publicar
- [x] **TD-060** Respaldo: snapshot antes de importar y versionado por marca de tiempo
- [x] **TD-052** build-debug.ps1 no debe desinstalar automaticamente
- [x] **TD-050** Fix preventivo: la copia de un workout no clona sus variantes
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

- [x] **TD-072** Ajustes: ensenar y liberar el espacio de los videos descargados
- [x] **TD-069** Pendiente: comprobar en dispositivo los avisos y la entrega automatica
- [x] **TD-056** Borrar handoff.md (TD-048 resuelto)
- [x] **TD-055** build-debug.ps1 deja de copiar el APK a Download del telefono
- [x] **TD-054** Regla: verificar firma antes de instalar y nunca desinstalar sin autorizacion
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

### UI

- [x] **TD-074** Nombre del ejercicio en el player: dos lineas como mucho, sin cortar palabras y con alto fijo
