# Data Model: Large Screens and Desktop (feature 011)

Nothing in the project document or the storage format changes: this feature adds no musical data. The types below are
UI and platform-seam types in `:shared:ui` (and one enum entry in its platform package). One type per file
(Principle VII); `internal` unless a platform shell needs it.

## Keyboard shortcuts (`ui.shortcuts`)

| Type | Shape | Rules |
|---|---|---|
| `ShortcutScope` | enum `Everywhere`, `Timeline`, `Tuner`, `Metronome`, `Projects` | `Everywhere` chords work on every destination; a destination scope works only while that destination is shown. A chord is unique within `Everywhere` plus any one destination. |
| `ChordKey` | sealed: `Character(char: Char)` (lower-case), `Named(key: Key)` | Letters and symbols match by the typed character (`utf16CodePoint`), named keys (Space, Escape, arrows, Home, Delete, number-row digits) by `Key` (research R6). |
| `KeyChord` | data: `key: ChordKey`, `primary: Boolean` (Ctrl on Android/ChromeOS, Command on iPad), `shift: Boolean` | `matches(event)` is the single matching rule; no chord uses Alt or Meta (reserved by the systems). |
| `ShortcutAction` | enum, one entry per action below | The action id is what the iPad menu commands and the Android system list call back with. |
| `Shortcut` | data: `chord`, `scope`, `action`, `label: String` (English copy, from `ShortcutCopy`) | Listed in the sheet in catalogue order. |
| `ShortcutCatalogue` | object with `all: ImmutableList<Shortcut>` | The single source (contracts/keyboard-shortcuts.md has the full table). A test fails on a duplicate chord, a missing label, a reserved chord or an action no handler serves. |

The catalogue's entries (chords are written with Ctrl; Command on iPad):

| Scope | Chord | Action |
|---|---|---|
| Everywhere | Ctrl+1 … Ctrl+5 | open Timeline, Tuner, Metronome, Projects, Profile |
| Everywhere | Ctrl+/ | open the shortcut sheet |
| Everywhere | Escape | close the topmost sheet, dialog or menu |
| Timeline | Space, Home, ←/→, Shift+←/→, S, Delete/Backspace, Ctrl+Z, Ctrl+Shift+Z (and Ctrl+Y), + / −, M, O | feature 009's keys, unchanged (`timeline/TimelineKeyHandling.kt`) |
| Metronome | Space; ↑/↓; Shift+↑/↓; T | start/stop; tempo ±1; tempo ±10; tap tempo |
| Tuner | A; E; 1–7 (up to the string count); Space; L | Auto Listen; By Ear; play string N's tone (1 = string I, the highest); stop the tone; toggle Loop Tone |
| Projects | Ctrl+F; Ctrl+N | focus search; new project |

## Drops (`ui.drop`)

| Type | Shape | Rules |
|---|---|---|
| `DropZone` | sealed: `TrackLane(trackId)`, `TimelineSpace`, `ProjectsGrid` | Where the pointer is when files hover or land. |
| `DroppedItem` | sealed: `AudioFile(file: PickedFile)`, `Folder(name, contents: List<DroppedItem>?)` (null contents = the platform does not let the app read it), `Other(name)` | Produced by a platform `DroppedFilesReader`; a folder's `contents` are its direct children only (no recursion). |
| `DropPlan` | sealed: `AddToTrack(trackId, atBeats: Beats, file)`, `NewTracks(files, atBeats)`, `NewProject(files)`, `Nothing` | Resolved by `DropPlanner.plan(zone, items, snappedBeats)`: exactly one audio file on a lane → `AddToTrack`; otherwise lanes and space → `NewTracks`; Projects → `NewProject`; no audio file at all → `Nothing`. |
| `DropOutcome` | data: `plan`, `skipped: List<SkippedItem>` | `SkippedItem(name, reason)` with reasons `NotAudio`, `UnreadableFolder`, plus the importer's own refusals added after import; all skipped names go into one message (`TimelineCopy.filesRefused` extended). |
| `DropHover` | data: `zone`, `plan: DropPlan` | The hover preview names what the drop will do ("New track", "Add to Drums at bar 5", "New project"); `Nothing` shows no accept state. |

`DroppedFilesReader` (interface, platform-bound): `suspend fun read(event: DragAndDropEvent): List<DroppedItem>` and
`fun accepts(event: DragAndDropEvent): Boolean` (MIME types only; contents are read on drop).

