---
description: Hacer commits atómicos separando por cambio lógico
---

# Workflow: Commit atómico

1. **Revisar todos los cambios**: `git diff HEAD` para ver el diff completo.
2. **Identificar cambios lógicos**: agrupar las hunks por propósito (un fix, una feature, un chore, etc.).
3. **Stagear por grupo**:
   - Si un archivo tiene hunks de un solo propósito: `git add <archivo>`.
   - Si un archivo tiene hunks de propósitos distintos: usar `git add -p <archivo>` y seleccionar solo las hunks correspondientes con `y`/`n`.
4. **Commitear cada grupo** con un mensaje descriptivo del propósito.
5. **Repetir** hasta que todos los cambios estén commiteados.
6. **Verificar**: `git status --short` debe mostrar working tree limpio.
7. **Mostrar el log**: `git log --oneline -<n>` para confirmar los commits resultantes.

## Reglas

- Un commit = un cambio lógico.
- Nunca mezclar propósitos distintos en un solo commit.
- Cambios en reglas/workflows van en su propio commit (sin bump de versión).
- El bump de versión va dentro del commit del cambio que genera el APK (feat, fix, perf, refactor). Nunca como commit separado.
- Cambios que solo tocan docs/reglas/workflows no requieren bump.
