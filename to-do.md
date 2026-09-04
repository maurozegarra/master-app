# To-Do - MASTER

> Generado automaticamente por `forge-status.ps1` desde `docs/forge-todo.json`.
> No editar directamente; actualizar el JSON y regenerar con `.\forge-status.ps1`.
> Convencion de commits: `feat: TD-XXX ...` / `fix: TD-XXX ...`.

Progreso: **53 / 67** hechos, 14 pendientes.

## Pendientes

### Feature

- [ ] **TD-067** Asignar trainings desde el telefono
  - TD-063 dejo funcionando la mitad de abajo: un telefono elige perfil, descarga lo que le tocan y lo fusiona sin romper el historial. La mitad de arriba no existia: crear un usuario era anadir una linea a users.json en GitHub y asignar era dejar el training entero en users/<id>.json, los dos escritos A MANO desde un backup del dispositivo; por eso Niko salia en la lista pero al elegirlo no pasaba nada (users/niko.json daba 404). La decision que ordena todo esto es del usuario: ASIGNAR TIENE QUE HACERSE DESDE EL TELEFONO, no desde un script en la PC. Y ahi esta el problema de fondo: a un archivo de GitHub no se escribe sin una credencial que no puede vivir dentro de un APK. Se elige SUPABASE, que se consume con HttpURLConnection igual que hoy, asi que el app NO gana ninguna libreria; el plan gratuito sobra para cuatro personas y es el unico camino evaluado que luego permite que cada uno tenga cuenta propia. Los videos NO se tocan: siguen en GitHub Releases con videos.json, porque el manifiesto guarda URLs y a la base de datos no le importa donde esten los bytes. Tres tablas: profiles (id text, name), trainings (uid pk, payload jsonb), assignments (profile_id, training_uid). El payload es el training tal cual lo serializa TrainingJson.encode, el mismo formato que ya viajaba, asi que el lado que recibe apenas cambia. El id de profiles es TEXTO y no uuid a proposito: los telefonos con v1.0.177 ya llevan guardado 'mauro' en preferencias, y con uuid habrian tenido que volver a elegir su perfil. Quien puede escribir se decide en el servidor con RLS y no en el app: leer, cualquiera con la clave publicable que va dentro del APK -esta disenada para ser publica-; escribir, solo una sesion autenticada, una unica cuenta, la del entrenador. El registro de nuevos usuarios queda APAGADO, porque una cuenta equivale a permiso de escritura. Se ejecuta en tres etapas, cada una dejando el app funcionando. ETAPA A HECHA: recibir desde la base de datos, sin cambios visibles, para validar la conexion antes de construir encima. net/Http.kt nuevo, un request(method,url,headers,body) sobre HttpURLConnection con el patron que ya tenia Downloader (timeouts, anticache, cierre en finally) y que devuelve el cuerpo del error a proposito, porque PostgREST explica ahi por que rechaza; fetchText pasa a apoyarse en el. net/Supabase.kt nuevo con la URL y la clave en un solo sitio, mas un accessToken opcional en headers() ya preparado para las escrituras de la etapa B. AssignmentRepository lee de REST y trae los trainings de alguien en UNA llamada: assignments?profile_id=eq.<id>&select=trainings(payload). Su get() LANZA ante cualquier codigo que no sea 2xx en vez de devolver el cuerpo del error, y eso es carga estructural: el cuerpo de un error se decodificaria como cero trainings, o sea como una desasignacion. Por lo mismo, en AssignedTrainingsJson una fila sin payload legible invalida la respuesta ENTERA en lugar de saltarse esa fila, porque saltarsela dejaria menos trainings de los asignados y desde fuera eso es indistinguible de una desasignacion. Se mantiene la regla ya probada: null si no se pudo leer, lista vacia solo si de verdad no le toca ninguno. users.json y users/mauro.json SE QUEDAN publicados hasta que todos actualicen; borrarlos ahora dejaria a ciegas a un telefono con la version vieja. VERIFICADO EN DISPOSITIVO en v1.0.178: el app arranca con todos sus trainings, COLUMNA sigue con su insignia ASSIGNED y el dialogo de perfil muestra Mauro y Niko leidos de Supabase -directory() no tiene copia local, si la llamada fallara la lista saldria vacia-, sin ningun fallo en el log. ETAPA B HECHA: asignar desde el telefono. AuthStore con login contra /auth/v1/token, renovacion sola y los tokens en SUS PROPIAS preferencias (master_auth), excluidas del respaldo en backup_rules.xml y data_extraction_rules.xml: un refresh token restaurado en otro telefono es permiso de escritura sobre los perfiles de todos. La renovacion se pide un minuto ANTES de caducar, porque pedirla en el limite deja la ventana en que el token viaja ya vencido; si el servidor RECHAZA el refresh se cierra la sesion, pero si no hay red NO se cierra, porque sin red no se puede saber si sigue viva y borrarla obligaria a volver a entrar cada vez que se abre el app fuera de cobertura. El motivo de un rechazo se enseña tal cual lo da el servidor: 'contraseña mal escrita' y 'cuenta que no existe' piden acciones distintas y un 'no se pudo' generico las haria indistinguibles. Bloque Entrenador en Settings, pantalla People (crear, renombrar, eliminar) y dialogo 'Asignar a...' desde una SwipeAction verde nueva en la card, visible solo con sesion y solo en trainings propios: uno asignado ya viene de otro, y repartirlo publicaria una copia con el mismo uid. isCoach es estado OBSERVABLE del ViewModel y no una lectura del almacenamiento, porque si no la accion de asignar solo aparecia cuando algo mas obligaba a repintar la lista. DECISIONES QUE EVITAN PERDER DATOS: el dialogo de asignar se abre con lo que hay HOY en el servidor ya marcado, nunca en blanco -si no, confirmar sin tocar nada desasignaria a todo el mundo-, y si no se pudo leer la lista o los marcados NO se ofrece guardar, porque se confirmaria contra un estado inventado; borrar un perfil dice cuantas asignaciones se lleva por delante y no deja confirmar hasta saber el numero; el id de un perfil sale del nombre (Niko Zegarra -> niko-zegarra, acentos plegados) y es ESTABLE al renombrar, asi que cambiar el nombre no desengancha a nadie. El payload se sube en cada confirmacion y no solo la primera vez, de modo que reasignar es tambien como se publican los cambios del training. El perfil de este mismo telefono NO aparece en el dialogo: asignarselo a uno mismo dejaria dos copias del mismo training, la propia y la asignada, porque la fusion no toca nunca lo propio. VERIFICADO EN DISPOSITIVO contra la base de datos real: crear un perfil desde el telefono (quedo con id 'prueba'), asignar MASTER a dos personas (subio el training entero -4 workouts, 33 ejercicios contando variantes, con assigned=false porque la insignia la pone quien recibe- y creo las dos filas), reabrir el dialogo y ver los dos ya marcados leidos del servidor, desmarcar a uno y ver desaparecer SOLO su fila, renombrar conservando el id, y borrar un perfil llevandose su asignacion en cascada. Tambien verificado el camino del rechazo: con el proveedor de correo apagado el login enseñaba el motivo real del servidor. NO PROBADO todavia: que al cerrar sesion desaparezca la accion de asignar.
 Y ETAPA C: 'Sincronizar ahora' en Ajustes y sincronizar al volver a primer plano -hoy solo ocurre en MasterViewModel.init, asi que una asignacion nueva tarda hasta el siguiente arranque en frio-, 'Ninguno' en el selector de perfil, y aviso cuando el perfil elegido no tiene nada asignado en vez de silencio. QUEDA FUERA: cuenta propia para cada persona, sincronizar el historial, editar en el telefono un training asignado (sigue siendo duplicar para hacerlo propio) y publicar los ejercicios propios del que asigna -el nombre viaja dentro del training y se ve bien, lo que no viaja es su definicion de catalogo-.
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

