#!/usr/bin/env python3
"""Generate the Tembra Audio app icons and store assets from the logo artwork.

The squirrel mark is cut out of ../logo/source/logo-v3-violet-dark.jpeg pixel for
pixel (the "TEMBRA AUDIO" wordmark is left out of every icon), so the colours and
gradients are the artwork's own. Nothing is redrawn.

    python3 design/brand/app-icon/generate_app_icons.py [android] [ios] [previews]

With no arguments it writes everything. Requires Pillow and NumPy; the iOS
preview also renders AppIcon.icon with Icon Composer's ictool (Xcode 27.1).
"""
import json
import shutil
import subprocess
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
SRC = HERE.parent / "logo" / "source" / "logo-v3-violet-dark.jpeg"

# The artwork's own background: a flat #141416 (std dev 0.4 across the canvas).
BG = (0x14, 0x14, 0x16)
BG_HEX = "#141416"

# Regions of the 2400x1792 source. The mark box stops above the wordmark.
MARK_BOX = (740, 280, 1810, 1290)
WORDMARK_BOX = (520, 1310, 2030, 1510)

# Diameter of the circle that encloses the mark, as a fraction of the visible
# icon (the 72 dp viewport on Android, the whole square on iOS and Google Play).
# 0.78 of 72 dp is 56 dp, inside Android's 66 dp safe zone with room to spare.
MARK_DIAMETER = 0.78

# Android adaptive icon: 108 dp layers, 72 dp visible viewport.
ANDROID_LAYER_DP = 108
ANDROID_VIEWPORT_DP = 72
ANDROID_SAFE_ZONE_DP = 66
ANDROID_DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}


def extract(box):
    """Cut a region out of the source and turn the flat background into alpha.

    Each pixel's alpha is how far it rises above the background; colour is then
    un-blended from the background so anti-aliased edges keep their true hue.
    JPEG ringing darker than the background becomes fully transparent.
    """
    rgb = np.asarray(Image.open(SRC).convert("RGB").crop(box), dtype=np.float32)
    bg = np.array(BG, dtype=np.float32)
    rise = np.clip(rgb - bg, 0, None).max(axis=2)
    alpha = np.clip((rise - 8.0) / 40.0, 0.0, 1.0)
    a = alpha[..., None]
    colour = np.where(a > 0, (rgb - bg * (1 - a)) / np.maximum(a, 1e-6), 0)
    rgba = np.dstack([np.clip(colour, 0, 255), alpha * 255]).round().astype(np.uint8)
    image = Image.fromarray(rgba)
    return image.crop(image.getbbox())


def enclosing_radius(mark):
    """Radius, from the mark's bounding-box centre, of its farthest opaque pixel."""
    alpha = np.asarray(mark.getchannel("A"))
    ys, xs = np.nonzero(alpha > 127)
    cx, cy = mark.width / 2, mark.height / 2
    return float(np.sqrt((xs + 0.5 - cx) ** 2 + (ys + 0.5 - cy) ** 2).max())


