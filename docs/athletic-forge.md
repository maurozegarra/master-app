# Athletic Forge

> De vibe coding a production-ready, un arnés a la vez.

Athletic nació como vibe coding: funciona, se publica, pero nada lo verifica
automáticamente. **Forge** es el sistema para llevarlo a production-ready sin
reescrituras, agregando capas de verificación en orden de valor y conectando
el estado real del código con el to-do.

---

## Diagnóstico — dónde está Athletic hoy

**Nivel 0 — vibe coding estructurado.**

| Tiene | Le falta |
|---|---|
| Código que funciona y se publica (110+ releases) | Tests (cero) |
| Documentación y convenciones (`AGENTS.md`, scripts) | CI / verificación automática |
| Build + install + release scripts | Feedback loop: cambio → verificación → to-do |
| Verificación manual en dispositivo (parcial) | Observabilidad (crashes, telemetría) |

El síntoma: el `to-do.md` se desactualizó porque **nada conecta "el código
cambió" con "el to-do se actualiza"**. Todo depende de la memoria humana.

---

## El sistema — capas en orden de valor

Cada capa habilita la siguiente. No se salta.

### Capa 1 — Harness de la lógica crítica (tests unitarios)
Tests unitarios puros (sin dispositivo, sin red) sobre la lógica de mayor valor
y más fácil de romper silenciosamente: el motor de pasos y la rotación idempotente
de `WorkoutPlayerService`. Corren en segundos. Es el primer arnés real: el código
deja de estar solo.

### Capa 2 — CI local
Cada build corre los tests antes de publicar. Si un test falla, el build se
detiene. `build-debug.ps1` pasa de "compila e instala" a "verifica, compila e
instala". Primer loop cerrado: *cambio → test → feedback*.

### Capa 3 — To-do alimentado por tests (loop engineering)
El to-do deja de ser markdown libre y pasa a ser estructurado: cada item tiene
un ID estable (`TD-001`), un criterio de verificación (test o paso manual), y un
estado derivado del código (no de la memoria). Un script genera el estado del
to-do desde git log + resultados de tests. Convención de commits que referencien
el ID (`feat: TD-001 weekly calendar`).

### Capa 4 — Verificación en dispositivo y observabilidad
Crash reporting, verificación funcional automatizada (Espresso/UI Automator),
release pipeline más robusto. Lo más caro y lo que menos valor da por ahora; se
aborda cuando las capas 1–3 están firmes.

---

## Arranque — Capa 1: tests del motor

### Qué se testea
`WorkoutPlayerService` (máquina de pasos) y `model/Workout.kt` (dominio). Sin
dispositivo, sin servicio real: se extrae la lógica testeable y se prueba pura.

### Comportamientos a cubrir
1. **Generación de pasos** (`buildSteps`): un training con N workouts × M
   ejercicios × K etapas produce la secuencia esperada de `PlayerStep`.
2. **Avance de pasos** (`advance`): desde un step, `advance` mueve al siguiente;
   al final del último step, finaliza el player.
3. **Rotación por workout** (`markCompletedWorkouts`): cada workout rota de forma
   independiente; al terminar un workout, su variante avanza; al abandonar a mitad,
   ese workout no rota.
4. **Idempotencia de la rotación** (`advancedWorkouts`): llamar `markCompletedWorkouts`
   dos veces con el mismo rango no rota dos veces.
5. **Edge cases**: workout vacío, training con un solo workout, última etapa del
   último ejercicio, workout rotativo con una sola variante.
6. **Cue de transición** (`alarmCue`): respeta `step.alarm == false` (no suena);
   con `step == null` (fin de workout) sí suena `beep_work.ogg`.

### Cómo
- Tests en `app/src/test/java/com/athletic/` (carpeta estándar de unit tests de
  Android, corre con `./gradlew test` o el Gradle cacheado).
- Framework: JUnit 4 (ya viene con `androidx.test` en el setup de Compose) +
  assertions fluidas. Sin Robolectric ni instrumentación para la lógica pura.
- Si la lógica de pasos está acoplada al servicio (depende de `Context`,
  `MediaPlayer`, etc.), se extrae a una clase testeable pura
  (`StepEngine` o similar) sin cambiar el comportamiento del servicio. El servicio
  delega en ella. **Refactor mínimo, sin reescritura.**

### Criterio de salida de la Capa 1
- Suite de tests del motor corriendo en <10s.
- Cobertura de los 6 comportamientos arriba.
- `build-debug.ps1` ejecuta los tests antes de compilar (puente a Capa 2).

---

## Convenciones que adoptamos desde ya

