# Athletic

App Android (Kotlin + Jetpack Compose, Material Design 3) para crear y ejecutar
rutinas de entrenamiento con un player tipo "timer con intervalos".

Nace como **independización de la sección _Athlete_** del proyecto `mini-timer`
(que agrupaba varias sub-apps: Times, Athlete, Clock/OSD y Water). La sub-app
Clock ya se independizó por separado; este repo rescata **solo Athlete** mediante
un *carve-out* (extraer y podar), preservando la lógica que ya funciona en lugar
de reescribir desde cero.

> Estado actual del repo: **carve-out completado y compilando** (Fases 0–7 de la
> hoja de ruta). El código de la app ya está portado al paquete `com.athletic`
> (`app/src/`): núcleo de dominio, servicio del player, UI completa, i18n solo-EN,
> ajustes, branding (wordmark TIMES + ícono de launcher) e historial/drag-reorder.
> **Pendiente (Fase 8):** verificación en dispositivo y generación del APK release.
> Aquí también viven los hallazgos, la hoja de ruta y los assets de branding.
> Ver `docs/` y `to-do.md`.

## Por qué carve-out y no reescribir de cero

La sección Athlete de `mini-timer` **funciona** y contiene lógica sutil y cara de
reconstruir bien sin regresiones:

- Servicio en primer plano robusto ante muerte del proceso (restaura estado).
- Rotación **independiente por workout** con idempotencia.
- Máquina de pasos del player (auto/manual, peso por serie, feedback y sugerencias).
- Persistencia + serialización JSON ya probada.

A diferencia de Clock (que se rehízo de cero porque su núcleo estaba roto), aquí
lo correcto es **extraer y limpiar**, no empezar en blanco.

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

- `docs/hallazgos-athlete.md` — inventario detallado de lo que YA existe.
- `docs/hoja-de-ruta.md` — roadmap del carve-out por fases + decisiones de producto.
- `branding/wordmark/` — wordmark **TIMES** completo (pipeline + `TimesWordmark.kt`
  + fuente Wallpoet + previews); el de Athlete queda en `legacy-athlete/`.
- `to-do.md` — pendientes (incluye reubicar/rebrandizar el wordmark y sugerencias).

## Entorno / build (heredado de mini-timer)

Mismo entorno que `mini-timer` (equipo con CrowdStrike, sin descargas de red):

- **NO existe `gradlew`** en el repo; se compila con el Gradle 9.4.1 cacheado.
- `JAVA_HOME = C:\Users\mzegarra_ide\Downloads\android-studio\jbr`.
- Sin `pip install`/`npm install` de red ni descargas de binarios: usar solo lo ya
  instalado y la librería estándar.
- Versionado: +1 por cada APK generado; APK release firmado con la clave debug.

El código ya está portado y compila con `verify-compile.ps1` (`compileReleaseKotlin`);
falta la verificación en dispositivo y el APK release (ver hoja de ruta, Fase 8).

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

## Fuente original

`mini-timer` en `C:\Users\mzegarra_ide\code\mini-timer` (paquete
`com.minitimer`, sección Athlete en `com.minitimer.ui.athlete` + `AthleteViewModel`,
`WorkoutStore`, `WorkoutPlayerService`, `model/Workout.kt`, `ExerciseCatalog`,
`AthleteDefaults`).