`OpenedFiles` (interface, platform-bound: `AndroidOpenedFiles`, `IosOpenedFiles`): `val files: Flow<List<DroppedItem>>`,
each emission one "open with Tembra Audio" request from the Files app (research R22). The shell plans it as
`DropZone.ProjectsGrid`, so it becomes `NewProject`; while a project plays, the existing stop-and-leave confirmation
comes first and cancel drops the request.

## Pointer (`ui.designsystem`)

| Type | Shape | Rules |
|---|---|---|
| `PointerShapes` | `expect object` with `grab`, `grabbing: PointerIcon` | Android `TYPE_GRAB`/`TYPE_GRABBING`; iOS `PointerIcon.Default` (research R7). |
| `onSecondaryClick` | `Modifier.onSecondaryClick(key: Any?, onSecondaryClick: (Offset) -> Unit)` | Consumes the secondary press in the `Initial` pass; the offset is local to the modified element. |
| `scrollNotches` | `expect fun scrollNotches(delta: Offset): Offset` | Platform deltas to wheel notches (research R9). |
| `TrackpadPinch` | data: `scale: Float`, `focus: Offset` | iOS only: emitted by the shell bridge into a `TrackpadPinchSource` (interface, `Flow<TrackpadPinch>`) the timeline viewport collects; Android gets pinches as Compose `Scale` events. |
| `PointerShapes.busy` | `PointerIcon` | Android `TYPE_WAIT`, iOS `PointerIcon.Default` (research R19); shown at the shell root while `ProjectSession` reports work in progress (import, project open). |
| `TembraScrollbar` | composable: `modifier`, `adapter: ScrollbarAdapter` (from `LazyListState`, `ScrollState` or the timeline viewport), `orientation` | Visible while the content scrolls from a pointer, fades after one second at rest, draggable with the mouse; touch scrolling does not show it (research R18). |

## Microphone (`ui.platform`)

`MicrophonePermissionStatus` gains `Unavailable` (no input device). `isDenied` stays false for it; the tuner's
`ListeningPolicy` treats it as "cannot listen", and the Auto Listen pane shows the no-microphone message instead of the
permission card.

## Window controls (`ui.platform`)

`WindowControls` (interface, platform-bound): `fullScreenAvailable: StateFlow<Boolean>` (Android: API 34+ and the
activity is in a multi-window or free-form mode; iOS: always false), `isFullScreen: StateFlow<Boolean>`,
`enterFullScreen()`, `exitFullScreen()` (research R21). The Timeline's overflow menu shows "Full screen" or "Exit full
screen" while available.

`ForegroundState` follows `ON_START`/`ON_STOP` on Android and `willEnterForeground`/`didEnterBackground` on iOS, not
pause or resign-active, so a window that loses focus to another window stays in the foreground.

## Window (manifest and shell values, not Kotlin types)

| Value | Android / ChromeOS | iPad |
|---|---|---|
| Minimum | `<layout android:minWidth="360dp" android:minHeight="480dp"/>` | `.frame(minWidth: 360, minHeight: 480)` + `sizeRestrictions.minimumSize` (iPadOS 26+) |
| First size | system default (75% of the display on Android desktop); ChromeOS `FreeformWindowSize=tablet` | system's choice (no API) |
| Later sizes | system-remembered (no `<layout>` default) | system-remembered |
| Instances | one task, `launchMode="singleTask"` (launcher and Files-app intents reuse it) | `UIApplicationSupportsMultipleScenes` false |
| Full screen | `requestFullscreenMode` from the Timeline's menu (API 34+, desktop and multi-window only) | system's (no app item) |
| Open with | `VIEW` intent filter: `audio/wav`, `audio/x-wav`, `audio/flac`, `audio/mpeg` on `content` | `CFBundleDocumentTypes`, rank Alternate, `.onOpenURL` |

## Shell state (`ui.navigation`)

`NavigationShell` gains `shortcutSheetOpen` (`rememberSaveable`), a `menuAnchor: Offset?` per menu owner for menus opened
at the pointer, and the focus-restore target after Escape (the originating control's requester, else the destination
root).

## Performance names (`:shared:engine` catalogue)

| Enum | Family | Signpost |
|---|---|---|
| `SHELL_WINDOW_RELAYOUT` | `SHELL` (new) | `shell.windowRelayout` |
| `SHELL_SHORTCUT_NAVIGATE` | `SHELL` | `shell.shortcutNavigate` |
| `TIMELINE_CONTEXT_MENU` | `TIMELINE` | `timeline.contextMenu` |

Anchors added to `PerfAnchors`: `METRONOME_START`, `TIMELINE_FIRST_CLIP` (if not present), `PROJECTS_SEARCH`,
`SHORTCUT_SHEET`.
