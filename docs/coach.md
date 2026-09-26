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

## Dos dolores distintos, con la misma escala

**Desde el 25-sep-2026 se distinguen, y la distinción es suya:**

- **El habitual** es el del despertar: lleva años, él lo llama *"normalizado"*, y es **el que
  hay que ir mejorando**. Se mide con la alarma (TD-151), al abrir los ojos, y los minutos
  hasta que afloja. Es la serie que manda.
- **El de crisis** es el de los días malos de septiembre: dolor antes y después de entrenar,
  y si irradia a la pierna. **Ya se fue.** Sus preguntas van plegadas al final de la sesión,
  detrás de *Back crisis today* (TD-165), y solo se contestan si vuelve.

En sus palabras: *"ya no existe ese dolor ni al comienzo ni al final, y sin ese dolor, no hay
irradiación"*. **Un *Pain before* vacío no es un olvido**: es que no hay crisis. Leerlo como
olvido es preguntarle cada día por algo que no tiene.

## Qué se le pide después de entrenar

Cuatro cosas, y ninguna necesita que se acuerde de nada raro:

- **Dolor habitual al despertar**, que ya contesta la alarma. **El de crisis, antes y
  después, solo si hay crisis** (ver arriba).
- **Dónde.** Centrado en la espalda, o irradiado a la pierna. Si se mueve de la pierna
  hacia la espalda va bien; si baja hacia la pierna, ese ejercicio sale de la rutina ese
  día y se anota.
- **Qué ejercicio cambió algo.** El 13-sep-2026 fueron el gato-camello y la bisagra de
  cadera; la caminata no. Ese dato es el que ajusta el orden.
- **Qué saltó y por qué.** El app registra que se saltó, no por qué.
- **Si cambió la dosis, cuál usó de verdad.** El 15-sep-2026 el historial decía 20/30/40 kg
  en el puente y lo que movió fueron 6/16/21: el app tenía la barra en 20 kg y la suya pesa
  6. Un registro que no es lo que paso no sirve para decidir nada.

**Y una pregunta por cada dosis que no sea un número en el app.** La caminata llevó tres
sesiones en "no hace nada" hasta que resultó que la hacía a 3, 4 y 5 km/h; el dato estaba
bien y la interpretación mal. **Un adjetivo no dosifica**: si la rutina dice "paso vivo" y
no un número, esa es una variable suelta y hay que preguntarla o escribirla.

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

## La semana

Vigente desde el **20-sep-2026** (revisión 11). Sale de dos cosas suyas: que los **lunes,
miércoles y jueves trabaja presencial** y no tiene 78 minutos, y de una pregunta que
destapó un olvido —ocho días entrenando sin que nadie mencionara el descanso—.

| Día | Qué | Lleva carga de cadera |
|---|---|---|
| Lunes | **LUMBAR (short)** | no |
| Martes | **LUMBAR** | **sí** |
| Miércoles | **LUMBAR (short)** | no |
| Jueves | **LUMBAR (short)** | no |
| Viernes | **LUMBAR** | **sí** |
| Sábado | **LUMBAR** | **sí** |
| Domingo | **LUMBAR (short)** | no |
| Cuando la espalda mande | **LUMBAR (bad day)**, en lugar del que tocaba | no |

**Caminar y McGill van los siete días.** En esta fase el descanso no es dejar de moverse:
su dolor de la mañana aparece por estar quieto, y McGill es resistencia a baja carga, no
fuerza.

**El puente y la sentadilla van tres días**, con descanso entre ellos. Llegó a hacerlos
siete días seguidos subiendo carga en cada sesión, que es lo que esta tabla corrige. El
domingo usa el corto justo para que viernes, sábado y domingo no sean tres días cargados
seguidos. El carry se queda todos los días: son tres viajes y además es caminar cargado.

**El corto y el completo comparten el peso del carry** a propósito. Dos números distintos
para el mismo ejercicio serían dos historiales que no se pueden comparar.

### Lo que hay que mirar el 18 de octubre

Fecha acordada el 20-sep para decidir sobre **volver al Muay Thai**: un mes completo de
datos, que es el mínimo para hablar de un deporte de impacto con una hernia detrás. Los
tres números, que el app ya registra solo:

