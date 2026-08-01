# To-Do - Athletic

> Generado automaticamente por `forge-status.ps1` desde `docs/forge-todo.json`.
> No editar directamente; actualizar el JSON y regenerar con `.\forge-status.ps1`.
> Convencion de commits: `feat: TD-XXX ...` / `fix: TD-XXX ...`.

Progreso: **9 / 19** hechos, 10 pendientes.

## Pendientes

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

### Rebrand

- [ ] **TD-017** Crear proyecto nuevo com.maurozegarra.master y migrar codigo
  - Crear proyecto nuevo en C:\Users\MASTER\code\master con package com.maurozegarra.master. Migrar src, res, fonts, manifest, gradle, scripts, docs, branding. Ajustar imports, applicationId, namespace, scripts, URLs. Partir de cero en datos (no migrar SharedPreferences). Verificar compile + tests + dispositivo. Borrar athletic al final.
- [ ] **TD-018** Renombrar repo de GitHub y actualizar URLs
  - Renombrar repo en GitHub (si aplica). Actualizar URLs en build-release.ps1 (repo, tag, apkUrl), update.json, README.md. Verificar que el self-update apunta al repo correcto.
- [ ] **TD-019** Actualizar documentacion del rebrand
  - Actualizar AGENTS.md (paquete, estructura, scripts), README.md, docs/athletic-forge.md, docs/hoja-de-ruta.md. Reemplazar 'Athletic' por 'MASTER' donde corresponda.

### Testing

- [ ] **TD-016** Verificar self-update en dispositivo
  - Pospuesto: necesita una version mas nueva en GitHub para que aparezca la barra de update. Probar deteccion, descarga e instalacion del APK.

## Hechos

### Branding

- [x] **TD-008** Rebrand del texto del wordmark (opcional)

### Bug

- [x] **TD-015** Fix drag-reorder en lista de trainings

### Forge

- [x] **TD-001** Tests del motor de pasos (StepEngine) - verificado por tests
- [x] **TD-002** Tests de rotacion idempotente - verificado por tests
- [x] **TD-003** Tests de dominio (Workout, PlayerStep) - verificado por tests
- [x] **TD-004** CI local integrado en scripts de build

### Mantenimiento

- [x] **TD-006** Limpieza de codigo muerto
- [x] **TD-007** Decidir sobre branding/icons.html sin trackear

### Testing

- [x] **TD-005** Verificacion funcional en dispositivo
