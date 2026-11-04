# Contract: take storage — staging, manifest, adoption, crash recovery, the live overview

**Where**: `:shared:data`, package `audio.tembra.data.projects`; data model §5; research R6, R7,
R14; spec FR-011, FR-023 to FR-025. Skill contracts: `kotlin-concurrency-and-flow` (every
repository write under the existing mutex on `Dispatchers.IO`; the adoption is one suspend
function called from the controller's `finaliseTake` launch), `kotlin-api-design`
(`TakeManifest` parsed by its own companion; `TakeAdopter` owns the adoption, the repository
only lists and deletes).

## Files

| Path (inside the project folder) | Writer | Lifetime |
|---|---|---|
| `audio/.take-<uuid>.wav` | the engine's recorder (24-bit PCM, the input's channels, the stream rate; header sizes refreshed every second) | from `recordStart` until adoption renames it to `audio/<sha256>.wav` (or deletes it: cancelled, too short, discarded by the user after a crash) |
| `audio/.take-<uuid>.json` | the controller, before `recordStart` | deleted after adoption or discard |
| `audio/<sha256>.wav`, `<cache>/<sha256>.peaks` | the adopter | as any audio copy (009 FR-048) |

Dot-files are already skipped by `listAudioFiles`, so a staging take never counts toward the
project's size and is never retired by `save(project, retiredAssets)`.

## `TakeManifest` (`@Serializable`, version 1)

`token`, `trackId`, `startBeats` (the Record position, or the pre-roll's `start`), `takeName`
(chosen at record start by `Track.nextTakeName()`), `startedAt` (epoch ms), `alignmentFrames`,
`sampleRate`, `channels`, `formatVersion`. Unknown fields are ignored on read.

## `TakeAdopter.adopt(folder, manifest, takeInfo): AdoptedTake`

1. `takeInfo.frames == 0` → delete both files, return `Discarded`.
2. Hash the WAV through `HashingSource.sha256` in 64 KiB chunks (as `AudioImporter.copyAndHash`).
3. If `audio/<sha256>.wav` exists (an identical take, practically never): delete the staging
   file; else rename.
4. `audio.inspect()` must agree with `takeInfo` (channels, rate, frames ± the last partial
   block); a disagreement is `AdoptFailed(reason)` and the staging files stay for the user's
   next launch.
5. `audio.buildOverview` inside `PerfTrace.section(TIMELINE_WAVEFORM)`.
6. Delete the manifest; return `Adopted(assetId, AudioAsset(fileName, Wav, channels, rate,
   frames, byteSize), ClipTemplate(startBeats = manifest.startBeats, offset 0, length = frames,
   name = manifest.takeName), audioPath, overviewPath)`.

The controller turns `Adopted` into `RecordTake` and hands it to the session; the session
opens the clip source, applies the edit (one history step) and pushes the arrangement.

## Crash recovery (FR-023)

`ProjectRepository.pendingTakes(id)` lists `.take-*.json` manifests whose WAV exists and whose
header `tembra_audio_inspect` can read (the recorder refreshed the sizes at most one second
before the end). On `ProjectSession.open`, after `install`, every pending take is adopted with a
synthetic `TakeInfo` from the inspection (frames from the header, `alignmentFrames` from the
manifest, `stopReason = WriteFailed`) and applied as a settled edit with no undo step (the
history is empty after a relaunch anyway), then `persist`. A manifest whose WAV is unreadable or
shorter than 0.01 s is discarded with its file. A project opened on a device that lacks the
engine (never: both platforms link it) would leave the files untouched.

## Free space (FR-025)

`TakeFreeSpace.freeBytes(folder.audioDir)` through `TimelineAudio.freeBytes` before every
`recordStart`: below 200 MB the controller emits `RecordRefused` and does not start. The
engine ends a take itself below 50 MB (`min_free_bytes` in the record config).

## `LiveOverview` (R7)

Holds the base buckets appended from `PeakBuckets` (int8 min/max per channel), derives the
1024, 4096 and 16384 levels incrementally (each coarser bucket is the min/max of the four finer
ones; a partial coarse bucket is recomputed on each append) and returns the `WaveformOverview`
shape `ClipPainter.drawClip` draws (`levelFor(framesPerPixel)`, `completedFraction = 1`). A gap
reported by `readPeaks` (the ring was overrun) repeats the previous bucket. It is replaced in
`RecordingUi.liveTake` at 10 Hz; when the take is adopted the clip reads its `.peaks` through
the existing `OverviewTracker`.

## `TimelinePreferences` (extended)

`monitorInput: Flow<Boolean>` on the boolean key `timeline.monitorInput`, default false;
`setMonitorInput(Boolean)`; `FakeTimelinePreferences` mirrors it.

## Tests (first, `commonTest` with `okio-fakefilesystem`)

- `TakeManifestTest`: round trip, unknown field ignored.
- `TakeAdopterTest` with `FakeAudioEngine.takeWriter`: adoption renames and hashes, a duplicate
  hash reuses the copy, inspect disagreement leaves the staging files, overview built inside the
  section, discard on zero frames, the clip template's name and start.
- `OkioProjectRepositoryTest` (extended): dot-files invisible to size and retire (already),
  `pendingTakes` lists only readable takes, `discardTake`, the recovery path adopts on open.
- `LiveOverviewTest`: level derivation equals `WaveformOverviewReader`'s levels for the same
  buckets, gap repetition, `levelFor` at every zoom, a 30-minute take's memory bound.
- `DataStoreTimelinePreferencesTest` (extended): the monitor key, default false.
