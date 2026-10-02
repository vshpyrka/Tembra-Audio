# Data Model: Tabletop Posture

Nothing in this feature is stored: every value below is derived from the window on each composition (spec Key
Entities: Posture). All types live in `:shared:ui`.

## 1. TabletopFold (`audio.tembra.ui.posture`)

The fold's vertical span in window pixels while the window is in tabletop posture.

| Field | Type | Rule |
|---|---|---|
| `top` | `Float` | The hinge's top edge in window px (`HingeInfo.bounds.top`) |
| `bottom` | `Float` | Its bottom edge (`bounds.bottom`); equal to `top` for a zero-height fold (the emulator) |

- `TabletopFold.from(posture: Posture): TabletopFold?` — `null` unless `posture.isTabletop`; otherwise the first hinge
  that is horizontal (`!isVertical`) and separating. A tabletop posture with no such hinge is `null` (treated as not
  tabletop).
- `currentTabletopFold(): TabletopFold?` — the composable read of `currentWindowAdaptiveInfo().windowPosture`.
- Validation: `bottom >= top` (constructor `require`).

## 1a. VerticalFold (`audio.tembra.ui.posture`, design iteration D1)

The hinge of a window half-opened like a book, in window pixels.

| Field | Type | Rule |
|---|---|---|
| `left` | `Float` | The hinge's left edge (`HingeInfo.bounds.left`) |
| `right` | `Float` | Its right edge; equal to `left` for a zero-width fold |

- `VerticalFold.from(posture)`: the first hinge with `isVertical && isSeparating`, else null. Validation:
  `right >= left`.
- `currentVerticalFold()`: the composable read. `FoldSplitRow(fold, …, start, end)` maps it into the layout by its own
  left edge in the window and splits with `FoldSplitGeometry` on the x axis.

## 1b. WideStringGrid (`audio.tembra.ui.tuner`, design iteration D2)

| Field | Type | Rule |
|---|---|---|
| `columns` | `Int` | Cards per row in the wide By Ear grid |
| `card` | `Dp` | The square card's side |

`arrange(count, baseColumns, full, gap, widthBudget, heightBudget, min)`: `baseColumns` (3, or 2 when
`count % 3 != 0 && count % 2 == 0`) while `min(full, byWidth, byHeight) >= min`; otherwise the first larger count that
keeps `min`; otherwise the largest card, raised to `min`. `full` is the card three columns give across
`byEarGridMaxWidthWide` (576 dp) with 20 dp gaps.

## 2. TunerLayout / MetronomeLayout (existing enums, one new entry each)

| Entry | When |
|---|---|
| `Stacked`, `Wide`, `Landscape` | Unchanged bands (width 600 / 840 dp, height 480 dp) |
| `Tabletop` (new) | The window is in tabletop posture (a non-null `TabletopFold`), at any size class |

- `from(windowSizeClass, fold: TabletopFold?)`: `Tabletop` when `fold != null`, otherwise the existing
  `from(windowSizeClass)`.
- Every `when (layout)` in the tuner and metronome packages handles `Tabletop` explicitly (no `else`).

## 3. FoldSplit regions (`audio.tembra.ui.designsystem`)

Pure arithmetic, unit-tested on its own (`FoldSplitGeometry`):

| Input | Meaning |
|---|---|
| `height` | The split layout's height in px |
| `originY` | The layout's top in window px (`NaN` until first placed) |
| `fold` | `TabletopFold?` |
| `gutter` | px kept clear on each side of the fold |

| Output | Rule |
|---|---|
| `upperHeight` | `fold.top − originY − gutter`, clamped to `0..height`; when `fold == null` or `originY` is `NaN`: `(height − 2·gutter) / 2` |
| `lowerTop` | `fold.bottom − originY + gutter`, clamped to `upperHeight..height`; half split mirrors the above |
| `lowerHeight` | `height − lowerTop` |

## 4. State transitions

```
not tabletop ──(fold half-opened, horizontal, spans window)──▶ Tabletop
Tabletop ──(flat / closed / vertical fold / window off the fold)──▶ the size-class layout
```

Only the arrangement changes: the ViewModels and their state (listening, auditions, the running click, every
selection) are untouched (spec FR-008). The same composables move between slots; state that lives in them
(`remember`) is keyed by position and may reset, which is acceptable only for scroll positions and open menus.
