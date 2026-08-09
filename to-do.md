# To-Do - MASTER

> Generado automaticamente por `forge-status.ps1` desde `docs/forge-todo.json`.
> No editar directamente; actualizar el JSON y regenerar con `.\forge-status.ps1`.
> Convencion de commits: `feat: TD-XXX ...` / `fix: TD-XXX ...`.

Progreso: **27 / 34** hechos, 7 pendientes.

## Pendientes

### Feature

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

- [ ] **TD-033** Arquitectura: Repository interfaces + MVI + Navigation + Testing
  - Fases 2-5 del plan en docs/plan-arquitectura.md. (2) Repository interfaces: TrainingRepository, SessionRepository, SettingsRepository como interfaces, WorkoutStore y SettingsStore las implementan, ViewModels reciben interfaces por constructor. (3) MVI: MasterState/MasterAction/MasterEvent, StateFlow + Channel, onAction() en vez de metodos sueltos, composables reciben state + onAction. (4) Compose Navigation type-safe con SavedStateHandle, migrar flags de navegacion del ViewModel a rutas. (5) Testing con Turbine + fakes: FakeTrainingRepository, FakeSessionRepository, FakeSettingsRepository, tests del ViewModel. Cada fase deja la app funcional y se ejecuta una a la vez.

## Hechos

### Branding

- [x] **TD-008** Rebrand del texto del wordmark (opcional)

### Bug

- [x] **TD-015** Fix drag-reorder en lista de trainings

### Feature

- [x] **TD-028** Skip por ejercicio en el player
- [x] **TD-021** Historial por ejercicio + sesiones parciales

### Fix

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