def place(mark, radius, size, visible):
    """Centre the mark on a transparent size x size canvas.

    `visible` is the fraction of the canvas that shows through the platform mask.
    """
    scale = (MARK_DIAMETER * visible * size / 2) / radius
    w, h = round(mark.width * scale), round(mark.height * scale)
    scaled = mark.resize((w, h), Image.LANCZOS)  # Pillow resamples RGBA premultiplied
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    canvas.alpha_composite(scaled, ((size - w) // 2, (size - h) // 2))
    return canvas


def flatten(layer, colour=BG):
    out = Image.new("RGBA", layer.size, colour + (255,))
    out.alpha_composite(layer)
    return out


def monochrome(layer):
    """White silhouette with the layer's alpha: Android tints it for themed icons."""
    white = Image.new("RGBA", layer.size, (255, 255, 255, 0))
    white.putalpha(layer.getchannel("A"))
    return white


def save_png(image, path, mode="RGBA"):
    path.parent.mkdir(parents=True, exist_ok=True)
    image.convert(mode).save(path, "PNG", optimize=True)


# --- masks used by the store crops and the previews -------------------------


def rounded_mask(size, radius_fraction, supersample=4):
    big = size * supersample
    mask = Image.new("L", (big, big), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        (0, 0, big - 1, big - 1), radius=round(big * radius_fraction), fill=255
    )
    return mask.resize((size, size), Image.LANCZOS)


def circle_mask(size, supersample=4):
    big = size * supersample
    mask = Image.new("L", (big, big), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, big - 1, big - 1), fill=255)
    return mask.resize((size, size), Image.LANCZOS)


def squircle_mask(size, exponent=5.0, supersample=4):
    big = size * supersample
    t = (np.arange(big) + 0.5) / big * 2 - 1
    x, y = np.meshgrid(t, t)
    inside = (np.abs(x) ** exponent + np.abs(y) ** exponent) <= 1
    mask = Image.fromarray((inside * 255).astype(np.uint8))
    return mask.resize((size, size), Image.LANCZOS)


def apply_mask(image, mask):
    out = image.copy()
    out.putalpha(Image.fromarray(
        (np.asarray(image.getchannel("A"), np.float32) * np.asarray(mask, np.float32) / 255)
        .round().astype(np.uint8)))
    return out


# --- Android ----------------------------------------------------------------

ADAPTIVE_XML = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@mipmap/ic_launcher_monochrome" />
</adaptive-icon>
"""

BACKGROUND_XML = f"""<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- The logo artwork's own background (design/brand/logo/source/logo-v3-violet-dark.jpeg). -->
    <color name="ic_launcher_background">{BG_HEX}</color>
</resources>
"""


def android(mark, radius, wordmark):
    out = HERE / "android"
    visible = ANDROID_VIEWPORT_DP / ANDROID_LAYER_DP
    res = out / "res"
    for density, factor in ANDROID_DENSITIES.items():
        size = round(ANDROID_LAYER_DP * factor)
        layer = place(mark, radius, size, visible)
        save_png(layer, res / f"mipmap-{density}" / "ic_launcher_foreground.png")
        save_png(monochrome(layer), res / f"mipmap-{density}" / "ic_launcher_monochrome.png")
    anydpi = res / "mipmap-anydpi-v26"
    anydpi.mkdir(parents=True, exist_ok=True)
    (anydpi / "ic_launcher.xml").write_text(ADAPTIVE_XML)
    (anydpi / "ic_launcher_round.xml").write_text(ADAPTIVE_XML)
    (res / "values").mkdir(parents=True, exist_ok=True)
    (res / "values" / "ic_launcher_background.xml").write_text(BACKGROUND_XML)

    # Google Play: 512x512 32-bit PNG, full square, opaque; Play applies the
    # 30 % corner mask and the shadow itself.
    play = out / "play-store"
    save_png(flatten(place(mark, radius, 512, 1.0)), play / "icon-512.png")
    save_png(feature_graphic(mark, wordmark), play / "feature-graphic-1024x500.png", mode="RGB")


def feature_graphic(mark, wordmark):
    """1024x500, 24-bit (no alpha): the mark beside the wordmark, centred.

    Store artwork, not the app icon, so the wordmark is allowed here.
    """
    w, h = 1024, 500
    canvas = Image.new("RGBA", (w, h), BG + (255,))
    mark_h = 300
    m = mark.resize((round(mark.width * mark_h / mark.height), mark_h), Image.LANCZOS)
    word_w = 520
    t = wordmark.resize((word_w, round(wordmark.height * word_w / wordmark.width)), Image.LANCZOS)
    gap = 56
    x = (w - (m.width + gap + t.width)) // 2
    canvas.alpha_composite(m, (x, (h - m.height) // 2))
    canvas.alpha_composite(t, (x + m.width + gap, (h - t.height) // 2))
    return canvas


# --- iOS --------------------------------------------------------------------


def greyscale(layer):
    """The layer's luminance, stretched so the brightest part of the mark is white.

    Keeps the tail's shading in the tinted icon; the floor stops the indigo tail
    base from sinking into the black background.
    """
    rgba = np.asarray(layer, np.float32)
    luma = rgba[..., :3] @ np.array([0.2126, 0.7152, 0.0722], np.float32)
    alpha = rgba[..., 3] / 255
    lit = luma[alpha > 0.5]
    norm = np.clip((luma - lit.min()) / max(lit.max() - lit.min(), 1), 0, 1)
    grey = (0.55 + 0.45 * norm) * 255 * alpha
    return Image.fromarray(grey.round().astype(np.uint8))


ICON_COMPOSER_DOC = {
    "fill": {"solid": "srgb:%.5f,%.5f,%.5f,1.00000" % tuple(c / 255 for c in BG)},
    "groups": [
        {
            "layers": [{"image-name": "squirrel.png", "name": "squirrel", "glass": True}],
            "shadow": {"kind": "neutral", "opacity": 0.5},
            "translucency": {"enabled": False, "value": 0.5},
        }
    ],
    "supported-platforms": {"squares": ["iOS"]},
}

ICTOOL = Path("/Applications/Xcode.app/Contents/Applications/Icon Composer.app/Contents/Executables/ictool")
ICON_RENDITIONS = ["Default", "Dark", "ClearLight", "ClearDark", "TintedLight", "TintedDark"]


def ios(mark, radius):
    """iOS app icon in both formats Xcode accepts, plus the App Store icon.

    AppIcon.appiconset: single-size 1024 in light, dark and tinted appearances
    (every iOS version; dark and tinted from iOS 18).
    AppIcon.icon: Icon Composer document (iOS 26+ layered Liquid Glass icon):
    the squirrel as one foreground layer on the artwork's solid background.
    """
    out = HERE / "ios"
    iconset = out / "AppIcon.appiconset"
    layer = place(mark, radius, 1024, 1.0)
    # Light (default): opaque, no alpha, no rounded corners; iOS applies the mask.
    save_png(flatten(layer), iconset / "app-icon-1024.png", mode="RGB")
    # Dark: the mark on a transparent background; iOS supplies the dark backdrop.
    save_png(layer, iconset / "app-icon-1024-dark.png")
    # Tinted: opaque greyscale on black that iOS recolours with the user's tint.
    save_png(greyscale(layer), iconset / "app-icon-1024-tinted.png", mode="L")
    contents = {
        "images": [
            {"filename": "app-icon-1024.png", "idiom": "universal", "platform": "ios", "size": "1024x1024"},
            {
                "appearances": [{"appearance": "luminosity", "value": "dark"}],
                "filename": "app-icon-1024-dark.png", "idiom": "universal", "platform": "ios", "size": "1024x1024",
            },
            {
                "appearances": [{"appearance": "luminosity", "value": "tinted"}],
                "filename": "app-icon-1024-tinted.png", "idiom": "universal", "platform": "ios", "size": "1024x1024",
            },
        ],
        "info": {"author": "xcode", "version": 1},
    }
    (iconset / "Contents.json").write_text(json.dumps(contents, indent=2) + "\n")

    icon_doc = out / "AppIcon.icon"
    save_png(layer, icon_doc / "Assets" / "squirrel.png")
    (icon_doc / "icon.json").write_text(json.dumps(ICON_COMPOSER_DOC, indent=2) + "\n")

    # App Store Connect marketing icon: 1024x1024, opaque, no alpha.
    save_png(flatten(layer), out / "app-store" / "app-store-icon-1024.png", mode="RGB")


def ios_preview():
    """Contact sheet: the asset-catalog variants under the iOS mask, then the
    Icon Composer document rendered by ictool in all six Home Screen appearances."""
    iconset = HERE / "ios" / "AppIcon.appiconset"
    cell = 360
    mask = squircle_mask(cell)

    def masked(image, backdrop):
        tile = Image.new("RGBA", (cell, cell), backdrop + (255,))
        tile.alpha_composite(image.convert("RGBA").resize((cell, cell), Image.LANCZOS))
        return apply_mask(tile, mask)

    tint = Image.new("RGBA", (cell, cell), (0xF2, 0xB6, 0x4C, 255))  # an example user tint
    tinted_src = Image.open(iconset / "app-icon-1024-tinted.png").resize((cell, cell), Image.LANCZOS)
    tint.putalpha(tinted_src)
    tinted = Image.new("RGBA", (cell, cell), (0, 0, 0, 255))
    tinted.alpha_composite(tint)

    rows = [[
        ("appiconset: light", masked(Image.open(iconset / "app-icon-1024.png"), BG)),
        ("appiconset: dark", masked(Image.open(iconset / "app-icon-1024-dark.png"), (0, 0, 0))),
        ("appiconset: tinted (amber)", apply_mask(tinted, mask)),
    ]]
    if ICTOOL.exists():
        renders = []
        tmp = HERE / "previews" / ".ictool"
        tmp.mkdir(parents=True, exist_ok=True)
        for rendition in ICON_RENDITIONS:
            png = tmp / f"{rendition}.png"
            subprocess.run([
                str(ICTOOL), str(HERE / "ios" / "AppIcon.icon"), "--export-image", "--output-file", str(png),
                "--platform", "iOS", "--rendition", rendition, "--width", "1024", "--height", "1024", "--scale", "1",
            ], check=True, capture_output=True)
            renders.append((f"AppIcon.icon: {rendition}", Image.open(png).convert("RGBA").resize((cell, cell), Image.LANCZOS)))
        shutil.rmtree(tmp)
        rows += [renders[:3], renders[3:]]

    pad, label_h = 40, 40
    sheet = Image.new("RGBA", (pad + 3 * (cell + pad), pad + len(rows) * (cell + label_h + pad)), (0x6E, 0x7B, 0x8F, 255))
    d = ImageDraw.Draw(sheet)
    for r, row in enumerate(rows):
        y = pad + r * (cell + label_h + pad)
        for c, (label, tile) in enumerate(row):
            x = pad + c * (cell + pad)
            sheet.alpha_composite(tile, (x, y))
            d.text((x, y + cell + 10), label, fill=(255, 255, 255, 255))
    save_png(sheet, HERE / "previews" / "ios-preview.png", mode="RGB")


# --- previews ---------------------------------------------------------------


def android_preview(mark, radius):
    """Contact sheet: the layer with keylines, OEM mask shapes, themed, Play."""
    scale = 4  # px per dp
    layer_px = ANDROID_LAYER_DP * scale
    view_px = ANDROID_VIEWPORT_DP * scale
    off = (layer_px - view_px) // 2
    fg = place(mark, radius, layer_px, ANDROID_VIEWPORT_DP / ANDROID_LAYER_DP)
    full = flatten(fg)
    viewport = full.crop((off, off, off + view_px, off + view_px))

    guides = full.copy()
    d = ImageDraw.Draw(guides)
    d.rectangle((off, off, off + view_px - 1, off + view_px - 1), outline=(255, 255, 255, 140), width=2)
    safe = ANDROID_SAFE_ZONE_DP * scale
    so = (layer_px - safe) // 2
    d.ellipse((so, so, so + safe - 1, so + safe - 1), outline=(80, 220, 160, 255), width=2)

    themed_fg = monochrome(fg).crop((off, off, off + view_px, off + view_px))
    themed = Image.new("RGBA", (view_px, view_px), (0xD3, 0xE8, 0xD0, 255))  # Material You light container
    tint = Image.new("RGBA", (view_px, view_px), (0x23, 0x4D, 0x2B, 255))
    tint.putalpha(themed_fg.getchannel("A"))
    themed.alpha_composite(tint)

    play = apply_mask(flatten(place(mark, radius, 512, 1.0)), rounded_mask(512, 0.30)).resize(
        (view_px, view_px), Image.LANCZOS)

    tiles = [
        ("108 dp layer, 72 dp viewport, 66 dp safe zone", guides),
        ("Circle (Pixel)", apply_mask(viewport, circle_mask(view_px))),
        ("Squircle", apply_mask(viewport, squircle_mask(view_px))),
        ("Rounded square", apply_mask(viewport, rounded_mask(view_px, 0.22))),
        ("Themed (monochrome layer)", apply_mask(themed, circle_mask(view_px))),
        ("Google Play 512 (30 % mask)", play),
    ]
    pad, label_h = 40, 40
    cell = layer_px
    sheet = Image.new("RGBA", (pad + len(tiles) * (cell + pad), pad * 2 + cell + label_h + 140), (0x2B, 0x2D, 0x31, 255))
    d = ImageDraw.Draw(sheet)
    for i, (label, tile) in enumerate(tiles):
        x = pad + i * (cell + pad)
        sheet.alpha_composite(tile, (x + (cell - tile.width) // 2, pad + (cell - tile.height) // 2))
        d.text((x, pad + cell + 10), label, fill=(230, 230, 235, 255))
    # Real launcher sizes: 48 dp at xxhdpi (144 px) and mdpi (48 px).
    y = pad + cell + label_h
    small = apply_mask(viewport, circle_mask(view_px))
    for i, px in enumerate((144, 96, 48)):
        sheet.alpha_composite(small.resize((px, px), Image.LANCZOS), (pad + i * 170, y + (140 - px) // 2))
    d.text((pad + 3 * 170, y + 60), "launcher sizes: 144 / 96 / 48 px", fill=(230, 230, 235, 255))
    save_png(sheet, HERE / "previews" / "android-preview.png", mode="RGB")


def main(argv):
    targets = set(argv) or {"android", "ios", "previews"}
    mark = extract(MARK_BOX)
    wordmark = extract(WORDMARK_BOX)
    radius = enclosing_radius(mark)
    save_png(mark, HERE / "master" / "tembra-mark.png")
    save_png(monochrome(mark), HERE / "master" / "tembra-mark-monochrome.png")
    save_png(wordmark, HERE / "master" / "tembra-wordmark.png")
    if "android" in targets:
        android(mark, radius, wordmark)
    if "ios" in targets:
        ios(mark, radius)
    if "previews" in targets:
        android_preview(mark, radius)
        if (HERE / "ios" / "AppIcon.appiconset").exists():
            ios_preview()
    print(f"mark {mark.size}, enclosing radius {radius:.1f}px")


if __name__ == "__main__":
    main(sys.argv[1:])
