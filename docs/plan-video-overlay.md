# Plan — TD-078: el vídeo se queda con la pantalla

## Contexto

Con el training de prueba *On Your Marks* (10 clips verticales 1080×1920 sacados de
Freeletics) el vídeo del player se ve pequeño. No es sensación: está medido.

Pantalla del S26: **384 × 832 dp** (1080×2340 a 450 dpi).

`ExerciseVideo` usa `aspectRatio(ratio, matchHeightConstraintsFirst = true)`: toma la
**altura** disponible y de ahí deduce el ancho. Con un 9:16 el ancho es sólo
`altura × 0.5625`. Y la altura sobrante es poca, porque el `Column` de `RunningView`
la reparte antes:

| Concepto | dp |
|---|---:|
| insets (status + gestos) | ~64 |
| padding 20 arriba/abajo | 40 |
| `Spacer` que libra la barra superpuesta | 44 |
| título (2 líneas @ 48sp, alto fijo) | 104 |
| spacers 12 + 12 | 24 |
| reloj (84sp) | ~101 |
| spacer + controles + "Next" | 116 |
| **ocupado** | **~493** |
| **queda para el vídeo** | **~339 de alto → ~190 de ancho** |

Unos **190 × 339 dp**: la mitad del ancho, **~19 % del área**. Como cada dp de alto sólo
devuelve 0.56 dp de ancho, recortar espacios rinde poquísimo: apretarlo todo daría
~1.4×, y a cambio de una maqueta peor.

La causa real es estructural: **el vídeo y el reloj compiten por la misma altura** en una
sola columna.

## Decisión

**Opción 1: el reloj, los controles y el título se superponen al vídeo**, que pasa a
quedarse con el área entera. Es lo que hace Freeletics. Descartadas:

- *Encoger lo que compite*: 1.4×, no resuelve la sensación.
- *Vídeo de fondo recortado*: la pantalla (0.46) es más estrecha que el clip (0.56), así
  que recortaría ~18 % por los lados, y obliga a renunciar al color de etapa como fondo.
- *Adaptativo vertical/horizontal*: **aplazado a soporte de tablets**, decisión del
  usuario. Ahora manda vertical.

Resultado esperado: **384 × 683 dp**, limitado ya por el ancho y no por el alto.
Unas **4× de área**.

## Lo que hay hoy, y que no es lo que parecía

Antes de tocar nada, dos hallazgos que cambian el punto de partida (**reportados, sin
corregir**):

1. **No hay auto-ocultado del OSD.** `MasterViewModel.osdNonce` (línea 129) se
   incrementa en `showPlayerControls()` y **no lo lee nadie**: es el temporizador que
   nunca se cableó.
2. **`playerControlsVisible` sólo tapa la `WorkoutProgressBar`** (PlayerScreen:522), la
   secundaria que aparece con más de un workout. Los controles de play/skip/check están
   siempre visibles.

Ninguna de las dos bloquea este plan, pero el plan **no puede apoyarse** en un
auto-hide que no existe.

## Diseño

`RunningView` deja de ser un `Column` que reparte altura y pasa a ser un `Box` de capas:

```
Box (fondo = color de etapa oscurecido 12%, gestos tap / drag horizontal)
├─ vídeo            -> ocupa el Box entero, centrado, aspectRatio del archivo
├─ scrim superior   -> degradado a color de etapa, para el título y la barra
├─ scrim inferior   -> degradado a color de etapa, para el reloj y los controles
├─ atenuado de pausa (el Box negro que ya existe)
└─ Column de chrome -> barra, título, (hueco elástico), reloj, controles, "Next"
```

Claves:

- **El color de etapa sigue siendo el fondo.** Es la señal de en qué fase estás, no
  decoración: lo dicen los comentarios del propio archivo. Los degradados van *hacia* ese
  color, no hacia negro, así que la fase se sigue leyendo en los bordes.
- **El vídeo no se recorta.** Sigue con `aspectRatio` del archivo; ahora el que limita es
  el ancho. En los bordes superior e inferior queda color de etapa, que es justo donde
  van los scrims.
- **El título se superpone.** Esto *refuerza* TD-074 en vez de romperlo: aquel pedía alto
  fijo para que el vídeo no saltara al cambiar de ejercicio; si el título sale del flujo,
  el vídeo deja de depender de él por completo. El ajuste de tamaño a dos líneas se queda
  como está.
- **El `Spacer(44)`** que libraba la barra superpuesta desaparece: el chrome ya se
  coloca por capas.

## Fases

Cada fase deja la app funcionando.

### Fase A — El vídeo se queda con el Box

Convertir el cuerpo de `RunningView` en capas y mover el vídeo al fondo, a tamaño
completo. Chrome todavía sin scrim, sólo comprobar que el vídeo crece y que nada se
descoloca. Es el paso que decide si el resto tiene sentido.

**Riesgo a verificar aquí:** `VideoLoop` es un `VideoView`, o sea un `SurfaceView`.
Por defecto la superficie va **por debajo** de la ventana, así que el contenido Compose
debería dibujarse encima sin tocar nada. Si no fuera así, se ve enseguida —el reloj
desaparecería tras el vídeo— y se resuelve con `setZOrderMediaOverlay(false)`. Se
comprueba en esta fase justamente porque invalidaría el enfoque.

### Fase B — Scrims y legibilidad

Degradados arriba y abajo hacia el color de etapa. Ajustar alturas y opacidades para que
el reloj de 84sp se lea sobre un fotograma claro sin apagar el vídeo. El atenuado de
pausa se queda **encima del vídeo y debajo del chrome**, para que en pausa el reloj siga
siendo lo más legible.

### Fase C — Repaso de gestos

El tap (alterna OSD) y el arrastre horizontal (check / anterior) viven en el `Box` raíz.
Con el vídeo a pantalla completa la zona de arrastre crece, que es una mejora, pero hay
que confirmar que el `SurfaceView` no se come los eventos y que los botones
superpuestos siguen ganando el tap.

### Fase D — *opcional, sólo si lo pides*

Cablear el auto-ocultado que `osdNonce` iba a hacer: el chrome se desvanece a los N
segundos y el vídeo queda limpio. Encaja de maravilla con esta maqueta, pero **es otro
alcance** y no entra salvo que lo digas.

## Qué no se toca

- `ui/VideoLoop.kt` — ni `VideoLoop` ni `ExerciseVideo` cambian. El foco de audio
  (TD-075) y la lógica del primer fotograma se quedan como están.
- El ajuste de tamaño del título (TD-074).
- `step.showVideo` (TD-070): un ejercicio sin vídeo mantiene la maqueta actual, con el
  reloj en el hueco elástico. Esa rama del `else` sigue viva.

## Cómo se prueba

Con *On Your Marks*, que ya está en el teléfono:

1. El vídeo ocupa el ancho completo y se ve **unas 4× más grande**.
2. El reloj se lee sobre fotogramas claros y oscuros.
3. El color de etapa sigue distinguiéndose al cambiar PREP → WORK → REST.
4. En pausa, la pantalla se atenúa y el reloj sigue siendo lo más legible.
5. Tap alterna la barra; arrastrar a izquierda/derecha sigue haciendo check / anterior.
6. Un ejercicio **sin** vídeo se ve exactamente como antes.
7. Nombres largos (*Deep Squat Hold*, *Ground Supermen*) siguen en dos líneas como mucho
   y el vídeo no salta al cambiar de ejercicio.
