# To-Do - MASTER

> Generado automaticamente por `forge-status.ps1` desde `docs/forge-todo.json`.
> No editar directamente; actualizar el JSON y regenerar con `.\forge-status.ps1`.
> Convencion de commits: `feat: TD-XXX ...` / `fix: TD-XXX ...`.

Progreso: **55 / 72** hechos, 17 pendientes.

## Pendientes

### Feature

- [ ] **TD-070** Poder ocultar el video publicado de un ejercicio
  - Probando la v1.0.189 el usuario quiso quitar el video de un ejercicio de COLUMNA. Hizo lo que el app pide para tocar un training asignado -duplicarlo y editar la copia-, llego a la ficha de video e instrucciones, y no habia ninguna opcion para quitarlo. La causa: ExerciseMediaCard solo pintaba 'Remove video' si hasOwnVideo(exerciseId), o sea si el archivo esta en own/, que es donde va lo que se asigna a mano con el selector en ESE telefono. Los siete videos de COLUMNA los publico el propio usuario en el manifiesto, asi que al telefono llegan a repo/ y para el app no son suyos. Esconder el boton tenia buen motivo -borrar un repo/ solo hace que se vuelva a descargar, asi que ofrecerlo seria mentir- pero dejaba sin salida el caso legitimo: no quiero ver este video en mi telefono, sin tocar lo que el coach publico. Y duplicar el training no ayuda, que es la parte que sorprende: el video no cuelga del training sino del exerciseId del catalogo, asi que la copia hereda exactamente el mismo. LA SOLUCION es un estado nuevo, VideoState.Hidden, y no una consulta aparte: la lista sellada ya modela todo lo que le puede pasar a un video, asi que en cuanto oculto es uno mas, todo lo que ya mira el estado hace lo correcto sin tocarlo -resolveState lo devuelve antes que nada, request() lo anade a la guarda y por eso no se encola ni se descarga, y fileFor() devuelve null porque Hidden no es Ready, con lo que el player deja de ensenarlo sin cambiar PlayerScreen-. La lista de ocultos se guarda como StringSet en las preferencias 'master' que VideoRepository ya abre, y NO en ExerciseMedia: ese modelo declara explicitamente que el video no vive ahi, y estar oculto es estado del video. rebuildStates() suma los ocultos a los ids que recorre, porque si no un video oculto desapareceria del mapa y no habria forma de volver a mostrarlo. ALCANCE, y conviene tenerlo claro: se oculta POR EJERCICIO DEL CATALOGO y solo en este telefono, asi que ocultar el de Gato-vaca lo oculta en todos los trainings del dispositivo que usen ese ejercicio, incluido el COLUMNA asignado. Es la misma regla que ya siguen las instrucciones y la ficha ya lo avisa con appliesToAllTrainings. NO se hace por copia del training aunque sea lo que la pregunta sugiere: exigiria un campo en Exercise, que viaja dentro del payload, y una preferencia local acabaria publicandose a los demas la proxima vez que ese training se asignara. OCULTAR NO BORRA el archivo descargado, para que se pueda deshacer sin red; liberar espacio es otra cosa y es TD-072. En la UI, quitar pasa a llamarse distinto segun el caso: sobre un video propio sigue siendo 'Remove video' y borra el own/ dejando ver el publicado si lo habia; sobre uno publicado es 'Hide video', porque 'Remove' ahi haria pensar que se borra para todos. Con un video propio encima de uno publicado salen los dos pasos seguidos, que es justo lo que ocurre. Y hay rama para el estado oculto -'Video hidden on this phone' con 'Show video'-, sin la cual ocultar seria una puerta de un solo sentido. La regla del orden se extrajo a una funcion pura, videoStateOf(hidden, rev, file), fijada en VideoStateTest: oculto gana sobre todo lo demas, porque si ganase el archivo el video se seguiria ensenando y si ganase la revision se descargaria uno que ya se dijo que no se queria.
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

- [ ] **TD-072** Ajustes: ensenar y liberar el espacio de los videos descargados
  - VideoCache.bytesUsed() y clearDownloaded() son codigo muerto. El comentario del propio archivo dice 'para poder ensenarlo en Settings' y nunca se conecto, asi que hoy no hay ninguna forma de liberar los videos descargados desde el app: solo los siete de COLUMNA son unos 20 MB, y la carpeta esta excluida del respaldo, o sea que tampoco se limpia sola. Falta la fila en Ajustes, dentro del grupo de datos: 'Videos descargados: X MB' con la accion de borrarlos. La logica ya esta escrita y probada en VideoCacheTest; es solo enchufarla. Ojo con una cosa: clearDownloaded borra repo/ y NO toca own/, que es justo lo correcto -un video propio no se recupera solo- y conviene que el texto lo diga, para que nadie crea que pierde el suyo. Salio al revisar TD-070.
