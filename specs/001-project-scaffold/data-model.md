# Data Model: Project Scaffold

**Feature**: `specs/001-project-scaffold/spec.md` | **Date**: 2026-09-23

The scaffold has three entities. None is persisted; all live in memory for the lifetime of the
process (design tokens) or the screen (placeholder state). Source of truth for every visual value
is `design/stitch/theme.json` (machine-readable) with `design/stitch/DESIGN.md` as the prose
reference.

## 1. Design Token Set

Owned by `:shared:ui`, package `audio.tembra.ui.theme`. One Kotlin file per type (Constitution
VII). Values are transcribed from `theme.json`; a host-side test reads `theme.json` from the
repository and fails on any drift (see `contracts/design-system-api.md`).

### 1.1 Colour scheme roles (`TembraColorScheme`)

Maps one-to-one onto the Material 3 dark colour scheme. Source: `theme.json → namedColors`
(47 roles). Snake-case names in the file become camelCase properties.

| Group | Roles (value) |
|---|---|
| Surfaces | background `#131316`, onBackground `#e4e1e6`, surface `#131316`, surfaceDim `#131316`, surfaceBright `#39393c`, surfaceContainerLowest `#0e0e11`, surfaceContainerLow `#1b1b1e`, surfaceContainer `#1f1f22`, surfaceContainerHigh `#2a2a2d`, surfaceContainerHighest `#353438`, surfaceVariant `#353438`, surfaceTint `#cebdff`, onSurface `#e4e1e6`, onSurfaceVariant `#cac4d4`, inverseSurface `#e4e1e6`, inverseOnSurface `#303033`, inversePrimary `#674bb5`, outline `#948e9d`, outlineVariant `#494552` |
| Primary | primary `#cebdff`, onPrimary `#381385`, primaryContainer `#a78bfa`, onPrimaryContainer `#3c1989`, primaryFixed `#e8ddff`, primaryFixedDim `#cebdff`, onPrimaryFixed `#21005e`, onPrimaryFixedVariant `#4f319c` |
| Secondary | secondary `#ffafd3`, onSecondary `#620040`, secondaryContainer `#85145a`, onSecondaryContainer `#ff93c8`, secondaryFixed `#ffd8e7`, secondaryFixedDim `#ffafd3`, onSecondaryFixed `#3d0026`, onSecondaryFixedVariant `#85145a` |
| Tertiary | tertiary `#d0bcff`, onTertiary `#3c0091`, tertiaryContainer `#ab88ff`, onTertiaryContainer `#40009b`, tertiaryFixed `#e9ddff`, tertiaryFixedDim `#d0bcff`, onTertiaryFixed `#23005c`, onTertiaryFixedVariant `#5516be` |
| Error | error `#ffb4ab`, onError `#690005`, errorContainer `#93000a`, onErrorContainer `#ffdad6` |

Seed colours (`theme.json → seedColors`: primary `#a78bfa`, secondary `#f472b6`, tertiary
`#8b5cf6`, neutral `#0f0f12`) are recorded as documentation only; the scheme is not regenerated
at runtime.

### 1.2 DAW semantic colours (`DawColors`)

Source: `theme.json → dawSemanticColors` (18 values). Exposed through a composition local, not
by overloading the Material scheme (per `design/stitch/README.md`).

| Property | Value | Use |
|---|---|---|
| canvasLevel0 | `#0F0F12` | viewport shell |
| surfaceLevel1 | `#18171C` | sidebars, track backplates |
| surfaceLevel2 | `#212026` | track lanes |
| surfaceLevel2Border | `#2B2A34` | 1 dp seams (the only "elevation") |
| surfaceLevel3 | `#292832` | floating toolbars, chips, pad grids |
| surfaceLevel4 | `#32303C` | hovered / pressed raised state |
| lavenderPrimary | `#A78BFA` | active playhead, automation lines |
| lavenderLight | `#C4B5FD` | primary accents |
| playhead | `#C084FC` | playhead line |
| rose | `#F472B6` | recorded clips, alert markers |
| roseLight | `#FDA4AF` | loop regions |
| magenta | `#E879F9` | clip accents |
| winePad | `#5B2A3D` | pad matrix background |
| winePadMid | `#78354E` | pad mid state |
| winePadActive | `#9D4B68` | active pad |
| gridOverlayFaint | `#A855F7` at alpha `0x1F` | beat subdivisions |
| gridOverlay | `#C084FC` at alpha `0x33` | bar divisions |
| clipGlow | `#A78BFA` at 25 % alpha, 12 dp blur | active clip glow (drawn, not a shadow) |

### 1.3 Typography (`TembraTypography`)

Source: `theme.json → typography` (12 styles) and `fonts` (headline and label: Space Grotesk;
body: Inter). Units: `px` in the file map to `sp` for size and line height, `em` to `em`.

