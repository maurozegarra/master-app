#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Construye el wordmark "TIMES" en estilo Wallpoet:
- T y M: glifos de Wallpoet sin cambios.
- E y S: glifos de Wallpoet con el corte vertical PUENTEADO (continuas).
- I: barra inclinada "\\" a altura completa de mayuscula (y0..575), grosor de trazo.
Genera:
- tools/wordmark-preview2.html : previsualizacion (varias variantes).
Solo stdlib (json, re).
"""
import json
import re

CAP = 575          # altura de mayuscula (unidades de fuente)
STROKE = 126       # grosor de trazo (ancho de la I de Wallpoet)

with open("tools/wallpoet_glyphs.json", encoding="utf-8") as f:
    G = json.load(f)["glyphs"]


def fmt(v):
    if abs(v - round(v)) < 1e-6:
        return str(int(round(v)))
    return ("%.2f" % v).rstrip("0").rstrip(".")


def offset_path(p, dx):
    toks = re.findall(r"[MLQZ]|-?\d+\.?\d*", p)
    out = []
    coord = 0
    for t in toks:
        if t in "MLQZ":
            out.append(t)
            coord = 0
        else:
            v = float(t)
            if coord % 2 == 0:
                v += dx
            out.append(fmt(v))
            coord += 1
    return " ".join(out)


# Puentes (rellenan el corte vertical donde cruzan las barras horizontales).
E_BRIDGES = (
    "M420 575 L465 575 L465 457 L420 457 Z "
    "M420 344 L465 344 L465 230 L420 230 Z "
    "M416 113 L469 113 L469 0 L416 0 Z"
)
S_BRIDGES = (
    "M390 575 L435 575 L435 457 L390 457 Z "
    "M390 344 L435 344 L435 227 L390 227 Z "
    "M390 113 L439 113 L439 0 L390 0 Z"
)


def slash_path(lean):
    """Barra inclinada \\ a altura completa, grosor STROKE, lsb=80."""
    x0 = 80
    top_l, top_r = x0, x0 + STROKE
    bot_l, bot_r = x0 + lean, x0 + STROKE + lean
    return "M%d %d L%d %d L%d %d L%d %d Z" % (
        top_l, CAP, top_r, CAP, bot_r, 0, bot_l, 0)


def build(lean, tracking=0):
    """Devuelve (path_d, width) del wordmark completo en unidades de fuente (y-up)."""
    slash_adv = STROKE + lean + 120  # avance del \\ (con bearings aprox)
    seq = [
        (G["T"]["path"], G["T"]["advanceWidth"]),
        (slash_path(lean), slash_adv),
        (G["M"]["path"], G["M"]["advanceWidth"]),
        (G["E"]["path"] + " " + E_BRIDGES, G["E"]["advanceWidth"]),
        (G["S"]["path"] + " " + S_BRIDGES, G["S"]["advanceWidth"]),
    ]
    parts = []
    pen = 0
    for path, adv in seq:
        parts.append(offset_path(path, pen))
        pen += adv + tracking
    return " ".join(parts), pen


def svg(lean, tracking, color):
    d, w = build(lean, tracking)
    return (
        '<svg viewBox="-20 -30 %d %d" width="100%%" style="max-width:760px">'
        '<g transform="translate(0,%d) scale(1,-1)">'
        '<path d="%s" fill="%s"/></g></svg>'
        % (w + 40, CAP + 60, CAP, d, color)
    )


# ---- Variante: M rotada usada como E -------------------------------------

def map_path(p, fn):
    toks = re.findall(r"[MLQZ]|-?\d+\.?\d*", p)
    out = []
    pend = None
    for t in toks:
        if t in "MLQZ":
            out.append(t)
        else:
            v = float(t)
            if pend is None:
                pend = v
            else:
                nx, ny = fn(pend, v)
                out.append(fmt(nx))
                out.append(fmt(ny))
                pend = None
    return " ".join(out)


def points(p):
    nums = [float(x) for x in re.findall(r"-?\d+\.?\d*", p)]
    return list(zip(nums[0::2], nums[1::2]))


def rotated_M(mode, lsb=80):
    """Devuelve (path, advance) de la M de Wallpoet rotada y reajustada a la
    caja de mayuscula (alto=CAP), para usarla como E."""
    rot = {
        "cw": lambda x, y: (y, -x),     # 90 horario
        "ccw": lambda x, y: (-y, x),    # 90 antihorario
        "180": lambda x, y: (-x, -y),   # 180
    }[mode]
    base = map_path(G["M"]["path"], rot)
    pts = points(base)
    minx = min(p[0] for p in pts)
    miny = min(p[1] for p in pts)
    maxx = max(p[0] for p in pts)
    maxy = max(p[1] for p in pts)
    scale = CAP / (maxy - miny)

    def refit(x, y):
        return ((x - minx) * scale + lsb, (y - miny) * scale)

    path = map_path(base, refit)
    width = (maxx - minx) * scale
    advance = width + lsb + 80
    return path, advance


def build_eM(lean, mode, tracking=0):
    epath, eadv = rotated_M(mode)
    slash_adv = STROKE + lean + 120
    seq = [
        (G["T"]["path"], G["T"]["advanceWidth"]),
        (slash_path(lean), slash_adv),
        (G["M"]["path"], G["M"]["advanceWidth"]),
        (epath, eadv),
        (G["S"]["path"] + " " + S_BRIDGES, G["S"]["advanceWidth"]),
    ]
    parts = []
    pen = 0
    for path, adv in seq:
        parts.append(offset_path(path, pen))
        pen += adv + tracking
    return " ".join(parts), pen


def svg_eM(lean, mode, color, tracking=0):
    d, w = build_eM(lean, mode, tracking)
    return (
        '<svg viewBox="-20 -30 %d %d" width="100%%" style="max-width:760px">'
        '<g transform="translate(0,%d) scale(1,-1)">'
        '<path d="%s" fill="%s"/></g></svg>'
        % (w + 40, CAP + 60, CAP, d, color)
    )


def write_preview3():
    rows = [
        ("E normal (referencia, versi\u00f3n anterior)", svg(180, 0, "#ffffff")),
        ("E = M rotada 90\u00b0 horario (cw)", svg_eM(180, "cw", "#ffffff")),
        ("E = M rotada 90\u00b0 antihorario (ccw)", svg_eM(180, "ccw", "#ffffff")),
        ("E = M rotada 180\u00b0", svg_eM(180, "180", "#ffffff")),
        ("cw \u00b7 acento", svg_eM(180, "cw", "#FF5252")),
        ("ccw \u00b7 acento", svg_eM(180, "ccw", "#FF5252")),
    ]
    cards = "\n".join(
        '<div class="card"><div class="lbl">%s</div><div class="panel">%s</div></div>'
        % (lbl, c) for lbl, c in rows
    )
    html = """<!doctype html>
