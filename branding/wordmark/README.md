# Wordmark — TIMES (custom, elegido para Athletic)

El wordmark elegido para Athletic es el de **TIMES** (custom, vectorial), NO el de
Athlete. Athlete era "solo una fuente" (Neuropol) con un ícono hexagonal; TIMES es
un wordmark **construido a mano** derivando y transformando los glifos de la fuente
**Wallpoet**. Aquí está TODO el flujo de cómo se hizo, para poder regenerarlo y/o
adaptarlo (p. ej. rebrandizar el texto a "ATHLETIC").

> El branding de Athlete (Neuropol + `AthleteTabIcon`) se conserva solo como
> referencia en `legacy-athlete/`.

## Contenido

- `TimesWordmark.kt` — componente Compose final: dibuja el wordmark con los
  **path data ya incrustados** (no usa la fuente en runtime). La "M" resalta en el
  color de acento; el resto en `textPrimary`. Se dimensiona por `height` y mantiene
  proporción con `aspectRatio(VW/VH)` (VW≈3432.86, VH=575).
- `wallpoet_regular.ttf` — fuente **fuente-origen** de los glifos (solo necesaria
  para REGENERAR el wordmark, no en runtime).
- `tools/` — pipeline offline (solo stdlib de Python, sin pip/red por CrowdStrike):
  - `wallpoet_extract.py` — parser TrueType mínimo (stdlib `struct`, sin `fonttools`).
    Lee `head`/`maxp`/`hhea`/`cmap`(fmt 4)/`loca`/`glyf`/`hmtx` y extrae el contorno
    SVG de cada glifo (T, I, M, E, S, `\`). Salida: `wallpoet_glyphs.json`.
  - `wordmark_build.py` — construye el wordmark: `offset_path` (desplaza X),
    `map_path` (aplica `fn(x,y)`: rotar/escalar/voltear), layout por `advanceWidth`
    + tracking. Genera previews HTML y exporta los path finales (y-down) a
    `wordmark_paths.txt`.
  - `wallpoet_glyphs.json` — contornos extraídos (salida del paso 1).
  - `wordmark_paths.txt` — path data finales en y-down (lo que se incrusta en Kotlin).
  - `wordmark-preview2.html`, `wordmark-preview5.html` — previsualizaciones (abrir
    con doble clic en el navegador).
- `legacy-athlete/` — branding anterior de Athlete (Neuropol + `AthleteTabIcon.kt`),
  solo referencia.

## Cómo se construyó TIMES (flujo offline, sin descargas)

1. **Extraer contornos** (`tools/wallpoet_extract.py`): parser TrueType con stdlib
   puro. Glifos simples (flags con REPEAT, curvas cuadráticas con on-curve
   implícitos). Salida `wallpoet_glyphs.json` con el path SVG de cada glifo en
   unidades de fuente (eje **Y hacia ARRIBA**, como TrueType).
2. **Construir el wordmark** (`tools/wordmark_build.py`): compone TIMES y exporta
   los paths finales ya en **y-down** (pantalla) a `wordmark_paths.txt`, más
   previews HTML.
3. **Render en Compose** (`TimesWordmark.kt`): los path data se incrustan como
   constantes (`REST_D`, `M_D`) y se dibujan con
   `PathParser().parsePathString(d).toPath()` + `Canvas`/`drawPath`. Para color
   dinámico se separan en varios paths y se escala con
   `scale(s, s, pivot = Offset.Zero)`, `s = size.height / VH`.

## Decisiones de diseño del wordmark TIMES

Datos de **Wallpoet** (`wallpoet_regular.ttf`): unitsPerEm **1000**, cap height
**575**, grosor de trazo (stem de la I) **126**.

- **T** y **M**: glifos de Wallpoet **sin cambios**.
- **E** = la **M rotada 90° antihorario (ccw)** y reajustada a la caja de mayúscula
  (fue la solución para una E coherente con el estilo stencil).
- **S**: glifo de Wallpoet con el **corte vertical puenteado** (continuo).
- **I** = barra inclinada `\` a altura completa de mayúscula (parámetro *lean* = 180,
  grosor = 126). Rellenado `fillType` **NonZero**.
- **M** en color de **acento**; el resto en `textPrimary`. Tracking 0.

**Técnicas clave**
- Fuente TrueType usa **y-UP**; se convierte a y-down con `y' = CAP - y`.
- **Puentear cortes** de fuentes stencil: no tapar con un rectángulo de altura
  completa (taparía los contadores); añadir rectángulos **solo donde cruzan las
  barras horizontales** (por rango Y).
- **Reutilizar un glifo rotado** como otra letra: rotar (cw `(y,-x)`, ccw `(-y,x)`,
  180 `(-x,-y)`), reescalar a la altura de mayúscula y trasladar a lsb/baseline.

## Cómo regenerar (desde la raíz de un checkout con la fuente)

Los scripts asumen rutas relativas `tools/...` y `app/src/main/res/font/wallpoet_regular.ttf`.
Al integrarlos en Athletic, ajustar esas rutas o ejecutar desde una carpeta con esa
estructura. Requiere solo Python (stdlib):

```
python tools/wallpoet_extract.py      # -> tools/wallpoet_glyphs.json
python tools/wordmark_build.py        # -> tools/wordmark_paths.txt + previews HTML
```

Luego copiar `WIDTH`/`HEIGHT` y los path `REST`/`M` de `wordmark_paths.txt` a las
constantes de `TimesWordmark.kt`.

## Al integrar en Athletic

1. Portar `TimesWordmark.kt` al paquete/UI de Athletic (ajustar `import` de `AppTheme`).
2. Si se quiere el texto "ATHLETIC" (no "TIMES"), regenerar con los glifos
   A-T-H-L-E-T-I-C aplicando las mismas técnicas (puentear cortes, `\` como I, etc.)
   — ver `to-do.md` (reubicar/rebrandizar wordmark).
3. La fuente Wallpoet solo se necesita para regenerar; en runtime basta el `.kt`.

