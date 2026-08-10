# Handoff — Notificación estilo YouTube (TD-048, RESUELTO)

> **Resolución (2026-08-09, v1.0.152):** Implementado con **Live Update de
> Android 16** (`Notification.ProgressStyle` + `setRequestPromotedOngoing(true)`
> + `setShortCriticalText(...)` + permiso `POST_PROMOTED_NOTIFICATIONS`),
> detrás de guard `SDK_INT >= 36`. El chip de la Now Bar muestra el tiempo
> restante en vivo y solo aparece en segundo plano. El camino se encontró por
> vía empírica (dumpsys en el S26 Ultra): el comportamiento de YouTube NO viene
> de la importancia del canal sino de la **Now Bar de One UI**, que SystemUI
> crea para apps con MediaSession o Live Updates. Los approaches A–G abajo
> quedan como registro del análisis.

## Objetivo

El ícono de MASTER en la barra de estado debería comportarse como el de YouTube:
visible **sólo cuando la app está en segundo plano** y hay un training en curso
(corriendo o pausado). Cuando el usuario está dentro de la app viendo el player,
el ícono no debería verse — ya tiene el player en pantalla.

## Estado actual

`WorkoutPlayerService` es un foreground service (FGS) que arranca al iniciar un
training. Llama a `startForegroundCompat(buildNotification())` en `onCreate`
(línea 69), lo que muestra inmediatamente una notificación con el ícono
`ic_stat_timer` en la barra de estado.

- Canal único `CHANNEL_ID` con `IMPORTANCE_DEFAULT` (línea 497).
- `setOngoing(true)` (línea 437), `VISIBILITY_PUBLIC` (línea 439).
- `setForegroundServiceBehavior(FOREGROUND_SERVICE_IMMEDIATE)` (línea 456).
- Se actualiza en cada tick y en cada cambio de paso.
- Al terminar/stop: `stopForeground(STOP_FOREGROUND_REMOVE)` + notificación
  final o cancelación.

**Configuración relevante del proyecto:**
- `targetSdk = 36` (Android 16) → aplican **todas** las restricciones modernas de FGS.
- `minSdk = 26`.
- Manifest: `android:foregroundServiceType="specialUse"` + permiso
  `FOREGROUND_SERVICE_SPECIAL_USE`.

**Problema:** el ícono aparece siempre, incluso con la app en primer plano.

---

## Análisis (CORREGIDO tras investigación)

> El análisis inicial de este handoff proponía usar dos canales de notificación
> con distinta importancia. **Esa propuesta es inviable.** Se corrige abajo.

### Restricción fundamental

Un FGS **exige** una notificación visible. No se puede ocultar el ícono
mientras el service esté en primer plano.

### Por qué el approach de dos canales NO funciona

Documentación oficial de `NotificationManager`:

- **`IMPORTANCE_LOW`**: "Shows in the shade, **and potentially in the status
  bar**". Es decir, **sí muestra el ícono** en la barra de estado. Sólo se
  oculta si el *usuario* activó "ocultar iconos silenciosos"
  (`shouldHideSilentStatusBarIcons()`), que es una preferencia del sistema, no
  controlable por la app.
- **`IMPORTANCE_MIN`**: sí oculta el ícono, pero la doc dice explícitamente:
  *"This should not be used with `Service.startForeground()` [...] If you do
  this, as of Android O, **the system will show a higher-priority notification
  about your app running in the background**."* → el resultado es **peor**.
- Además, la importancia de un canal **no se puede cambiar** por código una vez
  creado (sólo bajarla, y sólo si el usuario no la tocó).

**Conclusión: descartado.**

### Único approach viable: promover/degradar el FGS según el ciclo de vida

Es lo que realmente hace YouTube: **no corre un FGS mientras la app está en
primer plano** (no lo necesita, hay una Activity visible que mantiene vivo el
proceso). Sólo promueve el service a foreground cuando la app pasa a segundo
plano.

- **App en primer plano:** `stopForeground(STOP_FOREGROUND_REMOVE)` → el service
  sigue vivo como service normal, sin notificación ni ícono. Es seguro porque
  el proceso tiene importancia "foreground" mientras hay una Activity visible.
- **App a segundo plano:** `startForeground(...)` → aparece la notificación y
  el ícono, y el proceso queda protegido.