<html lang="es"><head><meta charset="utf-8"><title>TIMES \u2014 M rotada como E</title>
<style>
  body {{ margin:0; background:#0b0e0f; color:#e6e6e6; font-family:system-ui,Segoe UI,sans-serif; }}
  header {{ padding:18px 24px; border-bottom:1px solid #1c2123; }}
  header h1 {{ margin:0; font-size:17px; }}
  header p {{ margin:6px 0 0; color:#8b9498; font-size:13px; }}
  .card {{ display:grid; grid-template-columns:280px 1fr; gap:24px; align-items:center;
           padding:22px 24px; border-bottom:1px solid #14181a; }}
  .lbl {{ color:#8b9498; font-size:13px; }}
  .panel {{ background:#0E1213; border-radius:16px; padding:24px 30px; }}
</style></head>
<body>
<header><h1>TIMES \u2014 usar la M de Wallpoet ROTADA como letra E</h1>
<p>Comparativa de rotaciones de la M para que sirva de E. La I sigue como \\ (lean 180); T, M y S sin cambios.</p></header>
{cards}
</body></html>""".format(cards=cards)
    with open("tools/wordmark-preview3.html", "w", encoding="utf-8") as f:
        f.write(html)
    print("OK -> tools/wordmark-preview3.html")


def build_eM_split(lean, mode, tracking=0):
    """Como build_eM pero separa la M real en su propio path (otro color)."""
    epath, eadv = rotated_M(mode)
    slash_adv = STROKE + lean + 120
    seq = [
        ("T", G["T"]["path"], G["T"]["advanceWidth"]),
        ("I", slash_path(lean), slash_adv),
        ("M", G["M"]["path"], G["M"]["advanceWidth"]),
        ("E", epath, eadv),
        ("S", G["S"]["path"] + " " + S_BRIDGES, G["S"]["advanceWidth"]),
    ]
    rest, mpart, pen = [], [], 0
    for name, path, adv in seq:
        op = offset_path(path, pen)
        (mpart if name == "M" else rest).append(op)
        pen += adv + tracking
    return " ".join(rest), " ".join(mpart), pen


def write_final_paths(lean=180, mode="ccw", tracking=0):
    """Exporta los path data finales en coordenadas y-DOWN (para Compose/Android),
    normalizados a x inicial 0. Viewport: width x CAP."""
    rest_d, m_d, pen = build_eM_split(lean, mode, tracking)
    # bbox global para recortar el margen izquierdo (la T empieza en x=30)
    allpts = points(rest_d) + points(m_d)
    minx = min(p[0] for p in allpts)
    maxx = max(p[0] for p in allpts)

    def flip(x, y):
        return (x - minx, CAP - y)  # y-down y x normalizado a 0

    rest_f = map_path(rest_d, flip)
    m_f = map_path(m_d, flip)
    width = maxx - minx
    txt = ("WIDTH=%s\nHEIGHT=%s\n\nREST:\n%s\n\nM:\n%s\n"
           % (fmt(width), fmt(CAP), rest_f, m_f))
    with open("tools/wordmark_paths.txt", "w", encoding="utf-8") as f:
        f.write(txt)
    print("OK -> tools/wordmark_paths.txt  (width=%s height=%s)" % (fmt(width), fmt(CAP)))


def svg_split(lean, mode, tracking, rest_color, m_color):
    rest_d, m_d, w = build_eM_split(lean, mode, tracking)
    return (
        '<svg viewBox="-20 -30 %d %d" width="100%%" style="max-width:760px">'
        '<g transform="translate(0,%d) scale(1,-1)">'
        '<path d="%s" fill="%s"/><path d="%s" fill="%s"/></g></svg>'
        % (w + 40, CAP + 60, CAP, rest_d, rest_color, m_d, m_color)
    )


def write_preview5():
    red = "#FF5252"
    rows = [
        ("Final \u00b7 M en rojo (lean 180, tracking 0)", svg_split(180, "ccw", 0, "#ffffff", red)),
        ("Acento invertido (resto rojo, M blanca)", svg_split(180, "ccw", 0, red, "#ffffff")),
    ]
    cards = "\n".join(
        '<div class="card"><div class="lbl">%s</div><div class="panel">%s</div></div>'
        % (lbl, c) for lbl, c in rows
    )
    light = ('<div class="card"><div class="lbl">sobre claro</div>'
             '<div class="panel" style="background:#f3f3f1">%s</div></div>'
             % svg_split(180, "ccw", 0, "#0E1213", red))
    small = ('<div class="card"><div class="lbl">tama\u00f1os</div>'
             '<div class="panel" style="display:flex;gap:30px;align-items:center;flex-wrap:wrap">'
             '<div style="width:300px">%s</div><div style="width:180px">%s</div>'
             '<div style="width:110px">%s</div></div></div>'
             % (svg_split(180, "ccw", 0, "#ffffff", red),
                svg_split(180, "ccw", 0, "#ffffff", red),
                svg_split(180, "ccw", 0, "#ffffff", red)))
    html = """<!doctype html>
<html lang="es"><head><meta charset="utf-8"><title>TIMES \u2014 M en rojo</title>
<style>
  body {{ margin:0; background:#0b0e0f; color:#e6e6e6; font-family:system-ui,Segoe UI,sans-serif; }}
  header {{ padding:18px 24px; border-bottom:1px solid #1c2123; }}
  header h1 {{ margin:0; font-size:17px; }}
  header p {{ margin:6px 0 0; color:#8b9498; font-size:13px; }}
  .card {{ display:grid; grid-template-columns:280px 1fr; gap:24px; align-items:center;
           padding:22px 24px; border-bottom:1px solid #14181a; }}
  .lbl {{ color:#8b9498; font-size:13px; }}
  .panel {{ background:#0E1213; border-radius:16px; padding:24px 30px; }}
</style></head>
<body>
<header><h1>TIMES \u2014 M en rojo (lean 180, tracking 0)</h1>
<p>La M real resalta en rojo (#FF5252); el resto en blanco. E = M rotada ccw; T y S sin cambios.</p></header>
{cards}
{light}
{small}
</body></html>""".format(cards=cards, light=light, small=small)
    with open("tools/wordmark-preview5.html", "w", encoding="utf-8") as f:
        f.write(html)
    print("OK -> tools/wordmark-preview5.html")


def write_preview4():
    rows = [
        ("lean 120", svg_eM(120, "ccw", "#ffffff")),
        ("lean 180", svg_eM(180, "ccw", "#ffffff")),
        ("lean 240", svg_eM(240, "ccw", "#ffffff")),
        ("lean 180 \u00b7 tracking -60 (m\u00e1s junto)", svg_eM(180, "ccw", "#ffffff", -60)),
        ("lean 180 \u00b7 tracking -120 (muy junto)", svg_eM(180, "ccw", "#ffffff", -120)),
        ("lean 180 \u00b7 acento", svg_eM(180, "ccw", "#FF5252")),
    ]
    cards = "\n".join(
        '<div class="card"><div class="lbl">%s</div><div class="panel">%s</div></div>'
        % (lbl, c) for lbl, c in rows
    )
    light = ('<div class="card"><div class="lbl">sobre claro</div>'
             '<div class="panel" style="background:#f3f3f1">%s</div></div>'
             % svg_eM(180, "ccw", "#0E1213"))
    html = """<!doctype html>
<html lang="es"><head><meta charset="utf-8"><title>TIMES \u2014 final (E=M ccw)</title>
<style>
  body {{ margin:0; background:#0b0e0f; color:#e6e6e6; font-family:system-ui,Segoe UI,sans-serif; }}
  header {{ padding:18px 24px; border-bottom:1px solid #1c2123; }}
  header h1 {{ margin:0; font-size:17px; }}
  header p {{ margin:6px 0 0; color:#8b9498; font-size:13px; }}
  .card {{ display:grid; grid-template-columns:280px 1fr; gap:24px; align-items:center;
           padding:22px 24px; border-bottom:1px solid #14181a; }}
  .lbl {{ color:#8b9498; font-size:13px; }}
  .panel {{ background:#0E1213; border-radius:16px; padding:24px 30px; }}
</style></head>
<body>
<header><h1>TIMES \u2014 ajuste final (E = M rotada 90\u00b0 ccw)</h1>
<p>Elige inclinaci\u00f3n del \\ y el espaciado. T, M, S sin cambios; E continua.</p></header>
{cards}
{light}
</body></html>""".format(cards=cards, light=light)
    with open("tools/wordmark-preview4.html", "w", encoding="utf-8") as f:
        f.write(html)
    print("OK -> tools/wordmark-preview4.html")


def main():
    rows = []
    rows.append((
        "Original Wallpoet (fuente, con cortes)",
        '<p style="font-family:Wallpoet;font-size:120px;color:#fff;margin:0;letter-spacing:.04em">TIMES</p>'
    ))
    rows.append((
        "Vector: E/S puenteadas + I como \\  (lean 180)",
        svg(180, 0, "#ffffff")
    ))
    rows.append(("Lean 120", svg(120, 0, "#ffffff")))
    rows.append(("Lean 240", svg(240, 0, "#ffffff")))
    rows.append(("Tracking -60 (mas junto)", svg(180, -60, "#ffffff")))
    rows.append(("Acento #FF5252", svg(180, 0, "#FF5252")))

    cards = "\n".join(
        '<div class="card"><div class="lbl">%s</div>'
        '<div class="panel">%s</div></div>' % (lbl, content)
        for lbl, content in rows
    )
    light = (
        '<div class="card"><div class="lbl">Sobre claro</div>'
        '<div class="panel light">%s</div></div>' % svg(180, 0, "#0E1213")
    )

    html = """<!doctype html>
<html lang="es"><head><meta charset="utf-8">
<title>TIMES \u2014 Wallpoet vector</title>
<style>
  @font-face {{ font-family:'Wallpoet'; src:url('../app/src/main/res/font/wallpoet_regular.ttf') format('truetype'); }}
  body {{ margin:0; background:#0b0e0f; color:#e6e6e6; font-family:system-ui,Segoe UI,sans-serif; }}
  header {{ padding:18px 24px; border-bottom:1px solid #1c2123; }}
  header h1 {{ margin:0; font-size:17px; }}
  header p {{ margin:6px 0 0; color:#8b9498; font-size:13px; }}
  .card {{ display:grid; grid-template-columns:240px 1fr; gap:24px; align-items:center;
           padding:22px 24px; border-bottom:1px solid #14181a; }}
  .lbl {{ color:#8b9498; font-size:13px; }}
  .panel {{ background:#0E1213; border-radius:16px; padding:24px 30px; }}
  .panel.light {{ background:#f3f3f1; }}
</style></head>
<body>
<header><h1>Wordmark TIMES \u2014 Wallpoet (E/S continuas, I = barra inclinada)</h1>
<p>Arriba el original de la fuente (con cortes) para comparar. Abajo, versi\u00f3n vectorial con E y S puenteadas y la I como \\ a altura completa.</p></header>
{cards}
{light}
</body></html>""".format(cards=cards, light=light)

    with open("tools/wordmark-preview2.html", "w", encoding="utf-8") as f:
        f.write(html)
    print("OK -> tools/wordmark-preview2.html  (ancho lean180 =", build(180, 0)[1], ")")
    write_preview3()
    write_preview4()
    write_preview5()
    write_final_paths()


if __name__ == "__main__":
    main()