- **Cada cambio de comportamiento va con test.** Si tocas el motor, agregas o
  actualizas el test correspondiente. Sin test, no hay commit (lo hace el CI).
- **Commits referencian el to-do** cuando aplique: `feat: TD-001 ...`,
  `fix: TD-002 ...`. El ID viaja en el commit, no se inventa después.
- **El to-do se actualiza al cerrar el item**, no "cuando se acuerde". El script
  de la Capa 3 lo hará automático; mientras tanto, regla manual estricta.
- **No se borra un test para que pase el build.** Si un test falla, se arregla el
  código o se cambia el test con justificación explícita.

---

## Workflow del agente (cómo proceder cuando el usuario pide algo)

Estas son las reglas que **el agente de IA** (Devin, Cascade, etc.) debe seguir
cuando el usuario pide una feature, fix, o cambio. El agente **no improvisa**:
sigue estos pasos en orden.

### Escenario A — "Necesito la feature X" (feature nueva)

1. **Asignar ID**: el agente lee `docs/forge-todo.json`, encuentra el último
   `TD-NNN` y asigna el siguiente (`TD-015`, `TD-016`, etc.).
2. **Agregar el item al JSON**: el agente edita `docs/forge-todo.json` con el
   nuevo item, `status: "pending"`, categoría apropiada, y un campo `manual`
   que describa qué hay que hacer.
3. **Regenerar to-do**: `.\forge-status.ps1 -SkipTests` para que el item aparezca.
4. **Confirmar con el usuario**: mostrar el item agregado y esperar confirmación
   antes de implementar (salvo que el usuario ya haya dicho "hazlo").
5. **Implementar**: escribir el código + los tests correspondientes.
6. **Verificar**: `.\verify-compile.ps1` (corre tests + compila release). Si falla,
   arreglar antes de continuar.
7. **Commitear** (si el usuario autoriza): `feat: TD-NNN descripción`.
8. **Regenerar to-do**: `.\forge-status.ps1` (con tests) para que el item pase a
   done automáticamente si tiene test de verificación, o marcar `status: "done"`
   en el JSON si es por commit.
9. **Build** (si el usuario pide instalar): `.\build-debug.ps1 -Message "feat: TD-NNN ..."`.

### Escenario B — "Arregla el bug Y" (fix)

1. **Buscar el item**: si el bug corresponde a un `TD-NNN` existente, usar ese ID.
   Si no, asignar uno nuevo (mismo paso 1 del Escenario A).
2. **Escribir test que reproduzca el bug** (test failing). Esto es obligatorio
   para bugs de lógica: el test documenta el bug y protege contra regresión.
3. **Arreglar el código** hasta que el test pase.
4. **Verificar**: `.\verify-compile.ps1`.
5. **Commitear**: `fix: TD-NNN descripción`.
6. **Regenerar to-do**: `.\forge-status.ps1`.

### Escenario C — "Limpia / refactoriza Z" (mantenimiento)

1. **Asignar ID** o usar el existente (ej. TD-006 para código muerto).
2. **Implementar** el refactor. Si cambia comportamiento, agregar/actualizar tests.
3. **Verificar**: `.\verify-compile.ps1`.
4. **Commitear**: `chore: TD-NNN descripción`.
5. **Regenerar to-do**: `.\forge-status.ps1 -SkipTests` (o con tests si los tocó).

### Escenario D — "Quiero ver el estado del proyecto"

1. `.\forge-status.ps1` (corre tests + genera to-do.md).
2. Mostrar al usuario: progreso (N/M hechos), items pendientes por categoría,
   y si algún test está fallando.

### Reglas cruzadas

- **El agente NUNCA edita `to-do.md` directamente.** Siempre edita
  `docs/forge-todo.json` y regenera con `forge-status.ps1`.
- **El agente NUNCA commitea sin autorización explícita del usuario** (regla del
  `AGENTS.md`, sigue vigente).
- **El agente SIEMPRE corre `verify-compile.ps1` antes de declarar done un item**
  que toque código. Si los tests fallan, el item queda pending.
- **Si el usuario pide algo que no encaja en el sistema** (una pregunta, una
  consulta, exploración), el agente no necesita crear un TD-NNN. El sistema es
  para cambios rastreables, no para conversación.

---

## Estado del sistema

| Capa | Estado | Notas |
|---|---|---|
| 1 — Tests del motor | **Completada** | 59 tests, 4 archivos, corren en 18s |
| 2 — CI local | **Completada** | `run-tests.ps1` + integrado en los 3 scripts de build |
| 3 — To-do estructurado | **Completada** | `docs/forge-todo.json` + `forge-status.ps1` generan `to-do.md` |
| 4 — Dispositivo + obs. | Pendiente | Depende de Capa 3 |
