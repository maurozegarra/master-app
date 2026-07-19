#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Hornea las transformaciones del wordmark TIMES en un .ttf nuevo:
- E = M rotada 90° antihorario (ccw), reescalada a cap height (575), lsb=80.
- I = barra inclinada "\\" (lean=180, grosor=126), a altura completa de mayuscula.
- S = S original con cortes verticales puenteados (rectangulos anadidos).

Requiere: fonttools (pip install fonttools)
Uso: python tools/build_athletic_font.py
Salida: wallpoet_athletic.ttf
"""
import math
from fontTools.ttLib import TTFont
from fontTools.pens.ttGlyphPen import TTGlyphPen

SRC = "wallpoet_regular.ttf"
DST = "wallpoet_athletic.ttf"

CAP = 575       # altura de mayuscula (unidades de fuente)
STROKE = 126    # grosor de trazo (stem de la I de Wallpoet)
LEAN = 180      # inclinacion de la barra I

# Puentes para la S (rellenan el corte vertical donde cruzan las barras horizontales)
# Coordenadas y-UP, en unidades locales del glifo (antes de offset)
S_BRIDGES = [
    # (x0, y0, x1, y1) como rectangulos cerrados
    (390, 575, 435, 457),   # puente superior
    (390, 344, 435, 227),   # puente medio
    (390, 113, 439, 0),     # puente inferior
]


def load_font():
    return TTFont(SRC)


def get_glyph_contours(font, glyph_name):
    """Devuelve lista de contornos; cada contorno es lista de (x, y, is_on_curve)."""
    glyf = font["glyf"]
    glyph = glyf[glyph_name]
    if glyph.numberOfContours < 0:
        raise ValueError(f"Glyfo {glyph_name} es compuesto, no soportado")
    contours = []
    pts = []
    s = 0
    for e in glyph.endPtsOfContours:
        for i in range(s, e + 1):
            pts.append((glyph.coordinates[i][0], glyph.coordinates[i][1],
                        bool(glyph.flags[i] & 0x01)))
        contours.append(pts)
        pts = []
        s = e + 1
    return contours


def contours_to_path(contours):
    """Convierte contornos a path SVG (string) para depuracion."""
    parts = []
    for c in contours:
        if not c:
            continue
        parts.append(f"M{c[0][0]} {c[0][1]}")
        i = 1
        while i < len(c):
            x, y, on = c[i]
            if on:
                parts.append(f"L{x} {y}")
                i += 1
            else:
                # curva cuadratica
                if i + 1 < len(c) and c[i + 1][2]:
                    parts.append(f"Q{x} {y} {c[i+1][0]} {c[i+1][1]}")
                    i += 2
                else:
                    # punto medio implicito
                    nx = c[(i + 1) % len(c)][0]
                    ny = c[(i + 1) % len(c)][1]
                    mx = (x + nx) / 2
                    my = (y + ny) / 2
                    parts.append(f"Q{x} {y} {mx} {my}")
                    i += 1
        parts.append("Z")
    return " ".join(parts)


def transform_point(x, y, fn):
    return fn(x, y)


def transform_contours(contours, fn):
    """Aplica fn(x,y) -> (nx, ny) a todos los puntos."""
    result = []
    for c in contours:
        nc = [(*fn(x, y), on) for x, y, on in c]
        result.append(nc)
    return result


def bbox_of(contours):
    xs = [p[0] for c in contours for p in c]
    ys = [p[1] for c in contours for p in c]
    return min(xs), min(ys), max(xs), max(ys)


def build_E_from_M(font):
    """E = M rotada 90° ccw, reescalada a CAP, lsb=80.
    TrueType es y-UP; la rotacion ccw es (-y, x)."""
    contours = get_glyph_contours(font, "M")

    # Rotar 90° ccw: (x, y) -> (-y, x)
    rot = lambda x, y: (-y, x)
    rotated = transform_contours(contours, rot)

    # Bbox del rotado
    minx, miny, maxx, maxy = bbox_of(rotated)
    scale = CAP / (maxy - miny)
    lsb = 80

    # Reescalar y trasladar a lsb, baseline 0
    def refit(x, y):
        return ((x - minx) * scale + lsb, (y - miny) * scale)
    final = transform_contours(rotated, refit)

    # El ancho del glifo sera (maxx - minx) * scale + lsb + 80 (rsb)
    width = (maxx - minx) * scale + lsb + 80
    return final, int(round(width))


def build_I_slash():
    """I = barra inclinada \\ con lean=180, grosor=126, lsb=80.
    TrueType es y-UP: y=0 es baseline, y=575 es top.
    La barra va de top-left a bottom-right (\\)."""
    x0 = 80
    top_l, top_r = x0, x0 + STROKE       # y = CAP (top)
    bot_l, bot_r = x0 + LEAN, x0 + STROKE + LEAN  # y = 0 (bottom)

    # Un solo contorno: 4 puntos, todos on-curve
    contour = [
        (top_l, CAP, True),
        (top_r, CAP, True),
        (bot_r, 0, True),
        (bot_l, 0, True),
    ]
    # Ancho: posicion del borde derecho + bearing
    width = bot_r + 120  # avance aproximado (slash_adv = STROKE + LEAN + 120)
    return [contour], int(round(width))


def build_S_bridged(font):
    """S original + puentes rectangulares para tapar cortes verticales."""
    contours = get_glyph_contours(font, "S")

    # Anadir puentes como contornos rectangulares
    for x0, y0, x1, y1 in S_BRIDGES:
        rect = [
            (x0, y0, True),
            (x1, y0, True),
            (x1, y1, True),
            (x0, y1, True),
        ]
        contours.append(rect)

    # Mantener el advanceWidth original de S
    width = font["hmtx"]["S"][0]
    return contours, width


def contours_to_glyph(contours, glyph_name, width, font):
    """Crea un objeto TTGlyphPen y dibuja los contornos, devuelve el glyf."""
    pen = TTGlyphPen(font.getGlyphSet())
    for c in contours:
        if not c:
            continue
        # Mover al primer punto
        pen.moveTo((c[0][0], c[0][1]))
        i = 1
        while i < len(c):
            x, y, on = c[i]
            if on:
                pen.lineTo((x, y))
                i += 1
            else:
                # curva cuadratica: necesita siguiente punto on-curve
                if i + 1 < len(c) and c[i + 1][2]:
                    pen.qCurveTo((x, y), (c[i + 1][0], c[i + 1][1]))
                    i += 2
                else:
                    # punto medio implicito (off-curve seguido de off-curve)
                    nx = c[(i + 1) % len(c)][0]
                    ny = c[(i + 1) % len(c)][1]
                    mx = (x + nx) / 2
                    my = (y + ny) / 2
                    pen.qCurveTo((x, y), (mx, my))
                    i += 1
        pen.closePath()
    glyph = pen.glyph()
    return glyph, width


def main():
    font = load_font()
    glyf = font["glyf"]
    hmtx = font["hmtx"]

    # --- E = M rotada ccw ---
    e_contours, e_width = build_E_from_M(font)
    e_glyph, e_width = contours_to_glyph(e_contours, "E", e_width, font)
    glyf["E"] = e_glyph
    hmtx["E"] = (e_width, 0)
    print(f"E: {e_width} units, {len(e_contours)} contours")

    # --- I = barra inclinada ---
    i_contours, i_width = build_I_slash()
    i_glyph, i_width = contours_to_glyph(i_contours, "I", i_width, font)
    glyf["I"] = i_glyph
    hmtx["I"] = (i_width, 0)
    print(f"I: {i_width} units, {len(i_contours)} contours")

    # --- S = S puenteadas ---
    s_contours, s_width = build_S_bridged(font)
    s_glyph, s_width = contours_to_glyph(s_contours, "S", s_width, font)
    glyf["S"] = s_glyph
    hmtx["S"] = (s_width, 0)
    print(f"S: {s_width} units, {len(s_contours)} contours")

    # --- Normalizar espaciado: lsb=rsb=90 para gap consistente de 180 ---
    TARGET_LSB = 90
    TARGET_RSB = 90
    for name in "MASTER":
        g = glyf[name]
        if g.numberOfContours < 0:
            continue
        x_min = min(g.coordinates[i][0] for i in range(len(g.coordinates)))
        x_max = max(g.coordinates[i][0] for i in range(len(g.coordinates)))
        dx = TARGET_LSB - x_min
        for i in range(len(g.coordinates)):
            g.coordinates[i] = (g.coordinates[i][0] + dx, g.coordinates[i][1])
        new_aw = (x_max + dx) + TARGET_RSB
        hmtx[name] = (new_aw, TARGET_LSB)
        print(f"  {name}: xMin={x_min} -> {TARGET_LSB}, aw={new_aw}")

    # Guardar
    font.save(DST)
    print(f"\nOK -> {DST}")


if __name__ == "__main__":
    main()
