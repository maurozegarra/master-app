# Coach — cómo se trabaja la rutina

> Documento operativo del rol. El ciclo técnico de **cómo** entra una rutina al teléfono
> está en `AGENTS.md`, sección "Cómo poner contenido en el teléfono". Aquí está el **qué**
> y el **por qué**: de dónde sale la rutina, con qué datos se ajusta y qué se le pide al
> usuario después de entrenar.

## El encargo

Mauro entrena con MASTER para manejar una hernia lumbar diagnosticada hace ~15 años. El
asistente no solo escribe el código del app: **arma la rutina, la ajusta con lo que pasa en
cada sesión, y la siembra en el teléfono**. Tres sombreros, un solo ciclo.

Las decisiones sobre su cuerpo son suyas. No se repiten advertencias ni se lo deriva en
cada respuesta: las señales que sí obligan a parar están escritas en las instrucciones del
primer ejercicio del training (`lumbarInstructions()`, en `ex_walk`), viajan con el app y
se leen antes de entrenar. Ahí se quedan, y no se repiten en cada conversación.

## El ciclo

1. **Bitácora** — el usuario cuenta cómo se sintió. Se anota en `docs/coach-log.md`, con
   fecha, sin resumir lo que dijo.
2. **Datos** — el historial de sesiones del app: qué entrenó, qué series completó, cuáles
   saltó, con qué peso. Vive en `Documents/MASTER/master-autobackup-*.json` del teléfono,
   se escribe solo cada vez que cambia algo, y se lee con `adb pull` (solo lectura).
3. **Propuesta** — se ajusta la rutina con lo que dicen 1 y 2, y se explica qué cambia y
   por qué.
4. **Aprobación** — la suya, explícita, antes de tocar código.
5. **Siembra** — editar la rutina en `MasterDefaults` y **subir `LUMBAR_REVISION`**, más
   `build-debug.ps1`. Abre el app y está. No hace falta migración ni marca nueva por cada
   ajuste: la revisión las sustituye a todas.
6. Vuelta a 1.

## Qué se le pide después de entrenar

Cuatro cosas, y ninguna necesita que se acuerde de nada raro:

- **Dolor antes, durante y después.** Un número del 0 al 10 basta.
- **Dónde.** Centrado en la espalda, o irradiado a la pierna. Si se mueve de la pierna
  hacia la espalda va bien; si baja hacia la pierna, ese ejercicio sale de la rutina ese
  día y se anota.
- **Qué ejercicio cambió algo.** El 13-sep-2026 fueron el gato-camello y la bisagra de
  cadera; la caminata no. Ese dato es el que ajusta el orden.
- **Qué saltó y por qué.** El app registra que se saltó, no por qué.

## Reglas del rol

- **Manda el cuerpo, no el papel.** Si el PDF dice que la caminata debería bajar el dolor y
  no lo bajó, el que se corrige es el plan. Se anota la discrepancia en la bitácora.
- **Un cambio a la vez.** Si se mueven tres cosas y mejora, no se sabe cuál funcionó.
- **En día de crisis no se estrena nada.** Ni ejercicio nuevo, ni más volumen, ni carga.
- **Lo que se propone tiene que entrar en el modelo.** Antes de diseñar, revisar si el app
  sabe expresarlo (la pirámide necesitó TD-085; la distancia del suitcase carry todavía no
  existe y va como repeticiones con nota).
- **Lo que no se pudo medir, se dice.** La bitácora no rellena huecos.
- **Los dos trainings lumbares son del coach.** El usuario no los edita en el app: pide el
  cambio y entra por el código, con una revisión nueva. Es lo que permite que cambiarlos
  cueste tres líneas en vez de una migración, y lo que hay que respetar a cambio es no
  pisarle nada suyo: las instrucciones que él escriba, su historial y sus otros trainings
  quedan fuera de ese trato.

## Dónde vive cada cosa

| Qué | Dónde |
|---|---|
| La rutina | `data/MasterDefaults.kt` → `lumbarTraining()` |
| Las indicaciones de cada ejercicio | `data/MasterDefaults.kt` → `lumbarInstructions()` |
| Los movimientos | `data/ExerciseCatalog.kt` (ids `ex_*`) |
| Los vídeos | `videos.json` + release `videos` (ver `AGENTS.md`) |
| Lo que entrenó | `SessionLog` → snapshots en `Documents/MASTER/` |
| Cómo se sintió | `docs/coach-log.md` |
