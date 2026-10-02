#!/usr/bin/env python3
"""Generates the metronome's note-figure glyphs as Compose vector resources (hand-authored artwork, not Material Symbols).

Every figure is built from the same parts (a filled note head as a rotated ellipse, a stem on the right of the head,
flat beams joining the stem tops, a curved filled flag, a filled "3" with a bracket for triplets) so the eight read as
one family at 24 dp and 32 dp. Each shape is a closed polygon sampled from its curve and written as its own fill-only
`<path>` in a 24 x 24 viewport, with a literal white fill the Icon composable tints.

Usage: python3 tools/make-note-glyphs.py [--preview <dir>]
Output: shared/ui/src/commonMain/composeResources/drawable/ic_note_*_24.xml (idempotent: re-running overwrites
identically); with --preview, also <dir>/glyphs.svg.json (glyph name -> SVG path data in the 24 x 24 space),
<dir>/glyphs.html (a labelled preview sheet, each glyph at 96 x 96 in white on #111114).
"""
import json
import math
import os
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = os.path.join(SCRIPT_DIR, "..", "shared", "ui", "src", "commonMain", "composeResources", "drawable")

# Shared proportions (viewport units).
HEAD_RX = 2.5          # half of the 5-unit head length
HEAD_RY = 1.8          # half of the 3.6-unit head height
HEAD_TILT = -20.0      # degrees, rising to the right
STEM_W = 1.4
STEM_H = 12.0          # from the head centre to the stem top
BEAM_H = 2.2
BEAM_GAP = 0.9
EXTRA_STEM_PER_BEAM = 1.5  # engraving rule: a third beam lengthens the stems
MARGIN = 1.0
DIGIT_H = 4.0
DIGIT_STROKE = 0.95
BRACKET_W = 0.7
SAMPLES = 48


def _head_half_extent():
    t = math.radians(HEAD_TILT)
    hw = math.sqrt((HEAD_RX * math.cos(t)) ** 2 + (HEAD_RY * math.sin(t)) ** 2)
    hh = math.sqrt((HEAD_RX * math.sin(t)) ** 2 + (HEAD_RY * math.cos(t)) ** 2)
    return hw, hh


HEAD_HW, HEAD_HH = _head_half_extent()


def ellipse(cx, cy):
    t = math.radians(HEAD_TILT)
    pts = []
    for i in range(SAMPLES):
        a = 2 * math.pi * i / SAMPLES
        x, y = HEAD_RX * math.cos(a), HEAD_RY * math.sin(a)
        # Screen y grows downwards, so a negative tilt in screen space raises the right end.
        pts.append((cx + x * math.cos(t) - y * math.sin(t), cy + x * math.sin(t) + y * math.cos(t)))
    return pts


def rect(x0, y0, x1, y1):
    return [(x0, y0), (x1, y0), (x1, y1), (x0, y1)]


def bezier(p0, p1, p2, p3, n=24):
    out = []
    for i in range(n + 1):
        u = i / n
        a, b, c, d = (1 - u) ** 3, 3 * u * (1 - u) ** 2, 3 * u * u * (1 - u), u ** 3
        out.append((a * p0[0] + b * p1[0] + c * p2[0] + d * p3[0], a * p0[1] + b * p1[1] + c * p2[1] + d * p3[1]))
    return out


def stroke(centre, width):
    """Outline of an open polyline drawn with the given width (butt caps)."""
    left, right = [], []
    n = len(centre)
    for i in range(n):
        x0, y0 = centre[max(i - 1, 0)]
        x1, y1 = centre[min(i + 1, n - 1)]
        dx, dy = x1 - x0, y1 - y0
        length = math.hypot(dx, dy) or 1.0
        nx, ny = -dy / length * width / 2, dx / length * width / 2
        px, py = centre[i]
        left.append((px + nx, py + ny))
        right.append((px - nx, py - ny))
    return left + right[::-1]


