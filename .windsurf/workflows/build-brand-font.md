---
description: Crear una fuente .ttf modificada horneando transformaciones de glifos (rotaciones, puentes, barras) con fonttools, para usar como wordmark reutilizable
---

# Crear una fuente de marca (.ttf) con glifos transformados

Método para construir una fuente `.ttf` propia a partir de una fuente existente,
modificando glifos individuales (rotar, puentear cortes stencil, reemplazar por
barras inclinadas) y normalizando el espaciado. El resultado es una fuente
autocontenida que se puede usar en cualquier proyecto con `@font/` o
`FontFamily(Font(R.font.xxx))` — sin dibujar paths a mano en runtime.

## Cuándo usarlo

- Necesitas un wordmark con letras transformadas (p. ej. E = M rotada, I = barra
  inclinada `\`, S con cortes puenteados) y quieres que la fuente las renderice
  directamente al escribir el texto.
- Quieres reutilizar la fuente en múltiples proyectos sin recalcular paths.
- Las transformaciones son por glifo y se pueden expresar como operaciones
  geométricas (rotar, escalar, trasladar, añadir rectángulos).

## Requisitos

- **Python 3** + **fonttools** (`pip install fonttools`)
- La fuente origen en `.ttf` (no `.otf` — fonttools maneja `glyf` de TrueType)

## Pipeline

### 1. Extraer glifos base (opcional, para inspección)

Si necesitas ver los contornos de la fuente origen, usa `fonttools` para listar
métricas y bbox de cada glifo:

```python
from fontTools.ttLib import TTFont
f = TTFont("fuente_origen.ttf")
g = f["glyf"]
h = f["hmtx"]
for c in "MASTER":
    glyph = g[c]
    print(f"{c}: aw={h[c][0]} lsb={h[c][1]} "
          f"bbox=[{glyph.xMin}, {glyph.yMin}, {glyph.xMax}, {glyph.yMax}]")
```

### 2. Script de transformación

Estructura del script (`build_brand_font.py`):

```
SRC = "fuente_origen.ttf"
DST = "fuente_marca.ttf"
CAP = 575       # altura de mayúscula (unitsPerEm de la fuente)
```

#### Transformaciones por glifo

Cada transformación produce una lista de contornos, donde cada contorno es una
lista de tuplas `(x, y, is_on_curve)` en coordenadas TrueType (y-UP).

- **Rotar glifo**: `rot = lambda x, y: (-y, x)` para 90° ccw. Luego reescalar a
  CAP y trasladar a lsb deseado.
- **Barra inclinada** (`\`): 4 puntos on-curve formando un rectángulo
  inclinado. `lean` controla la inclinación, `STROKE` el grosor.
- **Puentear cortes stencil**: añadir rectángulos cerrados como contornos
  adicionales solo donde las barras horizontales cruzan el corte vertical.

#### Convertir contornos a glifo TrueType

Usar `TTGlyphPen` de fonttools para reconstruir el glifo:

```python
from fontTools.pens.ttGlyphPen import TTGlyphPen

pen = TTGlyphPen(font.getGlyphSet())
for contour in contours:
    pen.moveTo(contour[0][:2])
    # ... lineTo / qCurveTo según on-curve/off-curve
    pen.closePath()
glyph = pen.glyph()
```

#### Asignar al font

```python
glyf["E"] = e_glyph
hmtx["E"] = (advance_width, lsb)
```

### 3. Normalizar espaciado

Para que el gap entre letras sea consistente, normalizar lsb y rsb de cada glifo
a un valor fijo (p. ej. 90/90 → gap total de 180 unidades):

```python
TARGET_LSB = 90
TARGET_RSB = 90
for name in "MASTER":
    g = glyf[name]
    x_min = min(g.coordinates[i][0] for i in range(len(g.coordinates)))
    x_max = max(g.coordinates[i][0] for i in range(len(g.coordinates)))
    dx = TARGET_LSB - x_min
    for i in range(len(g.coordinates)):
        g.coordinates[i] = (g.coordinates[i][0] + dx, g.coordinates[i][1])
    new_aw = (x_max + dx) + TARGET_RSB
    hmtx[name] = (new_aw, TARGET_LSB)
```

### 4. Guardar y bundlear

```python
font.save("fuente_marca.ttf")
```

Copiar a `app/src/main/res/font/` del proyecto Android.

### 5. Usar en Compose

```kotlin
private val BrandFont = FontFamily(Font(R.font.fuente_marca))

@Composable
fun BrandWordmark(accent: Color, height: Dp = 28.dp) {
    val fontSize = height.value.sp
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = accent, fontFamily = BrandFont, fontSize = fontSize)) {
            append("M")
        }
        withStyle(SpanStyle(color = AppTheme.colors.textPrimary, fontFamily = BrandFont, fontSize = fontSize)) {
            append("AST")
        }
        withStyle(SpanStyle(color = accent, fontFamily = BrandFont, fontSize = fontSize)) {
            append("E")
        }
        withStyle(SpanStyle(color = AppTheme.colors.textPrimary, fontFamily = BrandFont, fontSize = fontSize)) {
            append("R")
        }
    }
    Text(text = text)
}
```

## Referencia: proyecto Athletic

- **Fuente origen**: `branding/wordmark/wallpoet_regular.ttf` (Wallpoet, unitsPerEm=1000, cap=575)
- **Script**: `branding/wordmark/tools/build_athletic_font.py`
- **Fuente generada**: `wallpoet_athletic.ttf` (E=M rotada ccw, I=barra `\`, S=puenteadas, espaciado normalizado 90/90)
- **Composable**: `app/src/main/java/com/athletic/ui/MasterWordmark.kt`
- **Tamaño en TopAppBar**: 28.dp de altura (~243.dp de ancho)

## Checklist

- [ ] fonttools instalado (`pip install fonttools`)
- [ ] Fuente origen es `.ttf` (no `.otf`)
- [ ] Transformaciones por glifo definidas (rotar, puentear, reemplazar)
- [ ] Espaciado normalizado (lsb/rsb consistentes)
- [ ] Fuente copiada a `res/font/`
- [ ] Composable usa `FontFamily(Font(R.font.xxx))` + `AnnotatedString` para color por letra
- [ ] Verificado en dispositivo (build + install + captura)
