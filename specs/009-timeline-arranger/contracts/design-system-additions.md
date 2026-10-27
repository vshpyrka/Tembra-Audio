# Contract: design-system additions for feature 009

Source of truth: `design/stitch/DESIGN.md` and `theme.json`; evidence screens:
`timeline-arranger/{phone,tablet}`, `projects-library/{phone,tablet}`, `track-effects/*`,
`mixer-timeline/*` (Studio Sound project, timeline parts only). Research R15; data model §7.
Every value is a token; nothing hard-coded (Principle IV).

## Tokens

| Token | Value / source | Evidence |
|---|---|---|
| `DawColors.trackTints: List<Color>` (6) | `lavender` (#c4b5fd, existing), `rose` (#f2a3b3 family, existing), `dustyRose`, `peach`, `mint`, `violet` — the four new ones sampled from `mixer-timeline/tablet-split-console` lanes 3–6 and rounded to the palette's lightness; recorded in `theme.json` under `dawSemanticColors.trackTints` | the four pulled screens; arranger screens use the first two |
| `DawColors.clipCaptionBand` | `surfaceLevel2` at 70 % | `phone-drawer` clips |
| `DawColors.playhead` (existing), `playheadChip` = `lavender` | | arranger + pulled ruler |
| `DawColors.gridOverlay`, `gridOverlayFaint` (existing) | bar / beat lines | arranger |
| `DawColors.selectionOutline` = `colors.primary` | 2 dp | spec |
| `DawColors.unavailableClip` = `outlineVariant` | greyed clip | spec |
| `TembraSpacing.laneHeightCompact` 88 dp, `laneHeightExpanded` 96 dp, `rulerHeight` 44 dp, `toolbarHeight` 48 dp, `transportHeightCompact` 64 dp, `transportHeightExpanded` 72 dp, `headerColumnWidth` 200 dp, `cardOverviewHeight` 56 dp | | layout contract |
| `TembraShapes.clip` = `medium` (12 dp) | rounded clip blocks (8–12 px in the pulled prompts; 12 in the arranger) | |
| Type: `tabularTimecode` (readout, ruler), `tabularParam` (gain, stats), `labelSmall` for chips | existing | |

`ThemeJsonParityTest` is extended for the new named colours; `theme.json` is the file edited
(then the Stitch design system is updated with seeds + prose only, per
`design/stitch/README.md`).

## Icons (through `tools/fetch-material-symbols.sh`)

`content_cut`, `undo`, `redo`, `skip_previous`, `skip_next`, `fast_rewind`, `fast_forward`,
`grid_on`, `zoom_in`, `zoom_out`, `search`, `sort`, `content_copy`, `library_music`,
`expand_more`, `expand_less`, `more_vert`, `audio_file`, `playlist_add`; instrument glyphs:
`mic` (Vocals), `piano` (Keys), `music_note` (Other/Strings), plus the existing `graphic_eq`
(Loops) — labels without a Material glyph (Guitar, Bass, Drums, Percussion, Synth, Brass,
Woodwind) show the chip text only, as the tuner's selector does since 2026-10-12.
`IconAssetsTest` lists them.

## New reusable components (`designsystem`)

| Component | Contract |
|---|---|
| `TembraMenu(expanded, onDismiss, items: List<MenuItemSpec>, modifier, anchor slot)` | dropdown with hover/focus rows, 44 dp rows, tooltip-free (text rows) |
| `TembraTooltip(text, modifier, content slot)` | the plain tooltip the metronome and tuner used ad hoc, promoted |
| `TembraTextField(value, onValueChange, label, modifier, singleLine, maxLength, error)` | names, descriptions, search |
| `TembraDialog(title, text, confirm: DialogAction, dismiss: DialogAction)` | stop-and-leave, delete confirms |
| `TembraCard(modifier, elevated: Boolean, content)` | project cards, settings form |

Feature components (listed in `timeline-screen.md` and `projects-library-screen.md`) stay in
their feature packages; anything used by two features moves to `designsystem` at that moment,
not before (Simplicity).

## Reduce motion

Playhead and overview drawing are information and keep moving; the clip lift on long press,
sheet transitions and card elevation animations snap under `ReduceMotion`.

## Tests

`TimelineTokensTest` (every new token resolves and matches `theme.json`), `IconAssetsTest`,
`TembraMenuTest`, `TembraTooltipTest`, `TembraTextFieldTest`, `TembraDialogTest` (uiTest;
semantics first, hover and focus through injected interaction sources).