def arc(cx, cy, rx, ry, a0, a1, n=28):
    """Elliptical arc in degrees, counter-clockwise on screen for a0 < a1 (y up)."""
    return [(cx + rx * math.cos(math.radians(a0 + (a1 - a0) * i / n)),
             cy - ry * math.sin(math.radians(a0 + (a1 - a0) * i / n))) for i in range(n + 1)]


def flag(stem_right, top):
    """An eighth flag hanging from the stem top, sweeping right and curling down."""
    x = stem_right
    outer = bezier((x - 0.2, top), (x + 0.4, top + 2.8), (x + 5.6, top + 3.6), (x + 3.8, top + 9.2))
    inner = bezier((x + 3.8, top + 9.2), (x + 3.9, top + 6.0), (x + 1.4, top + 5.2), (x - 0.2, top + 4.8))
    return outer + inner[1:]


def digit_three(cx, top):
    """A '3' of two stacked bowls, DIGIT_H tall, as two stroked arcs."""
    s = DIGIT_STROKE / 2
    h = DIGIT_H - 2 * s
    ry_top, ry_bot = h * 0.22, h * 0.28
    cy_top = top + s + ry_top
    cy_bot = top + s + 2 * ry_top + ry_bot
    rx_top, rx_bot = 1.15, 1.3
    top_bowl = arc(cx, cy_top, rx_top, ry_top, 155, -90)
    top_bowl.append((cx - 0.5, cy_top + ry_top))
    bottom_bowl = [(cx - 0.5, cy_bot - ry_bot)] + arc(cx, cy_bot, rx_bot, ry_bot, 90, -205)
    return [stroke(top_bowl, DIGIT_STROKE), stroke(bottom_bowl, DIGIT_STROKE)]


def figure(count, beams, triplet=False, spacing=0.0, flags=0):
    """Shapes of a figure laid out from the origin; centred into the viewport afterwards."""
    stem_h = STEM_H + max(0, beams - 2) * EXTRA_STEM_PER_BEAM
    shapes = []
    stem_tops = []
    for i in range(count):
        cx, cy = i * spacing, 0.0
        shapes.append(ellipse(cx, cy))
        right = cx + HEAD_HW - 0.05
        stem = (right - STEM_W, -stem_h, right)
        shapes.append(rect(stem[0], -stem_h, right, -0.3))
        stem_tops.append(stem)
    if beams and count > 1:
        x0, x1 = stem_tops[0][0], stem_tops[-1][2]
        for b in range(beams):
            y = -stem_h + b * (BEAM_H + BEAM_GAP)
            shapes.append(rect(x0, y, x1, y + BEAM_H))
    for f in range(flags):
        shapes.append(flag(stem_tops[0][2], -stem_h + f * (BEAM_H + BEAM_GAP)))
    if triplet:
        x0, x1 = stem_tops[0][0], stem_tops[-1][2]
        mid = (x0 + x1) / 2
        digit_top = -stem_h - 1.0 - DIGIT_H
        shapes.extend(digit_three(mid, digit_top))
        line_y = digit_top + DIGIT_H / 2 - BRACKET_W / 2
        tick_bottom = -stem_h - 0.8
        half_gap = 1.3 + DIGIT_STROKE / 2 + 0.55
        shapes.append(rect(x0, line_y, mid - half_gap, line_y + BRACKET_W))
        shapes.append(rect(mid + half_gap, line_y, x1, line_y + BRACKET_W))
        shapes.append(rect(x0, line_y, x0 + BRACKET_W, tick_bottom))
        shapes.append(rect(x1 - BRACKET_W, line_y, x1, tick_bottom))
    return centre(shapes)


def centre(shapes):
    xs = [p[0] for s in shapes for p in s]
    ys = [p[1] for s in shapes for p in s]
    dx = 12 - (min(xs) + max(xs)) / 2
    dy = 12 - (min(ys) + max(ys)) / 2
    moved = [[(x + dx, y + dy) for x, y in s] for s in shapes]
    for s in moved:
        for x, y in s:
            if not (MARGIN - 0.3 <= x <= 24 - MARGIN + 0.3 and MARGIN - 0.3 <= y <= 24 - MARGIN + 0.3):
                raise SystemExit("glyph point (%.2f, %.2f) leaves the 1-unit margin" % (x, y))
    return moved