- [ ] **TD-071** Llegar al video e instrucciones de un training asignado sin duplicarlo
  - A la ficha de video e instrucciones (ExerciseMediaCard) no se llega desde un training asignado. Vive solo dentro de ExerciseEditorScreen, y a ese se entra por Edit -> workout -> ejercicio; un training asignado no ofrece Edit, solo Duplicate. El usuario ya tiene salida -duplicar el training y editar la copia- y le parece bien la regla, asi que esto no bloquea a nadie. Pero queda anotado porque es dano colateral: esa regla existe para proteger la ESTRUCTURA del training, que la sincronizacion si pisa, y el video y las instrucciones no corren ese riesgo porque viven aparte, por exerciseId del catalogo, y la sincronizacion no los toca nunca. Si algun dia molesta, el sitio natural es la vista previa: tocar un training asignado ya abre PreviewView con sus workouts y ejercicios, y desde ahi se podria entrar al material de cada uno sin reabrir la edicion. Salio al revisar TD-070.
- [ ] **TD-069** Pendiente: comprobar en dispositivo los avisos y la entrega automatica
  - Lo que quedo sin comprobar de TD-067 y TD-068 al publicar la v1.0.189, apartado aqui porque los dos TD estaban hechos y probados en lo que se podia probar con un solo telefono, y esperar a lo demas habria bloqueado la publicacion. De lo de aqui ya solo faltan dos cosas, y las dos necesitan dejar pasar el tiempo con un segundo telefono encendido: que la notificacion se pinte y que el sondeo salte solo. VALIDADO POR EL USUARIO en v1.0.189 los cuatro avisos de sincronizacion, tirando de la lista: 'ya estabas al dia' en condiciones normales, 'no se pudo sincronizar' con el modo avion puesto y sin que desapareciera ningun training, 'elige primero quien usa este telefono' con el perfil en 'Nadie', y antes el de 'no te toca ninguno'. El usuario hizo la tercera TAMBIEN con el modo avion, que prueba dos cosas de regalo: que 'Nadie' se puede elegir sin conexion -la lista de personas no se lee, pero esa opcion no depende de ella, y eso fue una decision deliberada- y que la falta de perfil MANDA sobre la falta de conexion, que es el orden correcto: decirle a alguien que revise su conexion cuando lo que le falta es elegir quien usa el telefono le manda a perseguir el problema equivocado. Queda cerrado tambien que tirar de la lista llama de verdad a la red, porque 'ya estabas al dia' solo sale despues de leer el servidor. Un toast no aparece en el arbol de vistas, asi que esto no se podia automatizar por adb; lo miro una persona. VALIDADO EN UN SEGUNDO DISPOSITIVO, y con el todo el ciclo de TD-067 que aqui no se podia probar: se instalo la v1.0.189 en el telefono de Niko desde el release de GitHub, se eligio el perfil Niko, y desde el telefono del entrenador se le asignaron dos trainings. El primero aparecio al tirar de la lista. El segundo aparecio solo al mandar el app a segundo plano, esperar un minuto largo y volver a abrirlo, que es exactamente lo que faltaba comprobar: MainActivity.onStart dispara la lectura de verdad, y la instancia del ViewModel que usa es la misma que pinta la pantalla. DECISION DEL USUARIO, tomada con el diagnostico delante: NO se anade un sondeo mientras el app esta a la vista. El hueco es real -con el app abierto en primer plano nada lo refresca, porque onStart no vuelve a ocurrir y el sondeo en segundo plano solo avisa, nunca fusiona- y la correccion obvia seria comprobar cada minuto con la pantalla encendida, pero el usuario lo rechazo por lo que cuesta: 'no me parece una correccion estar haciendo get cada minuto'. Se queda como esta, con tirar de la lista como salida para el impaciente. Que quede escrito para que nadie lo tome por un descuido y lo 'arregle' mas adelante. El caso queda cubierto de todos modos, con menos finura: el sondeo en segundo plano corre este el app abierto o no, asi que en su siguiente pasada avisa igual; eso si, si el app ya esta en primer plano, tocar el aviso no sincroniza -la Activity ya esta arrancada y no pasa por onStart-, y hay que tirar de la lista. QUE LA NOTIFICACION SE PINTE, y que al tocarla se abra el app con el training ya dentro. El permiso POST_NOTIFICATIONS ya esta concedido en el telefono del entrenador, asi que esa incognita esta descartada. QUE EL SONDEO SALTE SOLO, sin forzarlo: se ha visto correr y decidir bien -'mauro: 1 asignados, 1 ya en el telefono, 0 por avisar'-, pero siempre a mano con cmd jobscheduler run -f. En un Samsung la optimizacion de bateria puede retrasarlo horas o matarlo, y ESTO es lo que decide si la entrega automatica sirve de verdad; si no salta solo, hay que exceptuar el app de la optimizacion de bateria o replantearse el push (FCM). POR QUE NO SE PUDO PROBAR AQUI: los tres ultimos necesitan una asignacion que el telefono todavia no tenga, y en UN SOLO telefono ese estado es imposible de fabricar -el dialogo de asignar excluye a proposito el perfil del propio dispositivo, elegir perfil sincroniza en el acto, un training asignado no se puede borrar en local, y con el modo avion no se puede ni cambiar de perfil porque la lista de personas tampoco se lee-. Se comprueban solos la primera vez que el telefono de Niko tenga la v1.0.189 o posterior.
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
