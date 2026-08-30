# To-Do - MASTER

> Generado automaticamente por `forge-status.ps1` desde `docs/forge-todo.json`.
> No editar directamente; actualizar el JSON y regenerar con `.\forge-status.ps1`.
> Convencion de commits: `feat: TD-XXX ...` / `fix: TD-XXX ...`.

Progreso: **48 / 63** hechos, 15 pendientes.

## Pendientes

### Feature

- [ ] **TD-063** Soporte de usuarios: asignar trainings con sus ejercicios y videos
  - El app es hoy monousuario y todo vive en SharedPreferences. La meta es que existan usuarios a los que se les asigne un training con sus ejercicios y videos, al estilo de un entrenador que reparte rutinas. Va DESPUES de estabilizar TD-062, que deja preparado lo caro de retrofitear: el uid estable en Training -los ids de hoy son contadores locales y dos dispositivos generan el mismo- y la URL del manifiesto en un solo sitio, para pasar a uno por usuario (/users/<uid>/videos.json) cambiando una constante. Falta decidir proveedor: Supabase es el candidato, porque las mismas politicas RLS que controlan las filas controlan el acceso a los archivos, y su API REST se consume con HttpURLConnection sin anadir un SDK pesado; Firebase quedo descartado al retirar Cloud Storage del plan gratuito en febrero de 2026. Los bytes pueden quedarse en GitHub Releases o mudarse a Cloudflare R2 (10 GB gratis, egreso a coste cero) sin tocar el app, porque el manifiesto guarda URLs. Empezar por sincronizacion SOLO DE BAJADA: el usuario recibe trainings de solo lectura, mucho mas simple que un sync bidireccional. El historial de sesiones se queda local. Hoy son 4 usuarios.
- [ ] **TD-062** Repositorio remoto de videos con descarga bajo demanda y cache
  - TD-058 dejo el video funcionando pero con un modelo que no escala: cada video se asigna a mano y vive en Movies/MASTER/, o sea 40 gestos para cubrir el catalogo y todos los problemas del almacenamiento compartido que ya costaron una sesion (colisiones de nombre, renombrado a (3), archivos invisibles por pertenecer a otro paquete). El modelo objetivo es el de Freeletics: el app trae los trainings, no los videos, y cada video se descarga la primera vez que hace falta -al revisar el training antes de hacerlo, o durante la corrida- quedandose en el dispositivo. MASTER YA TIENE EL MOTOR: UpdateChecker baja un manifiesto JSON de GitHub con HttpURLConnection y downloadApk (enterrado en update/UpdateDialog.kt) descarga un binario con progreso; se extrae a net/Downloader.kt y lo usan los dos. Manifiesto videos.json junto a update.json, con baseUrl separado del nombre de archivo para que mudarse a R2 sea editar una linea, y un rev por video para invalidar la cache de uno solo sin rebajar los 40; se cachea en SharedPreferences para que sin red el app siga sabiendo que tiene. Los archivos van a noBackupFilesDir/videos/<exerciseId>.<rev>.mp4, que Android excluye del respaldo automatico por definicion: 100+ MB de video no pueden reventar el tope de 25 MB ni la red que fallo el 29-ago, y son reconstruibles. Se descarga a .part y se renombra al terminar, para que cortar la red a mitad no deje un truncado que parezca valido. Cola secuencial, una descarga a la vez. Prefetch: al abrir el preview del training se encolan todos sus videos; durante la corrida se asegura el del SIGUIENTE ejercicio (el StepEngine ya lo conoce y NextExerciseLabel ya lo calcula); nunca bloquea, si falta se ve el glyph y el entrenamiento sigue. Ajuste en Settings para permitir descargas con datos moviles. El video propio asignado a mano gana sobre el del repositorio y se copia al mismo directorio privado. SE BORRA la maquinaria que causo el fallo de ayer: permisos READ_MEDIA_VIDEO y READ_EXTERNAL_STORAGE, ui/VideoPermission.kt entero, y writeVideo/findVideo/deleteVideo de SharedFiles, que se queda solo con los documentos (los snapshots de TD-060 se importan a mano por SAF y no dependen de MediaStore). file_paths.xml gana una entrada files-path para que abrir en el reproductor del sistema siga funcionando con el FileProvider que ya existe. Se adelanta de TD-063 solo lo caro de retrofitear: Training gana uid UUID preservado en el respaldo, con BackupJson en formato 3 aceptando 1..3. Bytes en GitHub Releases: sin limite de ancho de banda declarado y coste cero, aunque no es un CDN de medios; por eso el app solo conoce URLs. Google Drive descartado como servidor: bloqueo por cuota 24-48h en archivos muy descargados y endpoint directo que no es API oficial.
- [ ] **TD-059** Instrucciones paso a paso por ejercicio
  - Lista de pasos numerados por ejercicio del catalogo (exerciseId), guardada en ExerciseMediaStore junto al video de TD-058. Exercise.note NO se toca: sigue siendo la consigna corta por instancia que ya se muestra en 40sp durante la ejecucion. Editor: lista editable de pasos en la misma tarjeta del video. Player: icono en el OSD arriba a la derecha, visible con playerControlsVisible, que abre un ModalBottomSheet con el nombre y los pasos numerados; solo aparece si el ejercicio tiene instrucciones. Se descarto el swipe up de Freeletics: el player ya tiene el tap (alterna OSD) y el arrastre horizontal (check/anterior) ocupados, y un tercer gesto vertical seria invisible sin un texto de ayuda que cargaria mas la zona inferior, donde ya estan los controles y el label Next.
- [ ] **TD-057** Swipe-to-reveal en WorkoutRow y VariantRow + quitar el chevron inutil
  - Continuacion de TD-039, y donde mas se necesita segun el usuario: las filas del editor de training (WorkoutRow) y de la lista de variantes (VariantRow) muestran menu de 3 puntos Y un chevron KeyboardArrowRight que no aporta nada, porque la fila entera ya es clickable. Reusar el componente de swipe de TD-039: WorkoutRow con Make rotating/simple, Duplicate y Delete; VariantRow con Duplicate y Delete (dos acciones, de ahi que el componente acepte un numero variable). Eliminar el menu de 3 puntos y el chevron en ambas.
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

### Mantenimiento

- [ ] **TD-033** Arquitectura: Repository interfaces + MVI + Navigation + Testing
  - Fases 2-5 del plan en docs/plan-arquitectura.md. (2) Repository interfaces: TrainingRepository, SessionRepository, SettingsRepository como interfaces, WorkoutStore y SettingsStore las implementan, ViewModels reciben interfaces por constructor. (3) MVI: MasterState/MasterAction/MasterEvent, StateFlow + Channel, onAction() en vez de metodos sueltos, composables reciben state + onAction. (4) Compose Navigation type-safe con SavedStateHandle, migrar flags de navegacion del ViewModel a rutas. (5) Testing con Turbine + fakes: FakeTrainingRepository, FakeSessionRepository, FakeSettingsRepository, tests del ViewModel. Cada fase deja la app funcional y se ejecuta una a la vez.

## Hechos

### Branding

- [x] **TD-008** Rebrand del texto del wordmark (opcional)

### Bug

- [x] **TD-015** Fix drag-reorder en lista de trainings

### Feature

- [x] **TD-061** Refinar la presentacion del video en el player
- [x] **TD-058** Video instructivo por ejercicio
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
