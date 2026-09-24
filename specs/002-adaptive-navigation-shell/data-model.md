# Data Model: Adaptive Navigation Shell and Home

**Feature**: `002-adaptive-navigation-shell` | **Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

No persistence: every entity below lives in memory for the life of the process. Types are
Kotlin in `:shared:ui` unless stated; one type per file (Constitution VII). Evidence for the
design values is the Stitch HTML quoted in [research.md](research.md) R8.

## 1. Destination (registry)

The six places a user can go. A closed, ordered set: an `enum class Destination` in
`audio.tembra.ui.navigation`. Declaration order is display order (spec FR-002). The enum is the
navigation key (it implements Navigation 3's `NavKey`, research R2), so the back stack is a
`List<Destination>` and no parallel key hierarchy exists.

| Value | `id` | `label` (under the icon) | `title` (top bar) | `icon` | `hasSupportingPane` | `placeholderMessage` |
|---|---|---|---|---|---|---|
| `Home` | `home` | Home | Home | `TembraIcon.Home` | true | This is your starting point in Tembra Audio. |
| `Timeline` | `timeline` | Timeline | Timeline | `TembraIcon.Timeline` | false | The timeline arranger arrives with the transport and timeline feature. |
| `Tuner` | `tuner` | Tuner | Tuner | `TembraIcon.Tuner` | false | The instrument tuner arrives with the tuner feature. |
| `Metronome` | `metronome` | Metronome | Metronome | `TembraIcon.Metronome` | false | The metronome arrives with the metronome feature. |
| `Projects` | `projects` | Projects | Projects | `TembraIcon.Projects` | false | Your projects and library arrive with the projects feature. |
| `Profile` | `profile` | Profile | Profile | `TembraIcon.Profile` | false | Your profile and sign-in arrive with the collaboration feature. |

Rules
- `id`, `label` and `title` are unique (test). `Home` is first (test). Exactly six values (test).
- `placeholderMessage` is the one sentence FR-011 / FR-013 require; UI tests assert the exact
  text, so it is data, not copy inside a composable.
- The shell never `when`s over destinations for layout; it reads `hasSupportingPane` and the
  registry. Screen content is looked up through the entry registry (contracts/navigation-shell.md),
  so a destination's content composable knows nothing about the shell or other destinations.

## 2. Navigation state

`ShellUiState(val selected: Destination = Destination.Home)` exposed by `ShellViewModel` as a
`StateFlow` (Constitution II). Intents are plain functions.

| Intent | Precondition | Effect |
|---|---|---|
| `select(d)` | any | `selected = d`; if `d == selected` nothing changes (FR-008) |
| `back()` | `selected != Home` | `selected = Home`; returns `true` (consumed) |
| `back()` | `selected == Home` | no change; returns `false` so the platform handles it (FR-010) |

Derived, not stored: `backStack: List<Destination>` = `[Home]` when `selected == Home`, else
`[Home, selected]`. It is what `NavDisplay` renders; its length is never above two in this
feature. Survives rotation and live resizing because the ViewModel outlives the composition.
Not restored after process death: a relaunch starts at `Home` (spec edge case; FR-021).

## 3. Layout mode

`enum class LayoutMode { BottomBar, Rail, RailWithPanel }` in `audio.tembra.ui.navigation`,
derived from the window, never stored:

| Window width | Window height | `LayoutMode` |
|---|---|---|
| under 600 dp | any | `BottomBar` |
| 600 dp to 839 dp | any | `Rail` |
| 840 dp and wider | under 480 dp | `Rail` |
| 840 dp and wider | 480 dp and taller | `RailWithPanel` |

`LayoutMode.from(width: WindowWidth, isShort: Boolean)` is a pure function tested on the JVM
and the iOS simulator across both thresholds in both directions (SC-004). `WindowWidth`
(existing, `Compact` / `Medium` / `Expanded` at 600 dp and 840 dp) stays as the width bucket.
The root of the app computes both inputs from the platform window-size API (research R3); the
shell composable receives a `LayoutMode` parameter so UI tests can force each layout.

`RailWithPanel` shows the supporting pane only for a destination whose `hasSupportingPane` is
true; for the others the layout is visually identical to `Rail` (spec US3 scenario 3).

## 4. Icon set

`enum class TembraIcon(val resource: DrawableResource)` in `audio.tembra.ui.theme`, one value
per bundled glyph, resolved to an `ImageVector` through `TembraIcons.vector(icon)`. The glyph
files are Material Symbols Outlined, weight 400, no fill, grade 0, optical size 24, copied from
`google/material-design-icons` at the commit recorded in the licence inventory (research R6).

| Value | Glyph name | Resource file | Why |
|---|---|---|---|
| `Home` | `home` | `drawable/ic_home_24.xml` | no design reference; conventional |
| `Timeline` | `graphic_eq` | `drawable/ic_graphic_eq_24.xml` | every phone bottom bar |
| `Tuner` | `music_note` | `drawable/ic_music_note_24.xml` | every phone bottom bar |
| `Metronome` | `av_timer` | `drawable/ic_av_timer_24.xml` | no metronome glyph exists in Material Symbols |
| `Projects` | `folder` | `drawable/ic_folder_24.xml` | every phone bottom bar |
| `Profile` | `person` | `drawable/ic_person_24.xml` | the design's avatar glyph |

Rules: resource names are lowercase with underscores; every file is normalised as research R6
describes (no theme attribute or platform resource references); the set is extended by adding
a file and an enum value, nothing else.

## 5. Motion tokens

`TembraMotion` in `audio.tembra.ui.theme`, provided through the theme like spacing:

| Token | Value | Source |
|---|---|---|
| `standardDuration` | 300 ms | Material 3 motion, `duration.medium2`, the standard for a full-screen content change (research R7) |
| `standardEasing` | cubic-bezier(0.2, 0, 0, 1) | Material 3 `easing.standard` |
| `reduceMotion` | platform preference, read at the root (research R7) | when true, durations are treated as 0 |

Used by the destination transition (FR-008) and the active-marker animation. No screen defines
its own duration.

## 6. Chrome metrics (design reference, added to `TembraSpacing`)

From the Stitch HTML (research R8) and spec FR-019. All in dp.

| Token | Value | Design source |
|---|---|---|
| `topBarHeight` | 56 | `h-14` on wide screens; spec FR-019 |
| `bottomBarHeight` | 64 | `h-16` on every phone bottom bar (plus the system inset) |
| `railWidth` | 64 | `w-16` rail |
| `supportingPaneWidth` | 320 | `w-80` inspector; DESIGN.md desktop layout |
| `navItemMinWidth` | 56 | `min-w-[56px]` bar item |
| `railItemWidth` | 48 | rail `px-2` inside 64 |
| `navBarIconSize` / `navRailIconSize` | 20 / 22 | `text-[20px]` / `text-[22px]` |
| `navItemGap` | 4 | `gap-1` between icon and label |

Active-state treatment (spec FR-019): bar item is a `rounded-full` pill in `primaryContainer`
with `onPrimaryContainer` content and the `clip_glow` shadow; rail item is a `rounded-lg`
(16 dp) square in `secondaryContainer` with `onSecondaryContainer` content. Inactive items use
`onSurfaceVariant`; hover raises to `onSurface` (rail also fills `surfaceContainerHigh`). Labels
use `labelSmall`; the top bar shows the product name as an uppercase `labelSmall` overline in
`primary` and the title in `headlineSmall`.

## 7. Developer view

`enum class DeveloperView { TokenPreview }` in `audio.tembra.ui.devtools`, plus
`DeveloperViewRequest(view: DeveloperView?)` produced by the platform shell only in debug builds
(contracts/developer-view.md). When present, `App` renders the requested view full-screen inside
the theme instead of the navigation shell. Not part of `ShellUiState`; not reachable from any
control (FR-022).

## 8. What disappears

`PlaceholderUiState`, `PlaceholderViewModel`, `PlaceholderScreen`, `PlaceholderScreenContent`
and their tests are deleted (FR-022). `DesignTokenPreview` and its test move to
`audio.tembra.ui.devtools` unchanged in behaviour.
