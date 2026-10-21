# Feature Specification: Adaptive Navigation Shell and Home

**Feature Branch**: `002-adaptive-navigation-shell`

**Created**: 2026-09-24

**Status**: Draft

**Input**: User description: "The next thing I would like you to do is to create a navigation structure for the app. Check existing design screenshots or designs provided by Google Stitch platform. I want you to build me a home page which will be a starting point. The home page should display different UI components depending on the device type. You can check the existing screenshots. For example: The mobile design shows the bottom toolbar items. The foldable device displays the navigation rail on the left side. Larger devices like a tablet or desktop will display an additional right-side panel with additional content. Build a very simple navigation that will include: The home page, The timeline, The user profile, The metronome, The tuner. I would also recommend to pull the material icons and the fonts provided by Google, so feel free to pick any icon or any font and incorporate them into the app. https://github.com/google/material-design-icons the link. You can also make a research and find the best place where you can pull those material design icons and fonts. Make sure that you also add them into the design system or design module that we have already placed in the app so it is shared for both iOS and Android. Do not add elements that you are not certain about, or they are ambiguous, or they are present on one screen and it's not present on another screen."

## What the design shows *(evidence for every decision below)*

The sixteen Stitch screens under `design/stitch/screens/` (eight screens, phone and tablet
variants) and the four reference mockups under `design/mockups/` were reviewed screen by
screen. Only elements that appear consistently were carried into this specification.

