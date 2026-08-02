# Athletic

App Android (Kotlin + Jetpack Compose, Material Design 3) para crear y ejecutar
rutinas de entrenamiento con un player tipo "timer con intervalos".

App Android (Kotlin + Jetpack Compose, Material Design 3) para crear y ejecutar
rutinas de entrenamiento con un player tipo "timer con intervalos".

> Estado actual del repo: **compilando y publicando** (Fases 0–8 de la hoja de
> ruta). El código de la app está en el paquete `com.athletic`
> (`app/src/`): núcleo de dominio, servicio del player, UI completa, i18n solo-EN,
> ajustes, branding (wordmark MASTER + ícono de launcher) e historial/drag-reorder.
> Ver `docs/` y `to-do.md`.

## Por qué preservar la lógica existente

La app contiene lógica sutil y cara de reconstruir bien sin regresiones:

- Servicio en primer plano robusto ante muerte del proceso (restaura estado).
- Rotación **independiente por workout** con idempotencia.
- Máquina de pasos del player (auto/manual, peso por serie, feedback y sugerencias).
- Persistencia + serialización JSON ya probada.

## Jerarquía del dominio

`Training` (lo que se ejecuta de corrido) > `Workout` (bloque: Warmup, Cardio,
Lower…) > `Exercise` (unidad mínima). Los workouts pueden ser **rotativos** (varias
variantes que se alternan por corrida).

## Decisiones de producto (definidas)

- **Player cubre todo**: no habrá timer simple suelto por ahora.
- **Idioma: solo inglés** (se descarta el bilingüe).
- **MVP = todo**: entran todas las features actuales de Athlete + pendientes.
- **Wordmark = TIMES** (custom, vectorial derivado de Wallpoet), no el de Athlete.

## Documentación

- `docs/athletic-forge.md` — sistema de verificación y to-do (leer antes de cambiar).
- `docs/forge-todo.json` — fuente de verdad del to-do (regenerar con `forge-status.ps1`).
- `docs/hoja-de-ruta.md` — historial del proyecto por fases + decisiones de producto.
- `branding/wordmark/` — wordmark **MASTER** completo (pipeline + fuente Wallpoet
  + previews); el de Athlete queda en `legacy-athlete/`.
- `to-do.md` — generado automáticamente por `forge-status.ps1` desde `forge-todo.json`.

## Entorno / build

- **NO existe `gradlew`** en el repo; se compila con el Gradle 9.4.1 cacheado.
- `JAVA_HOME = C:\Program Files\Android\Android Studio\jbr`.
- Versionado: +1 por cada APK generado; APK release firmado con la clave debug.

El código compila con `verify-compile.ps1` (`compileReleaseKotlin`); la
verificación en dispositivo y el APK release están completados (ver hoja de ruta).

## Audio

Los beeps (`beep_second.ogg`, `beep_work.ogg`, `beep_finish.ogg`, `beep_rest.ogg`)
están normalizados a **-14 LUFS** con `ffmpeg loudnorm` (true peak limit -1 dBTP),
al mismo nivel de loudness percibido que Spotify. Esto asegura que los beeps no
suennen invasivos cuando hay musica de fondo.

- Originales respaldados en `app/src/main/res/raw/originals/`.
- `MediaPlayer.setVolume()` no tiene efecto en el dispositivo (Samsung S26), por lo
  que el control de volumen por etapa se removio. El volumen real lo determina la
  normalizacion LUFS del archivo.
- El cue de transicion respeta el switch "Alarm" por etapa (`StageConfig.alarm`) y
  usa `beepSoundUri` de la etapa con `beep_work.ogg` como default.

## Fuente del wordmark

El wordmark **MASTER** se deriva de la fuente **Wallpoet** mediante un pipeline
Python (ver `branding/wordmark/README.md`). El pipeline transforma glifos
(rotar, puentear cortes stencil, barras inclinadas) y los incrusta en
`ui/MasterWordmark.kt` como path data.
