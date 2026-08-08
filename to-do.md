# To-Do - MASTER

> Generado automaticamente por `forge-status.ps1` desde `docs/forge-todo.json`.
> No editar directamente; actualizar el JSON y regenerar con `.\forge-status.ps1`.
> Convencion de commits: `feat: TD-XXX ...` / `fix: TD-XXX ...`.

Progreso: **23 / 32** hechos, 9 pendientes.

## Pendientes

### Feature

- [ ] **TD-028** Skip por ejercicio en el player
  - Comando SKIP_EXERCISE que salta todos los pasos restantes del ejercicio actual al siguiente. Plan: (1) PlayerCommand.SKIP_EXERCISE en PlayerBus. (2) skipExercise() en WorkoutPlayerService: busca primer step con ownerExerciseId distinto o workoutIndex distinto, hace beginStep(nextIndex) o finishPlayer(). (3) AthleteViewModel.skipExercise(). (4) Boton Skip en PlayerScreen junto a Next/Prev. (5) Edge case rotacion: si se salta el ultimo ejercicio de un workout, NO marcar ese workout como completado (no rotar). Requiere ajustar markCompletedWorkouts o pasar flag. (6) Sesion: el ejercicio saltado no aparece en ExerciseRecord (igual que hoy). El badge Partial ya indica que faltan ejercicios. (7) Decision pendiente: saltar ultimo ejercicio del ultimo workout = COMPLETED o PARTIAL? (8) Tests: skipExercise con steps simulados, rotacion al saltar ultimo ejercicio. Complejidad: media. Archivos: ~5 codigo + 2 tests.
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
- [ ] **TD-009** Respaldo/exportacion de trainings JSON (opcional)
  - Evaluar export/import de trainings (JSON) via SAF con BackupManager

### Mantenimiento

- [ ] **TD-030** Mejorar arquitectura: extraer use-cases y Repository del MasterViewModel
  - AthleteViewModel.kt (751 lineas) es un god object que mezcla estado de UI, logica de negocio, acceso a Stores y coordinacion con el Service. El dominio (model/) ya esta aislado y testeado, pero la capa de presentacion no sigue clean architecture. Plan: (1) Introducir Repository pattern entre ViewModels y Stores (WorkoutRepository, SettingsRepository) para abstraer SharedPreferences. (2) Extraer use-cases del AthleteViewModel: seeding, restauracion del player, manejo de drafts, logica de sesiones. (3) Reducir AthleteViewModel a estado de UI + delegacion a use-cases. (4) Inyeccion manual por constructor (sin framework DI). (5) Tests de use-cases y Repository. Consideracion: para una app sin red ni DI framework, evaluar si el costo/valor justifica el refactor completo o si basta con extraer los use-cases mas grandes y dejar los Stores como estan. Decidir alcance antes de implementar.

### Testing

- [ ] **TD-016** Verificar self-update en dispositivo
  - Pospuesto: necesita una version mas nueva en GitHub para que aparezca la barra de update. Probar deteccion, descarga e instalacion del APK.

## Hechos

### Branding

- [x] **TD-008** Rebrand del texto del wordmark (opcional)

### Bug

- [x] **TD-015** Fix drag-reorder en lista de trainings

### Feature

- [x] **TD-021** Historial por ejercicio + sesiones parciales

### Fix

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

- [x] **TD-005** Verificacion funcional en dispositivo
