#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Parser TrueType minimo (solo stdlib) para extraer los contornos de glifos de
wallpoet_regular.ttf. Sin fonttools, sin pip, sin red.
Imprime JSON con, por caracter: glyphId, advanceWidth, bbox y el path SVG
(en unidades de fuente, eje Y hacia ARRIBA como en TrueType).
"""
import json
import struct
import sys

FONT = "app/src/main/res/font/wallpoet_regular.ttf"
CHARS = ["T", "I", "M", "E", "S", "\\"]


def read(path):
    with open(path, "rb") as f:
        return f.read()


def u16(d, o):
    return struct.unpack(">H", d[o:o + 2])[0]


def s16(d, o):
    return struct.unpack(">h", d[o:o + 2])[0]


def u32(d, o):
    return struct.unpack(">I", d[o:o + 4])[0]


def table_dir(d):
    num = u16(d, 4)
    tables = {}
    o = 12
    for _ in range(num):
        tag = d[o:o + 4].decode("latin-1")
        off = u32(d, o + 8)
        ln = u32(d, o + 12)
        tables[tag] = (off, ln)
        o += 16
    return tables


def parse_cmap(d, off):
    ntab = u16(d, off + 2)
    best = None
    o = off + 4
    for _ in range(ntab):
        plat = u16(d, o)
        enc = u16(d, o + 2)
        so = u32(d, o + 4)
        # preferimos Windows BMP (3,1), luego Unicode (0,*), luego (3,0)
        score = {(3, 1): 3, (0, 3): 2, (0, 4): 2, (3, 0): 1}.get((plat, enc), 0)
        if best is None or score > best[0]:
            best = (score, off + so)
        o += 8
    sub = best[1]
    fmt = u16(d, sub)
    if fmt != 4:
        raise SystemExit("cmap formato %d no soportado (se esperaba 4)" % fmt)
    segx2 = u16(d, sub + 6)
    segc = segx2 // 2
    base = sub + 14
    end = [u16(d, base + 2 * i) for i in range(segc)]
    start_o = base + segx2 + 2
    start = [u16(d, start_o + 2 * i) for i in range(segc)]
    delta_o = start_o + segx2
    delta = [s16(d, delta_o + 2 * i) for i in range(segc)]
    range_o = delta_o + segx2
    rng = [u16(d, range_o + 2 * i) for i in range(segc)]

    def gid(c):
        for i in range(segc):
            if end[i] >= c and start[i] <= c:
                if rng[i] == 0:
                    return (c + delta[i]) & 0xFFFF
                gi_addr = range_o + 2 * i + rng[i] + 2 * (c - start[i])
                g = u16(d, gi_addr)
                return 0 if g == 0 else (g + delta[i]) & 0xFFFF
        return 0

    return gid


def parse_loca(d, off, num_glyphs, long_format):
    res = []
    if long_format:
        for i in range(num_glyphs + 1):
            res.append(u32(d, off + 4 * i))
    else:
        for i in range(num_glyphs + 1):
            res.append(u16(d, off + 2 * i) * 2)
    return res


def parse_simple_glyph(d, o):
    nc = s16(d, o)
    if nc < 0:
        return None  # compuesto: no esperado en estas letras
    xmin = s16(d, o + 2)
    ymin = s16(d, o + 4)
    xmax = s16(d, o + 6)
    ymax = s16(d, o + 8)
    o += 10
    ends = [u16(d, o + 2 * i) for i in range(nc)]
    o += 2 * nc
    npts = (ends[-1] + 1) if ends else 0
    ilen = u16(d, o)
    o += 2 + ilen
    # flags
    flags = []
    while len(flags) < npts:
        f = d[o]
        o += 1
        flags.append(f)
        if f & 0x08:  # REPEAT
            rep = d[o]
            o += 1
            flags.extend([f] * rep)
    flags = flags[:npts]
    # x coords
    xs = []
    x = 0
    for f in flags:
        if f & 0x02:  # X_SHORT
            dx = d[o]
            o += 1
            x += dx if (f & 0x10) else -dx
        else:
            if not (f & 0x10):  # not X_SAME -> int16 delta
                x += s16(d, o)
                o += 2
            # else same x
        xs.append(x)
    # y coords
    ys = []
    y = 0
    for f in flags:
        if f & 0x04:  # Y_SHORT
            dy = d[o]
            o += 1
            y += dy if (f & 0x20) else -dy
        else:
            if not (f & 0x20):
                y += s16(d, o)
                o += 2
        ys.append(y)
    # separar en contornos
    contours = []
    s = 0
    for e in ends:
        pts = [(xs[i], ys[i], bool(flags[i] & 0x01)) for i in range(s, e + 1)]
        contours.append(pts)
        s = e + 1
    return {"bbox": (xmin, ymin, xmax, ymax), "contours": contours}


def contour_to_path(pts):
    n = len(pts)
    if n == 0:
        return ""
    # rotar para empezar en un punto on-curve
    start_i = next((i for i, p in enumerate(pts) if p[2]), None)
    out = []
    if start_i is None:
        # todos off-curve: empezar en el punto medio entre el ultimo y el primero
        sx = (pts[0][0] + pts[-1][0]) / 2.0
        sy = (pts[0][1] + pts[-1][1]) / 2.0
        out.append("M%g %g" % (sx, sy))
        for i in range(n):
            ctrl = pts[i]
            nxt = pts[(i + 1) % n]
            mx = (ctrl[0] + nxt[0]) / 2.0
            my = (ctrl[1] + nxt[1]) / 2.0
            out.append("Q%g %g %g %g" % (ctrl[0], ctrl[1], mx, my))
        out.append("Z")
        return " ".join(out)
    p = pts[start_i:] + pts[:start_i]
    out.append("M%g %g" % (p[0][0], p[0][1]))
    i = 1
    while i < n:
        cur = p[i]
        if cur[2]:
            out.append("L%g %g" % (cur[0], cur[1]))
            i += 1
        else:
            ctrl = cur
            if i + 1 < n:
                nxt = p[i + 1]
                if nxt[2]:
                    out.append("Q%g %g %g %g" % (ctrl[0], ctrl[1], nxt[0], nxt[1]))
                    i += 2
                else:
                    mx = (ctrl[0] + nxt[0]) / 2.0
                    my = (ctrl[1] + nxt[1]) / 2.0
                    out.append("Q%g %g %g %g" % (ctrl[0], ctrl[1], mx, my))
                    i += 1
            else:
                # ultimo punto off-curve -> cerrar contra el inicio
                out.append("Q%g %g %g %g" % (ctrl[0], ctrl[1], p[0][0], p[0][1]))
                i += 1
    out.append("Z")
    return " ".join(out)


def main():
    d = read(FONT)
    tabs = table_dir(d)
    head_off = tabs["head"][0]
    units = u16(d, head_off + 18)
    loca_fmt = s16(d, head_off + 50)
    num_glyphs = u16(d, tabs["maxp"][0] + 4)
    num_hmetrics = u16(d, tabs["hhea"][0] + 34)
    gid = parse_cmap(d, tabs["cmap"][0])
    loca = parse_loca(d, tabs["loca"][0], num_glyphs, loca_fmt == 1)
    glyf_off = tabs["glyf"][0]
    hmtx_off = tabs["hmtx"][0]

    def advance(g):
        if g < num_hmetrics:
            return u16(d, hmtx_off + 4 * g)
        return u16(d, hmtx_off + 4 * (num_hmetrics - 1))

    result = {"unitsPerEm": units, "glyphs": {}}
    for ch in CHARS:
        g = gid(ord(ch))
        entry = {"glyphId": g, "advanceWidth": advance(g)}
        o0 = glyf_off + loca[g]
        o1 = glyf_off + loca[g + 1]
        if o1 <= o0:
            entry["empty"] = True
            entry["path"] = ""
        else:
            gl = parse_simple_glyph(d, o0)
            if gl is None:
                entry["composite"] = True
            else:
                entry["bbox"] = gl["bbox"]
                entry["numContours"] = len(gl["contours"])
                entry["path"] = " ".join(contour_to_path(c) for c in gl["contours"])
        result["glyphs"][ch] = entry

    out = json.dumps(result, ensure_ascii=False, indent=1)
    sys.stdout.write(out + "\n")
    with open("tools/wallpoet_glyphs.json", "w", encoding="utf-8") as f:
        f.write(out)


if __name__ == "__main__":
    main()