### Riesgo serio de este approach

Desde **Android 12 (API 31)**, una app en segundo plano **no puede** promover un
service a foreground: el sistema lanza `ForegroundServiceStartNotAllowedException`.

Hay una exención documentada: *"Your app transitions from a user-visible state,
such as an activity"* — que en teoría cubre nuestro caso. **Pero** hay reportes
consistentes de que la excepción se lanza igual al llamar `startForeground()`
desde `Activity.onStop()` (`mAllowStartForeground false`), presumiblemente por
timing: la ventana de gracia es corta y no está garantizada.

**Consecuencia de que falle:** el service no queda protegido y el sistema puede
matarlo → **se pierde el training en curso**. Este es exactamente el tipo de
regresión que hay que evitar.

### Mitigaciones posibles

1. **Promover en `onPause()` en vez de `onStop()`** — señal más temprana, la app
   todavía se considera visible. Es la recomendación que circula para este caso.
2. **`try/catch` de `ForegroundServiceStartNotAllowedException`** — si falla,
   reintentar o dejar el service en foreground permanentemente (degradar al
   comportamiento actual) en vez de crashear.
3. **Flag de "modo seguro"**: si falla una vez, dejar de degradar el FGS por el
   resto de la corrida.

### Edge cases a cubrir

- **Pantalla se apaga durante el training** → `onPause`/`onStop` → debe
  promover a FGS. Caso muy común (TD-014 keep-screen-on sigue pendiente).
- **App abierta → pausa el timer → app a segundo plano** → ícono debe aparecer
  (training en curso aunque pausado).
- **Segundo plano → usuario vuelve a la app** → ícono debe desaparecer.
- **Service restaurado tras muerte del proceso** (`onCreate` → `restore()`) → si
  la app no está visible, debe arrancar como FGS.
- **Usuario cierra la app desde recientes** → `onPause` ya disparó la promoción.
- **Ida y vuelta rápida primer/segundo plano** → parpadeo del ícono; posible
  rate-limiting del sistema.
- **Steps manuales (reps)** → el service sigue activo, mismo comportamiento.
- **Android 14+ `specialUse`** → verificar que la re-promoción con
  `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` no falle.

---

## Recomendación

Este cambio toca el componente más crítico y frágil de la app (el service que
sostiene el training) para un beneficio puramente estético. La relación
riesgo/beneficio es mala:

- El beneficio es cosmético: no ver un ícono mientras usás la app.
- El riesgo es funcional: perder un training en curso si falla la promoción.

**Sugerencia: no implementarlo**, o implementarlo sólo con las tres mitigaciones
y una batería de pruebas en dispositivo antes de dar por bueno.

## Decisión pendiente del usuario

- **Opción A — No hacerlo.** Cerrar el TD como "won't do" con el análisis
  documentado.
- **Opción B — Hacerlo con mitigaciones.** Implementar promoción/degradación con
  `onPause`/`onResume`, `try/catch`, y flag de modo seguro. Probar todos los
  edge cases arriba en el S26 Ultra antes de aprobar.
- **Opción C — Investigar más** antes de decidir (ej. prototipo descartable para
  medir si la excepción se dispara en el S26 Ultra).

## Siguientes pasos (si se elige B)

1. Registrar TD-048 en `docs/forge-todo.json`.
2. Agregar señal de ciclo de vida: comandos `APP_FOREGROUND`/`APP_BACKGROUND` en
   `PlayerBus`, emitidos desde `MainActivity` en `onResume`/`onPause`.
3. En el service: `promoteToForeground()` / `demoteFromForeground()` con
   `try/catch` y flag `foregroundDowngradeDisabled`.
4. `onCreate`: arrancar como FGS siempre (no sabemos si hay UI visible todavía);
   degradar al recibir `APP_FOREGROUND`.
5. `verify-compile.ps1` + prueba exhaustiva en dispositivo.
6. Commit `feat: TD-048 ...` sólo tras aprobación del usuario.

---

## Contexto de la sesión

- Fecha: 2026-08-09
- TDs completados hoy: TD-045, TD-046, TD-047
- Versión actual: v1.0.150
- 22 commits ahead de origin/main (sin push)
- El usuario pidió "mucho análisis y vamos con calma" por malas experiencias
  previas con este tipo de cambio.