1. **Dolor al despertar en 0-1 la mayoría de los días** (el 20-sep estaba en 2).
2. **Cero irradiación**, que cumple desde el principio.
3. **El bloque cargado subiendo sin que el dolor de después se mueva del 0.**

Revisión intermedia el **4 de octubre**: si a esas alturas el dolor al despertar ya está en
0-1 casi siempre, la conversación se adelanta y **la propone el coach**, sin que él
pregunte. El retorno, cuando toque, empieza por desplazamiento y sombra —carga cero para el
disco—, no pegando.

## Antes de entregar una rutina

Nace del primer día de NIKO, el 18-sep-2026, entregado a las 22:40 con prisa por la hora:
sin instrucciones para los ejercicios nuevos, con el hip thrust en kilos totales sin saber
qué barra usa, y con el rumano asumiendo la barra de 20 por defecto —el mismo error que el
15-sep le hizo bajar la carga al otro atleta—. El usuario tuvo que preguntar cómo se hacían
dos ejercicios y hacer cuentas entre series. Su resumen: *"no le pusiste cariño"*.

**Una rutina no se entrega hasta que se cumple todo esto:**

1. **Cada ejercicio con barra dice qué barra.** Se pregunta; no se asume. El `barWeight`
   por defecto (20) no es un dato de nadie.
2. **Cada peso se puede armar.** Se revisa contra la tabla de saltos de `equipo.md`:
   con barra, múltiplos de 2.5 sobre el peso de la barra; con mancuerna, solo las que
   existen.
3. **Cada ejercicio que el atleta no conoce lleva instrucciones** dentro del app, paso a
   paso, antes de que lo vea en el player. Si tuvo que preguntar cómo se hace, faltaban.
4. **Se lee como la va a leer quien entrena**, no como la escribió quien diseña: entre
   series, cansado, con el reloj corriendo.
5. **El reloj no recorta nada de esta lista.** Si no entra bien antes de dormir, se para
   y se deja escrito dónde se quedó. Mejor mañana entera que hoy a medias.

## Dónde vive cada cosa

| Qué | Dónde |
|---|---|
| **Qué le toca cada día** | aquí mismo, en **La semana** |
| Su rutina lumbar | `data/MasterDefaults.kt` → `lumbarTraining()`, `lumbarShortTraining()`, `lumbarBadDayTraining()` · `LUMBAR_REVISION` |
| La rutina de NIKO | `data/MasterDefaults.kt` → `nikoTrainings()` · `NIKO_REVISION` · su semana base en `docs/niko.md` |
| Cómo le llega a NIKO | asignada desde el teléfono del coach. Tras una revisión **no hay que reasignar**: se republica sola al guardar y al volver a primer plano (TD-132) |
| Las indicaciones de cada ejercicio | se escriben en `catalogInstructions()` / `lumbarInstructions()` y **viajan por Supabase** (tabla `exercise_media`, TD-139): corregir una le llega al atleta al abrir el app, sin versión nueva |
| Los movimientos | `data/ExerciseCatalog.kt` (ids `ex_*`) |
| El equipo y qué pesos se pueden pedir | `docs/equipo.md` · en el código, `HomeGym` y `Plates` |
| Los vídeos | se publican **desde el teléfono**: ficha del ejercicio → **Publish** (TD-140) |
| Lo que entrenó | `SessionLog` → snapshots en `Documents/MASTER/` |
| Cómo se sintió | `docs/coach-log.md` |
| Lo que busca a largo plazo, y su pesaje | `docs/objetivos.md` |
| Todo lo de NIKO | `docs/niko.md` |

Su historial se lee del respaldo de su teléfono. **El de NIKO también, desde TD-126:** su
teléfono sube cada sesión de un training asignado, el del coach las baja al sincronizar, y
quedan en el mismo respaldo del coach bajo `athleteSessions`. Si ahí no aparece una sesión que
ella dice haber hecho, lo primero es mirar si su teléfono tiene la versión con TD-126 y si ese
training estaba asignado.
