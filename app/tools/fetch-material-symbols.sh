#!/usr/bin/env bash
# Downloads Material Symbols Outlined glyphs (24 dp, weight 400, unfilled) from google/material-design-icons at a
# pinned commit and normalises them for the Compose Multiplatform resource loader, which accepts only literal
# colours (specs/002-adaptive-navigation-shell/research.md R6). The only sanctioned way to add a glyph.
#
# Usage: tools/fetch-material-symbols.sh <glyph> [<glyph> ...]
# Output: shared/ui/src/commonMain/composeResources/drawable/ic_<glyph>_24.xml (idempotent: re-running overwrites identically)
set -euo pipefail

COMMIT=27e9ef1dbeedc13d682fece4a58e1eda4cb0961a
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_DIR="$SCRIPT_DIR/../shared/ui/src/commonMain/composeResources/drawable"

if [ "$#" -lt 1 ]; then
  echo "usage: $(basename "$0") <glyph> [<glyph> ...]" >&2
  exit 2
fi

mkdir -p "$OUT_DIR"

for glyph in "$@"; do
  if ! [[ "$glyph" =~ ^[a-z0-9_]+$ ]]; then
    echo "error: glyph name '$glyph' must match [a-z0-9_]+" >&2
    exit 1
  fi
  url="https://raw.githubusercontent.com/google/material-design-icons/$COMMIT/symbols/android/$glyph/materialsymbolsoutlined/${glyph}_24px.xml"
  out="$OUT_DIR/ic_${glyph}_24.xml"
  tmp="$(mktemp)"
  if ! curl --fail --silent --show-error --location "$url" -o "$tmp"; then
    echo "error: could not download $url" >&2
    rm -f "$tmp"
    exit 1
  fi
  header="<!-- Material Symbols Outlined \"$glyph\" from google/material-design-icons@$COMMIT (Apache-2.0); fetched by tools/fetch-material-symbols.sh, do not edit -->"
  normalised="$(
    perl -0pe 's/\s*android:tint="[^"]*"//g; s/android:fillColor="\@android:color\/white"/android:fillColor="#FFFFFFFF"/g' "$tmp"
  )"
  rm -f "$tmp"
  if printf '%s\n' "$normalised" | grep -qE '@|\?attr'; then
    echo "error: $glyph still references a platform resource or theme attribute after normalisation" >&2
    exit 1
  fi
  if printf '%s\n' "$normalised" | head -n 1 | grep -q '^<?xml'; then
    { printf '%s\n' "$normalised" | head -n 1; printf '%s\n' "$header"; printf '%s\n' "$normalised" | tail -n +2; } > "$out"
  else
    { printf '%s\n' "$header"; printf '%s\n' "$normalised"; } > "$out"
  fi
  echo "wrote ${out#"$SCRIPT_DIR/../"}"
done