| Style | Family | Size / line | Weight | Tracking | Notes |
|---|---|---|---|---|---|
| headlineLg | Space Grotesk | 32 / 40 | 600 | -0.02em | |
| headlineLgMobile | Space Grotesk | 24 / 32 | 600 | -0.01em | compact width |
| headlineMd | Space Grotesk | 22 / 28 | 600 | -0.01em | |
| headlineSm | Space Grotesk | 18 / 24 | 600 | | |
| bodyLg | Inter | 16 / 24 | 400 | | |
| bodyMd | Inter | 14 / 20 | 400 | | |
| bodySm | Inter | 12 / 16 | 400 | | |
| labelLg | Space Grotesk | 14 / 18 | 500 | 0.01em | |
| labelMd | Space Grotesk | 12 / 16 | 500 | 0.02em | |
| labelSm | Space Grotesk | 10 / 14 | 600 | 0.04em | |
| tabularTimecode | Space Grotesk | 15 / 20 | 500 | 0.05em | `tnum` on |
| tabularParam | Inter | 11 / 14 | 500 | 0.02em | `tnum` on |

Mapping onto Material `Typography` slots: headlineLg → `headlineLarge`, headlineMd →
`headlineMedium`, headlineSm → `headlineSmall`, bodyLg/Md/Sm → `bodyLarge/Medium/Small`,
labelLg/Md/Sm → `labelLarge/Medium/Small`. `headlineLgMobile`, `tabularTimecode` and
`tabularParam` have no Material slot and are exposed as extra properties.

Fonts are bundled as static TTF instances (Space Grotesk Medium 500, SemiBold 600, Bold 700;
Inter Regular 400, Medium 500, SemiBold 600), both under the SIL Open Font License 1.1, with the
OFL text kept next to the files.

### 1.4 Shapes (`TembraShapes`)

Source: `theme.json → rounded` (1rem = 16 dp): sm 4 dp, default 8 dp, md 12 dp, lg 16 dp,
xl 24 dp, full = pill. Mapping onto Material `Shapes`: extraSmall 4, small 8, medium 12,
large 16, extraLarge 24.

### 1.5 Spacing and sizing (`TembraSpacing`)

Source: `theme.json → spacing`, `touchTargetMin`: xs 4 dp, sm 8 dp, md 12 dp, lg 20 dp,
xl 28 dp, gutter 12 dp, pageMargin 16 dp, touchTargetMin 44 dp.

### 1.6 Elevation rule

No drop shadows anywhere. Depth is expressed by stepping `surfaceLevel*` colours and 1 dp
`surfaceLevel2Border` seams. The only glow is `clipGlow`. Encoded as a documented convention
plus a zero-elevation Material default in the theme; not a data type.

### 1.7 Window size classes

Source: `theme.json → layoutBreakpoints` and Constitution IV. The scaffold defines its own
`WindowWidth` enum with the Material width buckets: Compact (< 600 dp), Medium (600–839 dp),
Expanded (≥ 840 dp), computed from the measured available width (`BoxWithConstraints`) so no
extra library is needed. The placeholder screen treats Medium and Expanded the same ("wide").

**Validation rules**
- Every colour property equals the hex in `theme.json` (host-side test, exact match).
- Every text style's family, size, line height and weight equal the file (host-side test).
- Screens never reference `Color(0x…)`, raw `dp`/`sp` sizes or `FontFamily` directly outside
  `audio.tembra.ui.theme` (review checklist item; no lint tool in this feature).

## 2. Placeholder Screen State

Owned by `:shared:ui`, package `audio.tembra.ui.placeholder`.

| Field | Type | Rules |
|---|---|---|
| pressCount | Int | starts at 0; increments by exactly 1 per activation (tap, click, keyboard); never negative; survives rotation and window resize (held by the screen's ViewModel) |
| engineDescription | EngineDescriptor | read once on screen creation from the engine facade |

**State transitions**: `Idle(pressCount = n)` --activate--> `Idle(pressCount = n + 1)`. There is
no other state. Not persisted; a process restart resets it to 0 (acceptable for a placeholder).

## 3. Engine Descriptor

Owned by `:shared:engine`, package `audio.tembra.engine`.

| Field | Type | Rules |
|---|---|---|
| name | String | exactly `Tembra Engine` |
| version | SemanticVersion | parsed from the native string; `0.1.0` in this feature |
| compiledLibraries | List<String> | names and versions of native libraries compiled into the engine, e.g. `oboe 1.11.0`, `miniaudio 0.11.25`, `dr_wav 0.14.5`, `signalsmith-stretch 1.1.0`; empty on the fake engine |
| rawDescription | String | the exact string returned by the C ABI (`Tembra Engine 0.1.0 (oboe 1.11.0; miniaudio 0.11.25; dr_wav 0.14.5; signalsmith-stretch 1.1.0)`) |

`SemanticVersion` (`major.minor.patch`, non-negative ints, comparable) lives in `:shared:core`,
package `audio.tembra.core.version`, because it is pure and will be reused for the project file
schema version. Parsing rejects anything that is not three dot-separated non-negative integers.

**Relationships**: `EngineDescriptor` is produced by `AudioEngine.describe()` (see
`contracts/audio-engine-facade.md`) and consumed by the placeholder ViewModel. The C ABI string
format is fixed in `contracts/engine-c-abi.md`; the Kotlin side never parses beyond the first
`name version` pair plus the parenthesised list.