- [ ] **TD-066** Opcional: script para publicar perfiles y asignaciones desde la PC
  - TD-063 dejo el app recibiendo asignaciones, pero los archivos users.json y users/<id>.json se generaron A MANO desde un backup del dispositivo. Asi no es usable: cada cambio de asignacion exige que alguien edite JSON. Falta un publish-profiles.ps1 que lea los trainings de una fuente -el export del usuario, o docs/ si se decide tenerlos versionados-, cruce una tabla de asignaciones tipo docs/assignments.json ({ 'niko': ['<uid>', '<uid>'] }) y genere los archivos listos para publicar, con la misma mecanica que build-release.ps1. Ojo con dos cosas al escribirlo: el uid de cada training publicado tiene que ser ESTABLE entre publicaciones, porque es la clave con la que el dispositivo empareja y conserva el id local que enlaza el historial; y publicar una lista vacia para alguien le retira sus trainings asignados, asi que conviene que el script avise de cuantos quita antes de escribir. BAJA A OPCIONAL con TD-067: asignar pasa a hacerse desde el telefono contra Supabase, asi que este script deja de ser el camino y queda como herramienta alterna para cuando estes en la PC, y solo si despues de TD-067 sigue haciendo falta.
- [ ] **TD-033** Arquitectura: Repository interfaces + MVI + Navigation + Testing
  - Fases 2-5 del plan en docs/plan-arquitectura.md. (2) Repository interfaces: TrainingRepository, SessionRepository, SettingsRepository como interfaces, WorkoutStore y SettingsStore las implementan, ViewModels reciben interfaces por constructor. (3) MVI: MasterState/MasterAction/MasterEvent, StateFlow + Channel, onAction() en vez de metodos sueltos, composables reciben state + onAction. (4) Compose Navigation type-safe con SavedStateHandle, migrar flags de navegacion del ViewModel a rutas. (5) Testing con Turbine + fakes: FakeTrainingRepository, FakeSessionRepository, FakeSettingsRepository, tests del ViewModel. Cada fase deja la app funcional y se ejecuta una a la vez.

## Hechos

### Branding

- [x] **TD-008** Rebrand del texto del wordmark (opcional)

### Bug

- [x] **TD-015** Fix drag-reorder en lista de trainings

### Feature

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