| Element | Where it appears | Verdict |
|---|---|---|
| Bottom navigation bar: five items, icon above a short label, the active item sits on a raised lavender circle | Every phone screen that has a bottom bar (tuner, tuner by ear, effects, drum sequencer, export) and the tablet timeline | **Kept** as the compact-width navigation; **icons only since 2026-10-17**, drawn by the platform design system's standard compact navigation bar (owner's decision, FR-003, FR-019) |
| Left navigation rail, 64 px wide, icon above label, active item on a filled rounded square | Seven of eight wide screens (timeline, projects, effects, tuner, tuner by ear, export; the tablet timeline's "Tracks / FX" rail is the known inconsistency) | **Kept** as the wide-width navigation; **icons only since 2026-10-17**, drawn by the platform design system's standard collapsed rail (owner's decision, FR-003, FR-019) |
| Top bar showing the product name and the current screen title (or project name) | Every main screen | **Kept**, reduced to name and title; **removed from the shell on 2026-10-16** (owner's request: it only repeated the selected item and took height) |
| Top-bar icons: menu, cloud state, help, share, avatar, folder, settings | Vary from screen to screen; no screen defines what they open | **Excluded** (ambiguous) |
| Right-hand panel, 320 px, holding the loop library (category chips and a pad bank) | Timeline screens and mockups only; absent on tuner, effects, export and projects | **Kept as a slot** on expanded widths; on Home it held a "Library" empty state (FR-012) until Home was removed on 2026-10-17; no destination fills the slot now |
| Transport bar (volume, rewind, play, timecode, speed, metronome toggle) | Every DAW screen | **Excluded**: it belongs to the transport and timeline feature, which comes after the audio spine in the fixed build order |
| Navigation item "Projects" | Every design rail and bar | **Kept** as a placeholder destination, like Tuner (clarified 2026-09-24) |
| Navigation items "Effects", "Pads", "Remote" | Design rails and bars | **Not in this feature**: Effects and Pads remain primary destinations and join the navigation with their own features; Remote follows with collaboration (see Assumptions) |
| Mockup navigation "Users", "Comments", "Remote" | The four August 2026 mockups only | **Superseded** by the Stitch screens |

## Clarifications

### Session 2026-09-24

- Q: Should this feature's navigation contain only the five destinations you listed (Home, Timeline, Tuner, Metronome, Profile), with Effects, Pads and Projects added later when their own features land? → A: Five plus Projects. Projects is included now as a placeholder destination like Tuner; Effects and Pads are excluded until their features land.
- Q: What should happen to the scaffold's placeholder screen, the counter button and the design-token preview, once Home replaces it as the start screen? → A: B. Keep the token preview as a developer-only view outside the navigation, available in debug builds only; delete the counter button and its tests.
- Q: On windows 840 dp and wider, is the "Library" panel beside Home always visible, or can the user collapse and reopen it? → A: A. Always visible at 840 dp and wider (hidden only when the window is shorter than 480 dp); no collapse control.
- Q: When the user switches between destinations, should the change be instant or animated? → A: B. A short fade between destinations, and the active marker animates too, using the standard transition duration from the platform's design guidelines rather than a custom value.

### Session 2026-10-16

- Owner's request after the device-matrix run: the top bar that showed only the selected tab is redundant, takes a lot of space and does nothing, so the shell drops it on every destination. The `TembraTopBar` component stays in the design system for a later screen that needs a title bar. FR-007 is reversed; FR-002, FR-015, FR-018, FR-019, FR-022, the edge cases, User Story 1 and the Destination entity are amended to match.

### Session 2026-10-17

- On 2026-10-16 the owner asked, as an experiment, to remove the Home section and show only icons in the
  navigation. On 2026-10-17 the owner kept the result: "I like the result with not hardcoded navigation bar height,
  and usage of expressive material components … the navigation bar should only display the icons". So Home is
  removed and the app opens on Timeline, the first of the five remaining destinations, with back from any other
  destination returning to Timeline; the navigation items show icons only, each item's name staying as its
  accessible name and appearing as a tooltip on hover; and the bottom bar and the rail are the platform design
  system's standard compact navigation bar and collapsed rail, which bring their own sizes, indicator shape and
  indicator motion in place of the design's 64 dp bar and 64 dp rail. Home's "Library" panel goes with Home; the
  side-panel slot stays for a later destination that has supporting content. FR-003, FR-011, FR-012 and User
  Story 3 are reversed; FR-001, FR-002, FR-004 to FR-006, FR-008, FR-010, FR-014, FR-015, FR-017 to FR-020, User
  Stories 1, 2 and 5, the edge cases, the Destination, Navigation State and Icon Set entities, SC-001, SC-003,
  SC-006, SC-008 and the assumptions are amended to match.
- Later the same day the owner adopted new Tuner and Metronome glyphs, after an experiment comparing the nearest
  Material Symbols glyphs, metronome glyphs from other open icon sets and generated drafts: the Tuner item shows a
  hand-drawn tuning fork and the Metronome item a hand-drawn pyramid metronome, both drawn for the project on the
  Material Symbols 24 dp grid in the Outlined regular weight, because Material Symbols has neither. FR-016, User
  Story 5, the Icon Set entity and the icon assumptions are amended.

## User Scenarios & Testing *(mandatory)*

The users of this feature are musicians and producers opening Tembra Audio on a phone, a
foldable, a tablet or an Android desktop-mode window, and the contributors who will build every
later screen inside this shell. The feature delivers the persistent frame of the app (the
navigation control and an optional side panel; its top bar was removed on 2026-10-16), five
destinations of which the first, Timeline, is the starting point, and the shared icon set the
frame uses. Until 2026-10-17 a sixth destination, Home, was the starting point and the other
five were reachable from it.

### User Story 1 - Start on Timeline and move between the five destinations (Priority: P1)

A user opens the app and lands on Timeline. From the navigation control they can reach Tuner,
Metronome, Projects and Profile, and come back to Timeline. The item for the current destination is
visibly marked and is what identifies the destination (the top bar that repeated its name was removed on
2026-10-16). The items show icons only; each item's name is what a screen reader announces. Until
2026-10-17 the app opened on Home, a sixth destination, and each item showed its name under its icon.

**Why this priority**: The navigation structure is the point of the feature. Without it no
other story can be demonstrated.

**Independent Test**: Launch the app, confirm Timeline is shown, tap each of the five items in
turn and confirm the matching destination appears and the item is marked active.

**Acceptance Scenarios**:

1. **Given** a cold launch on either platform, **When** the app finishes opening, **Then** the
   Timeline destination is shown and its navigation item is marked active.
2. **Given** any destination, **When** the user selects a different navigation item, **Then**
   the destination for that item replaces the current one and the item becomes the only active
   item.
3. **Given** a destination is shown, **When** the user selects its own (already active) item
   again, **Then** nothing changes and no duplicate screen is created.
4. **Given** the user is on Tuner, Metronome, Projects or Profile on Android, **When** they
   use the system back action, **Then** the app returns to Timeline; a second back from Timeline leaves
   the app as the platform normally does.

---

### User Story 2 - The frame adapts to the window (Priority: P1)

The same build shows a bottom navigation bar on a phone, a left rail on a wider window such as
an unfolded foldable or a small tablet in portrait, and a left rail plus a right-hand panel on
a large tablet or a desktop-mode window, beside a destination that has supporting content (none
has since Home was removed on 2026-10-17). When the window is resized live (folding, unfolding,
rotating, dragging a desktop window), the frame switches layout without losing the user's
place.

**Why this priority**: The request is explicitly about different components per device size,
and the constitution requires every screen to follow the window size rather than the device
type.

**Independent Test**: Render Timeline at 360 dp, 700 dp and 1024 dp widths on both platforms and
confirm the three layouts; resize across the 600 dp and 840 dp thresholds while on Tuner and
confirm Tuner stays selected. (Home was the rendered destination until 2026-10-17.)

**Acceptance Scenarios**:

1. **Given** a window narrower than 600 dp, **When** any destination is shown, **Then** the
   items are in a bottom navigation bar, there is no rail and no side panel.
2. **Given** a window 600 dp to 839 dp wide, **When** any destination is shown, **Then** the
   items are in a left rail, there is no bottom bar and no side panel.
3. **Given** a window 840 dp or wider, **When** a destination that has supporting content is
   shown, **Then** the items are in a left rail and a right-hand panel is shown beside its
   content. (Amended 2026-10-17: no destination has supporting content since Home was removed, so
   at this width every destination shows the rail without a panel; until then this scenario was
   Home with its panel.)
4. **Given** the user is on any destination, **When** the window is resized across a
   threshold, **Then** the navigation control changes form and the same destination remains
   selected with its content state intact.
5. **Given** a phone held in landscape (wide but short), **When** any destination is shown,
   **Then** the rail is used and the right-hand panel is hidden so the content keeps a usable
   height. (Home was the destination named here until 2026-10-17.)

---

### User Story 3 - The Home page and its side panel (Priority: P2)

**Reversed 2026-10-17**: Home and its "Library" panel were removed at the owner's request (FR-011
and FR-012 reversed). The story below is kept as the record of what was built and accepted; the
side-panel slot it proved stays in the frame (FR-006) for a later destination with supporting
content.

A user on Home sees the destination title and a one-line note, the same placeholder form as
the other five destinations: no design screen defines a home page and the user chose not to
invent one. On expanded widths a right-hand panel titled "Library" appears beside it with an
empty state, reserving the place the design gives to the loop library.

**Why this priority**: The shell is usable with placeholder content; the panel proves the
three-column layout the design prescribes for wide windows.

**Independent Test**: Open Home at compact and expanded widths and confirm the placeholder
and, at expanded width, the "Library" panel with its empty state.

**Acceptance Scenarios**:

1. **Given** Home at any width, **When** it is shown, **Then** it displays the title "Home"
   and one sentence describing it as the starting point, with no clipped or overlapping
   elements and no other content.
2. **Given** Home at 840 dp or wider, **When** it is shown, **Then** a right-hand panel
   320 dp wide titled "Library" is displayed beside the Home content, containing only an
   empty-state message that loops and sounds will appear here.
3. **Given** Timeline, Tuner, Metronome, Projects or Profile at 840 dp or wider, **When** shown,
   **Then** no right-hand panel is displayed (these destinations have no supporting content in
   this feature), matching the tuner and effects design screens.

---

### User Story 4 - Pointer, keyboard and screen reader (Priority: P2)

A user with a mouse or trackpad sees a hover state on every navigation item; a keyboard user
can move focus through the items and activate one; a screen-reader user hears each item's
label and whether it is selected.

**Why this priority**: The constitution makes pointer and keyboard input first-class and
requires semantics on every control; retrofitting this later is costly.

**Independent Test**: Hover, tab through and activate items with a keyboard, and read the
items with the platform screen reader on both platforms.

**Acceptance Scenarios**:

1. **Given** a pointer, **When** it hovers over a navigation item, **Then** the item shows a
   hover state.
2. **Given** a keyboard, **When** focus is moved through the navigation control, **Then**
   every item receives a visible focus indicator in order, and pressing the activation key
   selects the focused item.
3. **Given** a screen reader, **When** it reaches a navigation item, **Then** it announces the
   item's label and its selected state.
4. **Given** any layout, **When** the touch targets are measured, **Then** every navigation
   item is at least 44 dp in both dimensions.

---

### User Story 5 - The shared design system gains the icons and the frame (Priority: P3)

A contributor building the next screen finds the navigation icons, the frame components (top
bar, bottom bar, rail, side panel) and the type styles in the shared design system, uses them
by name, and sees identical rendering on Android and iOS. The icons come from Google's
Material Symbols set and the fonts from Google Fonts, bundled with the app; since 2026-10-17
the Tuner and Metronome glyphs are the project's own, drawn on the Material Symbols grid.

**Why this priority**: Shared assets prevent per-platform drift, but they are only valuable
once the shell that uses them exists.

**Independent Test**: Compare the five navigation glyphs on both platforms pixel-for-pixel at
the same size (six until Home was removed on 2026-10-17); confirm the licence inventory lists
the icon set; confirm the shell code contains no literal colour, size or font values.

**Acceptance Scenarios**:

1. **Given** the app on both platforms, **When** the navigation control is rendered, **Then**
   the five icons are the same glyphs at the same size on both (Material Symbols glyphs, and
   since 2026-10-17 the project's own Tuner and Metronome glyphs; all Material Symbols until then).
2. **Given** the shared design system, **When** a contributor looks for an icon, a frame
   component or a label style, **Then** each is available by name without touching platform
   code.
3. **Given** the licence inventory, **When** it is reviewed, **Then** it lists the icon set,
   its version and its permissive licence, and the two font families remain listed.

---

### Edge Cases

- The window is resized continuously across 600 dp and 840 dp (desktop-mode window drag,
  fold and unfold): at each threshold the navigation control changes form in one step (no
  frame shows both a bar and a rail), the destination content is not recreated, and the
  selected destination and its content state are kept.
- The window is wide but shorter than 480 dp (phone landscape): the rail is used and the side
  panel is hidden.
- The user enables the largest system font size: the navigation items show no text, so the
  bottom bar keeps its height and no item overflows or overlaps (amended 2026-10-17; until then
  the labels had to stay readable and unclipped and the bar grew in height as needed).
- The device has a display cutout, rounded corners or a gesture navigation area: the content
  clears the status area (and a desktop window's caption), the bottom bar sits above the gesture area, and the rail clears a
  side cutout in landscape.
- The user taps two items in quick succession: the last tap wins and exactly one destination
  is shown.
- The app is killed by the system and relaunched: it opens on Timeline (Home until 2026-10-17;
  no destination is remembered in this feature).
- The device is in light mode: the app keeps the design's dark theme.
- The user has enabled the platform's reduce-motion setting: destination switches happen
  without animation and the active marker jumps to the new item.
- Very narrow windows (under 360 dp, for example a split-screen phone): the five items still
  fit in the bottom bar; nothing is clipped (until 2026-10-17: six items, each with its label).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001** (amended 2026-10-17): The app MUST open on Timeline, the first destination, on
  every launch. Until 2026-10-17 it opened on Home.
- **FR-002** (amended 2026-10-17): The navigation MUST contain exactly five destinations, in
  this order: Timeline, Tuner, Metronome, Projects, Profile. No other items (no Home, Effects,
  Pads, Remote, menu, avatar or settings) MUST appear in the navigation control or anywhere else
  in the frame. Until 2026-10-17 it contained six, with Home first.
- **FR-003** (reversed 2026-10-17): Every destination MUST have a text label and an icon. The
  bottom bar and the rail MUST show the icon only, and the label MUST be the icon's accessible
  name, so a screen reader still announces a named tab (FR-015). Until 2026-10-17 the label and
  the icon were both visible in the bar and in the rail, the icon above the label as in the design.
- **FR-004**: In windows narrower than 600 dp the items MUST be shown in a bottom
  navigation bar and there MUST be no rail and no side panel.
- **FR-005**: In windows 600 dp to 839 dp wide the items MUST be shown in a left rail and
  there MUST be no bottom bar and no side panel.
- **FR-006** (amended 2026-10-17): In windows 840 dp or wider the items MUST be shown in a left
  rail, and a destination that has supporting content MUST show it in a right-hand panel 320 dp
  wide beside its main content. No destination has supporting content since 2026-10-17; until
  then Home did, and it was the only one. When the window is shorter than 480 dp the panel MUST
  be hidden.
- **FR-007** (reversed 2026-10-16): The frame MUST NOT show a top bar on any destination: the
  active navigation item names the destination, the content starts right under the status bar
  (or a desktop window's caption), and a rail runs the full window height. Until 2026-10-16 a
  full-width top bar above the rail showed the product name "Tembra Audio" and the current
  destination's title.
- **FR-008** (amended 2026-10-17, 2026-10-20, 2026-10-21): Selecting an item MUST show its destination and
  mark that item as the only active item; selecting the already active item MUST have no effect.
  The switch MUST be animated: the outgoing destination fades into the incoming one using the
  standard transition duration of the platform's design guidelines (a few hundred milliseconds,
  recorded as a token in the shared design system, never a custom per-screen value), and the
  active marker moves to the new item with the navigation component's own standard animation.
  When the platform's reduce-motion setting is on, the switch MUST be instant. A destination that
  prepares its content over the frames after the switch, so that no single frame carries the
  whole screen (the Metronome since 2026-10-20, the Tuner since 2026-10-21), starts its fade-in,
  with the same duration, once that content is ready, a few frames after the outgoing destination
  starts to fade out; under reduce motion it appears without animation once ready. Until
  2026-10-17 the marker's move used the same standard transition duration as the fade.
- **FR-009**: A change of layout caused by resizing MUST preserve the selected destination and
  its content state.
- **FR-010** (amended 2026-10-17): On Android the system back action from any destination other
  than Timeline MUST return to Timeline; from Timeline it MUST behave as the platform default
  (leave the app). Until 2026-10-17 back returned to Home.
- **FR-011** (reversed 2026-10-17): There MUST be no Home destination. Until 2026-10-17 Home
  displayed the title "Home" and one sentence describing it as the starting point of the app,
  and nothing else (the same placeholder form as FR-013).
- **FR-012** (reversed 2026-10-17): The "Library" panel MUST NOT be shown on any destination;
  the side-panel slot of FR-006 stays empty until a later feature gives a destination
  supporting content. Until 2026-10-17, on windows 840 dp or wider, Home's right-hand panel was
  titled "Library" and contained only an empty-state message saying that loops and sounds will
  appear here, with no chips, pads, sample data or controls and no way to collapse or hide it.
- **FR-013**: Timeline, Tuner, Metronome, Projects and Profile MUST each show a placeholder consisting
  of the destination title and one sentence saying the feature is coming, and nothing else.
  They MUST NOT show a transport bar, controls or sample data.
- **FR-014** (amended 2026-10-17): Every navigation item MUST show a hover state under a
  pointer, a visible focus indicator when focused, MUST be activatable from the keyboard, and
  MUST be at least 44 dp in both dimensions. Since the items show icons only, hovering an item
  MUST also show its label as a tooltip, and the focus indicator is a ring drawn inside the
  item's active-marker shape (the bar's pill, the rail's circle) in the secondary colour. Until
  2026-10-17 the label was drawn under the icon, no tooltip was required, and the focus ring was
  the design system's own, in the primary colour.
- **FR-015** (amended 2026-10-17): Every navigation item MUST expose its label (since
  2026-10-17 as the icon's accessible name, not drawn), its selected state and a stable test
  identifier to accessibility services and UI tests; the bottom bar, rail and side panel MUST
  each be identifiable the same way. The test identifiers did not change with the amendment.
- **FR-016** (amended 2026-10-17): The icons MUST come from Google's Material Symbols set
  (Apache-2.0), except the Tuner and Metronome navigation glyphs, which are the project's own
  artwork (a tuning fork and a pyramid metronome, neither of which Material Symbols has) drawn on
  the same 24 dp grid in the same Outlined regular weight. All icons MUST be bundled with the app
  (never fetched at run time), MUST be added to the shared design system as named icons, and
  MUST be used by screens only through those names. The licence inventory MUST list the icon set
  with its version and source; the project's own glyphs need no entry. Until 2026-10-17 every
  icon, the Tuner's music note and the Metronome's timer included, came from Material Symbols.
- **FR-017** (amended 2026-10-17): All text in the frame MUST use the shared design system's
  type styles (the bundled Space Grotesk and Inter families) and all colours, sizes, spacing and
  shapes MUST come from the shared design tokens, except the bottom bar's and the rail's own
  sizes and indicator shapes, which come from the platform design system's standard components
  (FR-019). The frame MUST contain no literal visual values. Until 2026-10-17 the bar's and the
  rail's sizes were shared design tokens too.
- **FR-018** (amended 2026-10-17): The bottom bar, rail and side-panel frame MUST be shared
  design-system components implemented once for both platforms, so later features reuse them
  unchanged; since 2026-10-17 the bar and the rail wrap the platform design system's standard
  compact navigation bar and collapsed rail instead of drawing their own. The top bar component
  stays in the design system for later screens, though the frame no longer shows it (FR-007).
- **FR-019** (amended 2026-10-17): The frame MUST match the design reference where the design
  fixes a value: the top bar component 56 dp high (not shown by the frame since 2026-10-16);
  side panel 320 dp wide; the bar and the rail on the lowest surface container colour; inactive
  items in the muted on-surface colour. The bottom bar and the rail MUST take their other
  metrics from the platform design system's standard compact navigation bar and collapsed rail,
  not from fixed values of the app: the bar is at least 64 dp high plus the system inset below
  it (88 dp in all on a Pixel 6 Pro, 98 pt on an iPhone 14), the collapsed rail is 96 dp wide,
  the active bar item sits on a pill-shaped indicator (56 × 32 dp) in the primary container
  colour, and the active rail item on a filled circle (56 dp) in the secondary container
  colour. Until 2026-10-17 the bar was exactly 64 dp high plus the system inset, the rail 64 dp wide, the
  active bottom-bar item drawn on a raised circle in the primary lavender and the active rail
  item on a filled rounded square in the secondary container colour.
- **FR-020** (amended 2026-10-17): Automated tests MUST cover: the navigation state logic (start
  on the first destination, selection, no-op reselection) on both the Android-side host and the
  iOS simulator; each of the three layouts at a representative width; layout switching
  preserving selection; and the accessibility semantics of the items. Tests MUST NOT depend on
  transition timing: they wait for a switch to finish or run with animations disabled.
  Screenshots of the start destination at compact (360 dp), medium (about 700 dp) and expanded
  (1024 dp or wider) widths on both platforms MUST be stored under the feature directory and
  reviewed against the Stitch references before acceptance. Until 2026-10-17 the start
  destination, and so the screenshots' subject, was Home.
- **FR-021**: The feature MUST NOT implement a transport bar, a navigation drawer or menu,
  cloud or sync state, sharing, help, sign-in or accounts, remembering the last destination, or
  any audio behaviour.
- **FR-022**: The scaffold's design-token preview MUST remain available as a developer-only
  view: present in debug builds only, absent from release builds, never reachable from the
  navigation control, and opened through a developer mechanism chosen in the
  plan. Its existing automated test MUST keep passing. The scaffold's counter button, its
  state and its tests MUST be removed.

### Key Entities

- **Destination**: one of the five places the user can go (six, with Home, until 2026-10-17).
  Attributes: stable identifier, label (the icon's accessible name; shown under the icon until
  2026-10-17), title (shown by its placeholder; the top bar that also showed it was removed on
  2026-10-16), icon name, order, and whether it has supporting content for the side panel.
- **Navigation State**: which destination is selected. Starts at the first destination,
  Timeline (Home until 2026-10-17); changes only through the navigation control or the Android
  back action; survives rotation and resizing.
- **Layout Mode**: the form the frame takes, derived from the window: bottom bar (under
  600 dp), rail (600 dp to 839 dp), or rail with side panel (840 dp and wider, height 480 dp
  or more).
- **Icon Set**: the named glyphs available to screens through the shared design system, with
  their source and licence: Material Symbols glyphs and, since 2026-10-17, the project's own
  Tuner and Metronome glyphs drawn on the same grid (only Material Symbols glyphs until then).
  In this feature it contains at least the navigation glyphs (five since 2026-10-17, when
  Home's glyph left the set; six before).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On both platforms a cold launch shows Timeline (Home until 2026-10-17), and every
  one of the five destinations is reachable with a single tap or click from any other
  destination.
- **SC-002**: The transition to a new destination starts within 100 ms of the tap on a
  current mid-range phone and finishes within the standard transition duration; no switch
  shows a blank frame.
- **SC-003**: Screenshots at 360 dp, about 700 dp and 1024 dp or wider on Android and on iOS
  show the bottom bar, the rail, and the rail with side panel respectively, with zero clipped,
  overlapping or off-screen elements, and the placement and sizes match the design reference on
  visual review. Amended 2026-10-17: at 1024 dp the side panel appears only beside a destination
  with supporting content (none now), and the bar's and the rail's sizes are judged against
  FR-019, not the design's 64 dp.
- **SC-004**: In an automated resize test crossing both thresholds in both directions, the
  selected destination is preserved in 100% of transitions.
- **SC-005**: 100% of navigation items are reachable and activatable by keyboard, announce
  their label and selected state to the screen reader, and measure at least 44 dp.
- **SC-006**: The five navigation glyphs (six until 2026-10-17) are identical on both platforms
  at the same size, the licence inventory lists the icon set, and a review of the frame's source
  finds zero literal colour, size or font values.
- **SC-007**: All test lanes are green and the build produces zero compiler or lint warnings.
- **SC-008**: A contributor unfamiliar with the code, given the shared design system, can name
  the icon, the frame component and the label style to use for a new destination in under 5
  minutes. Amended 2026-10-17: a new destination needs no label style, since its label is the
  icon's accessible name and is not drawn.

## Assumptions

- **Destination set and the constitution.** The constitution's primary destinations
  (Timeline, Tuner, Effects, Pads, Projects) stay as written; the user confirmed on 2026-09-24
  that no amendment is needed. This feature ships six destinations: the five the user asked
  for (Home, Timeline, Tuner, Metronome, Profile) plus Projects as a placeholder, added in the
  clarification session. Effects and Pads remain primary destinations and are added to the
  navigation when their features land; Remote follows with collaboration. On 2026-10-17 the
  owner removed Home, which leaves five destinations.
- **Order of items.** The user's list was not presented as an order. The chosen order is Home,
  Timeline, Tuner, Metronome, Projects, Profile: Home first as the start, the musical tools
  together as in the design, Projects after them as in every design bar and rail, and the
  account last as is conventional and as the design places the avatar at the far end. Since
  Home was removed on 2026-10-17, Timeline is first and is the start.
- **Six items in the compact bar.** The design's bottom bar holds five items; the user chose
  six. Each item therefore gets a sixth of the width (60 dp at 360 dp), which still satisfies
  the 44 dp touch target; the plan must verify that every label, including "Metronome", fits
  unclipped at 360 dp and at the largest system font size (FR-014, Edge Cases). Since
  2026-10-17 the bar holds five icon-only items, a fifth of the width each (72 dp at 360 dp),
  and there is no label to fit.
- **Window widths, not device types.** "Mobile", "foldable" and "tablet or desktop" map to the
  window width thresholds 600 dp and 840 dp used throughout the industry and already adopted
  by the project. An unfolded foldable falls in the middle band on some devices and in the
  wide band on others; the layout follows the width it actually has. A wide window shorter
  than 480 dp (phone landscape) hides the side panel.
- **Placeholders.** Timeline, Tuner and Metronome are placeholders because their real screens
  arrive with the audio spine, transport and timeline, metronome and tuner features in the
  fixed build order. Projects is a placeholder because project files and the library arrive
  with later features; the projects-library design screen is not built here. Profile is a
  placeholder because accounts, sign-in and collaboration are the last item in the build
  order; the sign-in design screen is not built here.
- **No transport bar.** The constitution places the transport bar on every DAW screen, but it
  needs the engine and transport feature; placeholders are not DAW screens.
- **Top bar is name and title only.** The menu, cloud-state, help, share, avatar, folder and
  settings icons in the top bars of the design vary between screens and none of them has a
  defined behaviour, so they are excluded per the user's instruction. On 2026-10-16 the owner
  removed the remaining name-and-title bar from the frame as well (FR-007).
- **Icon choices.** Where the design shows a glyph for a destination it was taken from the
  phone bottom bars, which agree on every screen; the rails use different glyphs for the same
  items and were not followed. Chosen glyphs (all present in the current Material Symbols
  code-point list): Home `home` (no design reference; removed with Home on 2026-10-17); Timeline `graphic_eq` (every phone
  bottom bar); Tuner `music_note` (every phone bottom bar); Metronome `av_timer` (Material
  Symbols has no metronome glyph; the mockups show a custom pendulum glyph that the metronome
  feature may introduce later); Projects `folder` (every phone bottom bar); Profile `person`
  (the design's avatar glyph). Amended 2026-10-17: Tuner shows a tuning fork (two prongs
  joined by a U, a stem, a sound arc on each side) and Metronome a pyramid metronome (body,
  scale plate, and a rod that leaves through a gap in the body's right edge so the crossing
  stays legible), both the project's own artwork, because Material Symbols has neither; the
  owner chose them after comparing the nearest Material Symbols glyphs, metronome glyphs from
  other open icon sets and generated drafts (FR-016).
- **Where the icons and fonts come from (decided at the user's request).** Icons are
  Material Symbols Outlined glyphs, the set the Stitch screens themselves use, taken from
  Google's `google/material-design-icons` repository as the ready-made vector asset the
  repository publishes for every glyph (one file per glyph, 24 dp, regular weight, unfilled),
  copied into the shared design system and bundled with the app. No icon library is added and
  no icon font is shipped: the full Material Symbols font is several megabytes, the classic
  Material Icons library has been deprecated since 2022, and per-glyph assets cost a few
  hundred bytes each and render identically on both platforms. The repository is Apache-2.0
  and goes into the licence inventory. Fonts are unchanged: Space Grotesk and Inter are
  already bundled from Google Fonts under the SIL Open Font License and no new weight is
  needed for the frame. Since 2026-10-17 the Tuner and Metronome glyphs are drawn for the
  project on the Material Symbols 24 dp grid in the Outlined regular weight (2 dp strokes, round
  joins, flat ends); being the project's own artwork they add no licence to the inventory.
- **The first destination is always the start.** The last-visited destination is not
  remembered across launches. The user described Home as the starting point; since Home was
  removed on 2026-10-17 the first destination in order, Timeline, is the start.
- **The scaffold's placeholder screen is split.** Home replaced it as the start screen (Timeline
  since 2026-10-17). The
  counter button and its tests are deleted; the design-token preview survives as a
  developer-only view in debug builds (FR-022), and token parity stays guarded by the existing
  automated test.
- **Dark theme only, English only, left-to-right**, as in the scaffold.
- **Branch.** The feature branch `002-adaptive-navigation-shell` was created from `main` by
  hand on 2026-09-24, before the spec-kit git extension was installed the same day; from the
  next feature on, the `before_specify` hook creates the branch.
