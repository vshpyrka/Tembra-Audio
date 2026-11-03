# Contract: drag-and-drop import

Spec FR-026, User Story 5, SC-009; research R12. Skill contracts: `kotlin-api-design` (`DroppedFilesReader` interface
with Activity-owned Android and iOS bindings; planning in common code), `kotlin-control-flow` (exhaustive `when` over
`DropZone`, `DroppedItem` and `DropPlan`), `compose-state-and-effects` (hover state per zone, cleared on exit and end).

## Files (`app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/drop/`)

`DropZone.kt`, `DroppedItem.kt`, `DropPlan.kt`, `DropPlanner.kt`, `DropOutcome.kt`, `SkippedItem.kt`, `DropHover.kt`,
`DroppedFilesReader.kt`, `DropTargetModifier.kt` (`Modifier.audioDropTarget(zone, reader, onHover, onDrop)` over
`dragAndDropTarget`). Platform: `androidMain/.../AndroidDroppedFilesReader.kt`, `iosMain/.../IosDroppedFilesReader.kt`.

## Behaviour

1. `accepts`: Android true for `audio/*`, `application/x-arc-uri-list` (ChromeOS Files) and
   `vnd.android.document/directory`; iOS true when any item conforms to `public.audio` or `public.folder`.
2. While hovering, the zone resolves a preview `DropPlan` from the item count and types available before reading
   (`DropHover`) and shows it ("New track", "Add to <track> at bar N", "New project"); leaving or ending clears it.
3. On drop, the reader reads items: audio files become `PickedFile`s (Android: after
   `requestDragAndDropPermissions`, released when the import has copied them; iOS: copied into the cache inside the
   provider's completion handler); folders become `Folder(name, contents)` with direct children only, or
   `contents = null` when unreadable (Android always; iOS per the spike).
4. `DropPlanner.plan(zone, items, snappedBeats)`: flatten readable folders one level, collect audio files and skipped
   items; then the rules of data-model.md. `AddToTrack` and `NewTracks` call `ProjectSession.importAudio(files,
   ImportTarget.Track(trackId, atBeats) / ImportTarget.NewTracks(atBeats))`; `NewProject` creates a project, opens it
   and imports as new tracks.
5. One message lists every skipped name with its reason, merged with the importer's refusals
   (`TimelineCopy.filesRefused`); `Nothing` with no audio shows "No audio files were found".

## Tests first

- `DropPlannerTest` (commonTest): one file on a lane → `AddToTrack` at the snapped beat; two files on a lane →
  `NewTracks`; files on space → `NewTracks`; Projects → `NewProject`; a readable folder with 3 WAVs, a text file and a
  sub-folder → 3 files, 2 skipped; an unreadable folder → skipped as `UnreadableFolder`; only non-audio → `Nothing`.
- `AudioDropTargetTest` (uiTest, fake reader and fake `DragAndDropEvent`): hover shows the preview text for each zone,
  exit clears it, drop calls the session with the planned target.
- `AndroidDroppedFilesReaderTest` (androidApp androidTest): `content://` URIs from a test provider are read under
  granted permissions and released; a directory URI yields `Folder(contents = null)`.
- `IosDroppedFilesReaderTest` (iosTest): item providers for an audio file and a folder (spike outcome) map as specified.
- Manual (quickstart §5): drags from ChromeOS-like Files on the desktop emulator, the tablet's file manager and the
  iPad's Files app.
