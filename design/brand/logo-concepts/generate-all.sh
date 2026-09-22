#!/usr/bin/env bash
# Generate Tembra Audio logo concepts — options B (Single Breath) and C (T Meter),
# three variations each.
#
# RUN THIS FROM A TERMINAL INSIDE THE ANTIGRAVITY IDE, so that agy attaches to
# the app's authenticated session. Run standalone it hits the `daily` backend
# and every generate_image call returns HTTP 500.
set -uo pipefail

GEN=""
for c in "$HOME/.agents/skills/antigravity-image/scripts/gen_image.sh" \
         "$HOME/.claude/skills/antigravity-image/scripts/gen_image.sh"; do
  [ -x "$c" ] && { GEN="$c"; break; }
done
[ -n "$GEN" ] || { echo "gen_image.sh not found"; exit 1; }

OUT="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$OUT"

run() {
  echo; echo "==== $1 ===="
  "$GEN" "$2" "$OUT/$1.png" || echo "FAILED: $1"
}

# ---------------------------------------------------------------------------
# OPTION B — Single Breath: one continuous stroke that swells and tapers.
# ---------------------------------------------------------------------------

run "tembra-b1-breath-straight" \
"Logo design for an audio brand: a single continuous horizontal line drawn as one elegant unbroken stroke, swelling from hairline-thin at both ends to thick at the center and tapering back again, suggesting the shape of a sound wave breathing. Warm amber-to-rust gradient along the stroke. Flat vector, generous negative space, centered on a warm off-white background. No text. Sophisticated, restrained, high-end audio brand mark."

run "tembra-b2-breath-undulating" \
"Logo design for an audio brand: a single continuous unbroken stroke that undulates through three smooth peaks and troughs like a slow sine wave, the line weight swelling at the crests and thinning at the crossings, so it reads as a sound wave in motion. Warm honey amber deepening to burnt terracotta along its length. Flat vector, fluid confident curves, wide margins, centered on a warm off-white background. No text. Elegant, musical, premium audio identity."

run "tembra-b3-breath-loop" \
"Logo design for an audio brand: a single continuous stroke curving back on itself to form a soft closed loop, an elongated horizontal oval with one graceful crossing at the center, the line swelling and tapering as it travels so it suggests both a resonating loop and a breath of sound. Warm amber to deep clay gradient. Flat vector, one unbroken path, perfectly balanced, centered on a cream background with generous margins. No text. Refined minimal audio brand mark."

# ---------------------------------------------------------------------------
# OPTION C — T Meter: letterform built from level-meter bars.
# ---------------------------------------------------------------------------

run "tembra-c1-meter-full" \
"Logo design for an audio brand: a bold geometric letter T monogram constructed entirely from vertical rounded bars of varying heights, like a level meter or equalizer display, the tallest bars forming the crossbar and the stem. Warm palette of honey gold, amber and burnt orange across the bars. Flat vector, geometric precision, centered on a cream background, generous margins. No text. Clean modern audio software branding."

run "tembra-c2-meter-stem" \
"Logo design for an audio brand: a bold geometric letter T with a solid clean crossbar, its vertical stem built from a stack of short horizontal segments of varying widths like a level meter filling up, the segments brightest at the base and fading toward the top. Warm honey gold through amber to deep burnt orange. Flat vector, strong geometry, centered on a cream background with generous margins. No text. Modern premium audio software mark."

run "tembra-c3-meter-icon" \
"Logo design for an audio brand: an app icon style mark, a rounded square tile in warm deep terracotta, containing a letter T formed from vertical rounded equalizer bars of varying heights in honey gold and bright amber. Flat vector, crisp geometry, strong figure-ground contrast, the tile centered with generous margins on a plain cream background. No text. Confident modern audio app branding."

echo; echo "==== done ===="; ls -la "$OUT"/*.png 2>/dev/null || echo "no images produced"