def fmt(v):
    s = ("%.2f" % v).rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def path_data(points):
    """One closed subpath, always wound clockwise on screen so the subpaths of a glyph union under the nonzero rule."""
    area = sum(x0 * y1 - x1 * y0 for (x0, y0), (x1, y1) in zip(points, points[1:] + points[:1]))
    if area < 0:
        points = points[::-1]
    head = "M%s,%s" % (fmt(points[0][0]), fmt(points[0][1]))
    return head + "".join("L%s,%s" % (fmt(x), fmt(y)) for x, y in points[1:]) + "Z"


GLYPHS = [
    ("note_quarter", "quarter note", figure(1, 0)),
    ("note_eighth_pair", "two eighth notes", figure(2, 1, spacing=8.0)),
    ("note_sixteenth_quad", "four sixteenth notes", figure(4, 2, spacing=5.6)),
    ("note_eighth_triplet", "eighth-note triplet", figure(3, 1, triplet=True, spacing=6.6)),
    ("note_eighth", "eighth note", figure(1, 0, flags=1)),
    ("note_sixteenth_pair", "two sixteenth notes", figure(2, 2, spacing=8.0)),
    ("note_thirty_second_quad", "four thirty-second notes", figure(4, 3, spacing=5.6)),
    ("note_sixteenth_triplet", "sixteenth-note triplet", figure(3, 2, triplet=True, spacing=6.6)),
]


def vector_xml(name, shapes):
    paths = "".join(
        '  <path\n      android:fillColor="#FFFFFFFF"\n      android:pathData="%s"/>\n' % path_data(s) for s in shapes
    )
    return (
        '<!-- Tembra note glyph "%s"; generated by tools/make-note-glyphs.py, do not edit -->\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="24dp"\n'
        '    android:height="24dp"\n'
        '    android:viewportWidth="24"\n'
        '    android:viewportHeight="24">\n'
        "%s</vector>\n" % (name, paths)
    )


def write_preview(directory):
    os.makedirs(directory, exist_ok=True)
    data = {name: " ".join(path_data(s) for s in shapes) for name, _, shapes in GLYPHS}
    with open(os.path.join(directory, "glyphs.svg.json"), "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")
    tiles = []
    for name, label, _ in GLYPHS:
        tiles.append(
            '<figure><svg viewBox="0 0 24 24" width="96" height="96"><path fill="#fff" d="%s"/></svg>'
            '<svg viewBox="0 0 24 24" width="24" height="24"><path fill="#fff" d="%s"/></svg>'
            '<svg viewBox="0 0 24 24" width="32" height="32"><path fill="#fff" d="%s"/></svg>'
            "<figcaption>%s<br>ic_%s_24</figcaption></figure>" % (data[name], data[name], data[name], label, name)
        )
    html = (
        "<!doctype html><meta charset=utf-8><style>body{margin:16px;background:#222;font:11px sans-serif;color:#ccc;"
        "display:grid;grid-template-columns:repeat(4,200px);gap:12px}figure{margin:0;background:#111114;padding:8px;"
        "display:flex;flex-wrap:wrap;align-items:flex-end;gap:8px}svg{outline:1px dashed #333}figcaption{width:100%%}"
        "</style>%s" % "".join(tiles)
    )
    with open(os.path.join(directory, "glyphs.html"), "w") as f:
        f.write(html)


def main(argv):
    for name, _, shapes in GLYPHS:
        out = os.path.join(OUT_DIR, "ic_%s_24.xml" % name)
        with open(out, "w") as f:
            f.write(vector_xml(name, shapes))
        print("wrote " + os.path.relpath(out, os.path.join(SCRIPT_DIR, "..")))
    if len(argv) == 2 and argv[0] == "--preview":
        write_preview(argv[1])
    elif argv:
        raise SystemExit("usage: make-note-glyphs.py [--preview <dir>]")


if __name__ == "__main__":
    main(sys.argv[1:])
