# To-Do - Athletic

> Generado automaticamente por `forge-status.ps1` desde `docs/forge-todo.json`.
> No editar directamente; actualizar el JSON y regenerar con `.\forge-status.ps1`.
> Convencion de commits: `feat: TD-XXX ...` / `fix: TD-XXX ...`.

Progreso: **9 / 20** hechos, 11 pendientes.

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

- [ ] **TD-020** Eliminar referencias a entorno corporativo y mini-timer
  - Eliminar todas las refs a entorno corporativo (CrowdStrike, Netskope, JFrog, 'equipo corporativo', 'sin descargas de red', mzegarra_ide) y a mini-timer (proyecto origen) de docs, workflows y comentarios de codigo. Borrar docs/hallazgos-athlete.md (obsoleto) y .windsurf/workflows/conectar-telefono.md (duplicado con refs a otro proyecto).

### Rebrand

- [ ] **TD-019** Actualizar documentacion del rebrand
  - Actualizar AGENTS.md (paquete, estructura, scripts), README.md, docs/athletic-forge.md, docs/hoja-de-ruta.md. Reemplazar 'Athletic' por 'MASTER' donde corresponda.
- [ ] **TD-018** Renombrar repo de GitHub y actualizar URLs
  - Renombrar repo en GitHub (si aplica). Actualizar URLs en build-release.ps1 (repo, tag, apkUrl), update.json, README.md. Verificar que el self-update apunta al repo correcto.
- [ ] **TD-017** Crear proyecto nuevo com.maurozegarra.master y migrar codigo
  - Crear proyecto nuevo en C:\Users\MASTER\code\master con package com.maurozegarra.master. Migrar src, res, fonts, manifest, gradle, scripts, docs, branding. Ajustar imports, applicationId, namespace, scripts, URLs. Partir de cero en datos (no migrar SharedPreferences). Verificar compile + tests + dispositivo. Borrar athletic al final.

### Testing

- [ ] **TD-016** Verificar self-update en dispositivo
  - Pospuesto: necesita una version mas nueva en GitHub para que aparezca la barra de update. Probar deteccion, descarga e instalacion del APK.

## Hechos

### Branding

- [x] **TD-008** Rebrand del texto del wordmark (opcional)

### Bug

- [x] **TD-015** Fix drag-reorder en lista de trainings

### Forge

- [x] **TD-004** CI local integrado en scripts de build
- [x] **TD-003** Tests de dominio (Workout, PlayerStep) - verificado por tests
- [x] **TD-002** Tests de rotacion idempotente - verificado por tests
- [x] **TD-001** Tests del motor de pasos (StepEngine) - verificado por tests

### Mantenimiento

- [x] **TD-007** Decidir sobre branding/icons.html sin trackear
- [x] **TD-006** Limpieza de codigo muerto

### Testing

- [x] **TD-005** Verificacion funcional en dispositivo
