# To-Do - Athletic

> Generado automaticamente por `forge-status.ps1` desde `docs/forge-todo.json`.
> No editar directamente; actualizar el JSON y regenerar con `.\forge-status.ps1`.
> Convencion de commits: `feat: TD-XXX ...` / `fix: TD-XXX ...`.

Progreso: **5 / 14** hechos, 9 pendientes.

## Pendientes

### Branding

- [ ] **TD-008** Rebrand del texto del wordmark (opcional)
  - Decidir si se mantiene 'MASTER' o se regenera con glifos de 'ATHLETIC' usando branding/wordmark/tools/

### Feature

- [ ] **TD-009** Respaldo/exportacion de trainings JSON (opcional)
  - Evaluar export/import de trainings (JSON) via SAF, como mini-timer con BackupManager
- [ ] **TD-010** Catalogo de ejercicios expandido (opcional)
  - Revisar/expandir ExerciseCatalog y ExerciseIcons pensando en el app independiente
- [ ] **TD-011** Animaciones de ejercicio (opcional)
  - Placeholder para animacion por ejercicio; definir si entra en el roadmap (sin libs de red)
- [ ] **TD-012** Onboarding / estado vacio (opcional)
  - Cuidar el primer arranque: seed de ejemplos vs vacio + tutorial breve
- [ ] **TD-013** Accesibilidad (opcional)
  - contentDescription en iconos del player y areas tactiles >=48dp
- [ ] **TD-014** Keep-screen-on durante la corrida (opcional)
  - El player a pantalla completa podria beneficiarse de keep-screen-on

### Mantenimiento

- [ ] **TD-007** Decidir sobre branding/icons.html sin trackear
  - Preview HTML del launcher icon. Commitear o agregar a .gitignore.

### Testing

- [ ] **TD-005** Verificacion funcional en dispositivo
  - Avance de pasos (Done), countdown cronometrado, pausa/reanudar, segundo plano y muerte de proceso, registro en Historial, drag-reorder, weekly calendar, mini-player, self-update

## Hechos

### Forge

- [x] **TD-001** Tests del motor de pasos (StepEngine) - verificado por tests
- [x] **TD-002** Tests de rotacion idempotente - verificado por tests
- [x] **TD-003** Tests de dominio (Workout, PlayerStep) - verificado por tests
- [x] **TD-004** CI local integrado en scripts de build

### Mantenimiento

- [x] **TD-006** Limpieza de codigo muerto
