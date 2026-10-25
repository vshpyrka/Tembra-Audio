# Feature 009 — Timeline Arranger: evidence for the pull request (T136)

Branch `009-timeline-arranger`, 2026-10-19, final pass 2026-10-25. What the reviewer can open:

| Evidence | Where |
|---|---|
| Screenshot matrix (spec FR-062) on 10 targets, one row per file, plus the gates, the device checks and what is left to the owner | `../screenshots/NOTES.md` (117 PNGs beside it) |
| Baseline profile regenerated with the Projects destination, verified on the perf build | `profile.md`, `app/androidApp/src/main/generated/baselineProfiles/` |
| Compose compiler report: 59 feature composables, all restartable and skippable | `compose-report.md`, `compose-report/` |
| Trial perf rounds (`timeline` journey, the extended `stress` journey, the three audio counters) on the Pixel 6 Pro and the iPhone 14 | `trial-round.md` |
| Test lanes, final pass with `--warning-mode all`, on the Pixel 6 Pro and every other attached Android target, the iPhone 18 Pro and iPad simulators | `../screenshots/NOTES.md` § Gates and § Final pass |
| Device-test window: the Compose host and androidx.test's bootstrap activities declared black (the owner watches the matrix run), the tuner meter's needle probes made honest by it | `app/shared/ui/src/androidDeviceTest/AndroidManifest.xml`, `res/values/themes.xml`; `TunerMeterTest`; `device-test-window-before.png` (one capture per focused window during the 20:16 lane: androidx.test's `EmptyActivity` white) and `device-test-window.png` (the same windows during the 20:35 lane: black) |

## The principle each non-obvious decision serves (plan Complexity #1–#3, research R5, R8, R9, R13)

- **One `ProjectSession` singleton owns the open document, its history and the engine binding** (Complexity #1,
  Principle II "nothing Kotlin on the audio callback"): every edit becomes a command the C++ engine applies on its own
  thread; the session is the one coroutine owner (`@Named("session")` scope, named launches, no `init` launches).
- **A JSON `library.json` index instead of Room** (Complexity #2, research R13): the library is a list of a few
  hundred entries read once; the index rebuilds itself from the project folders when it is missing or stale, which is
  also what makes a copied project folder appear (SC-009).
- **Originals decoded on a reader thread, no PCM cache** (research R5, Principle I one engine): one `DiskStreamer`
  thread per engine, now at audio priority, refills one SPSC fifo per clip; a dry fifo renders silence and counts a
  `starvation`, reset per transport session.
- **Everything musical in beats** (Principle III, research R8): clip starts, splits, the metronome anchor and the
  view state carry beats; frames come from the tempo map at render time, so a tempo change re-derives clip frames and
  re-anchors the click without touching the document's positions.
- **The playhead and the readout read per-frame telemetry in a draw lambda** (research R9, R7, Principle VI): no
  `Text` recomposes per frame; the accessible playhead is a narrow node so the lanes stay reachable.
- **The transport bar keeps all seven controls at phone widths** (spec FR-004 names them left to right): below 520 dp
  the readout stacks and fits its budget, measured by `TimeReadoutTest` and the 360/411 dp bar tests.
- **The Timeline hands the audio device over when another destination shows** (spec FR-008a, constitution v1.3.2: the
  engine's states are exclusive, the tuner and the metronome need an idle engine): `ShellViewModel` tells the session
  which destination hosts the transport, the session closes the transport synchronously before the Tuner or the
  Metronome appears and reopens it on return at the stored position, retrying a busy engine for up to five seconds; a
  leave that lands during the reopen closes it again, play after a failed reattach binds the transport in full, a seek
  or a click-setting change while detached moves only the stored state. One mutex serialises the opens and closes (a
  second open while one is in flight is refused by the engine and drops the audio focus), the start job runs once and
  re-checks the state after each suspension, a pause from `Starting` keeps the stored position. Eleven session tests
  and one shell test, driven through a fake engine that can hold an open or answer busy.

## Skill contracts per public composable and coroutine owner

- Coroutine owners: `ProjectSession` (session scope: engine events, preferences, host stop requests, seeks, autosave),
  `TimelineViewModel` / `TrackSettingsViewModel` / `ProjectsViewModel` (launch sites named, 10 Hz polls stop when the
  screen is hidden, `uiState` derived with `stateIn(Eagerly)`), `AppStartup` and the debug `ProjectsSeeder` (one
  launch each on the session scope; the seeder catches its I/O failure). Messages are buffered `Channel`s exposed as
  `receiveAsFlow()`; state is `StateFlow`.
- Public composables are stateless (`state`, `callbacks`, `telemetry`, `layout`, `modifier`): `TimelineScreenContent`,
  `TrackSettingsScreenContent`, `ProjectsScreenContent`, `TransportBar`, `TimeReadout`, `PlayheadOverlay`, `TrackLane`,
  `TimelineRuler`, the dialogs and sheets; every one carries its `testTag` (now exposed inside dialog, menu and sheet
  windows too) and reads for screen readers per spec FR-058.
- Focus and keys: one `Modifier.onPreviewKeyEvent` owner on the screen root (`timelineKeyHandling`), the root takes
  focus once the project is in, Tab walks toolbar → headers → clips → transport (asserted by one recorded pass).

## After the merge

Run the labelled sessions (`uv run tracepulse run --device pixel6 --label 009-timeline --rounds 5` and the same on
`iphone14`), `tracepulse sync` and `report`, and append the session to `app/perf/baselines.md` as feature 008 did.
Expect the `stress` journey's step at the label boundary (its transport phase: about twenty times the audio callbacks
of the feature 008 journey, each rendering four clips) and the app-size step from the bundled demo loops; both are
explained in `trial-round.md` § Gates. On the iPhone 14 the runner's preflight refuses a journey once the phone's
thermal state leaves "nominal", which it did after forty minutes of journeys on the charging cable: run the five
journeys as two sessions, or let the phone cool between rounds.
