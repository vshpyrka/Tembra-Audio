# Contract: project storage, library index, import pipeline, overviews

**Where**: `:shared:data`, package `audio.tembra.data.projects` (Okio over `AppDirectories`,
`Dispatchers.IO`); the picker seam in `:shared:ui/platform`. Research R9–R11; spec FR-044 to
FR-054; data model §4.

## Folders

```
<documents>/projects/
├── library.json                         # LibraryIndex: one entry per project + openProjectId
└── <projectId>/
    ├── project.json                     # the document (contracts/project-document.md)
    └── audio/<sha256>.<wav|flac|mp3>    # byte-for-byte copies of the originals
<cache>/projects/<projectId>/
└── <sha256>.peaks                       # waveform overview (engine format); rebuildable
```

`AppDirectories.documents` is backed up by the platform; `cache` is not and may be purged — the
repository rebuilds a missing `.peaks` on open (the clip shows the placeholder meanwhile).

## `ProjectRepository` (interface in `commonMain`, `OkioProjectRepository`, `FakeProjectRepository`)

| Member | Behaviour |
|---|---|
| `library(): Flow<LibraryIndex>` | emits the index on subscription and after every change; reads `library.json`, or rebuilds it by scanning folders when missing, unreadable or when a folder's `project.json` is newer than the index entry |
| `create(name): Project` | new folder, document at defaults (120 BPM, 4/4, empty), index entry, returns the project |
| `open(id): OpenedProject` | reads and validates the document; returns the project plus `audioPath(assetId)` and `overviewPath(assetId)` resolvers and the set of assets whose file is missing |
| `save(project)` | stage `project.json.tmp` → `rename`; updates the index entry (name, description, key, tempo, length, track count, size, last edited, card overview from the overviews and track settings); deletes audio copies no clip references **only when told the history no longer holds them** (`save(project, retiredAssets)`) |
| `duplicate(id): Project` | copies the folder with new project/track/clip ids, name "<name> copy" (numbered when taken), same asset ids; copies the overviews too |
| `delete(id)` | removes both folders and the index entry; if it was `openProjectId`, clears it |
| `installDemo(): Project` | writes the demo document and its audio from the bundled resources (idempotent: returns the existing demo if present); `restoreDemo()` replaces it |
| `importAudio(projectId, file: PickedFile): ImportResult` | the pipeline below |
| `sizeOnDevice(id): Long` | sum of `project.json` and `audio/*` (not the cache) |
| `setOpen(id?)` | records `openProjectId` for FR-047 |

## Import pipeline (`AudioImporter`, IO dispatcher)

1. `PickedFile.open()` → Okio `Source` from the platform (SAF stream / security-scoped URL).
2. Copy to `audio/.staging-<uuid>` through a `HashingSource(SHA-256)`; cap at 30 minutes'
   worth of bytes is not knowable before decoding, so the copy completes first (a 500 MB guard
   refuses obviously oversized files early with `TooLarge`).
3. `TimelineAudio.inspect(stagingPath)` → `AudioInfo` or `Unreadable`; refuse `channels > 2`
   (`TooManyChannels`) or `frames / sampleRate > 1800` (`TooLong`); the format must be WAV, FLAC
   or MP3 (`UnsupportedFormat`; the picker's MIME filter is advisory only).
4. If `audio/<sha256>.<ext>` exists, delete the staging file and reuse the asset; otherwise
   rename into place.
5. `TimelineAudio.buildOverview(audioPath, overviewPath)` (the engine's offline builder); the
   repository exposes `overviewStatus(assetId): Flow<OverviewStatus>` (`Building(completedFraction)`,
   `Ready`, `Failed`) by polling the `.peaks` header every 500 ms while building.
6. Return `Accepted(asset, clipTemplate(name = fileName without extension, offset 0, length = frames))`.

A multiple pick runs steps 1–6 per file sequentially (one staging file at a time), returns the
list of results; the session turns the accepted ones into one `ImportClips` edit and the refusals
into one message.

## `LibraryIndex` (`library.json`)

```json
{ "version": 1, "openProjectId": "…", "entries": [ { "id": "…", "name": "Demo project", "description": "…",
  "key": "Dmin", "tempo": 124, "lengthSeconds": 30.96, "trackCount": 4, "byteSize": 11891616,
  "lastEdited": 1791331200000, "origin": "demo", "overview": { "spans": 48, "peaks": "base64…", "colors": "base64…" } } ] }
```

The index is a cache of the documents; `rebuild()` recreates it from the folders and is run when
it is missing, fails to parse, or any folder is newer. Sorting, filtering and searching are done
in memory by `LibraryQuery` (pure, `:shared:core`): `All`/`Recent` (edited within seven days of
`now`), `LastEdited desc` / `Name asc` (case-insensitive), query matches name or description
case-insensitively.

## `AudioFilePicker` (seam, `:shared:ui/platform`; replaces `SoundFilePicker`)

```kotlin
interface AudioFilePicker {
    suspend fun pickOne(request: AudioPickRequest): AudioPickResult      // Picked(listOf(file)) | Cancelled | Failed(reason)
    suspend fun pickMany(request: AudioPickRequest): AudioPickResult     // Picked(files) in picker order
}
data class AudioPickRequest(val mimeTypes: List<String>)                 // ["audio/wav","audio/x-wav","audio/flac","audio/mpeg"]; the metronome passes ["audio/wav","audio/x-wav"]
class PickedFile(val displayName: String, private val opener: suspend () -> Source)
```

Android: `ActivityResultContracts.OpenDocument` / `OpenMultipleDocuments` through the existing
host pattern (`SoundFilePickerHost` generalised to `AudioFilePickerHost`); iOS:
`UIDocumentPickerViewController(forOpeningContentTypes:)` with `allowsMultipleSelection`,
`startAccessingSecurityScopedResource` around the copy. The metronome's custom-sound import moves
to `pickOne` with the WAV filter; its behaviour is unchanged (its tests prove it).

## Autosave (`AutosaveScheduler`, in the session but specified here)

`onEdit()` schedules a save 500 ms later; a second edit inside the window moves it, but a save
is forced 2 s after the first unsaved edit; `flush()` saves immediately (leave Timeline,
background, host stop, project switch). Saves serialise on one IO worker; a save in flight plus a
new edit yields one more save after it.

## Tests (first)

- `OkioProjectRepositoryTest` (fake filesystem): create/open/save round trip; staging and rename;
  index rebuild when missing or stale; duplicate naming; delete removes both folders; demo install
  idempotent; `sizeOnDevice`; unused asset deletion only with `retiredAssets`.
- `AudioImporterTest`: every refusal and acceptance of the matrix with a scripted
  `TimelineAudio.inspect`; dedup by hash; multiple pick with a refusal in the middle; the staging
  file never survives a failure.
- `LibraryQueryTest`: filters, sorts, search, "M of N".
- `AutosaveSchedulerTest` (virtual time): 500 ms debounce, 2 s cap, flush, coalescing.
- Device/simulator: `ProjectRepositoryFileTest` on real `AppDirectories`; `AudioFilePickerTest`
  on iOS with an injected picker result; Android picker host test.
