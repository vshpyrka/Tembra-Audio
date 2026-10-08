# Feature Specification: Performance Pipeline

**Feature Branch**: `007-performance-pipeline`

**Created**: 2026-10-08

**Status**: Draft

**Input**: User description: "Performance pipeline: profiling and regression measurement for Tembra Audio on Android and iOS. Why: the app will soon carry a real-time audio engine, a 60–120 Hz telemetry-driven UI and a timeline. We need to see performance regressions (startup, UI frames, memory, binary size, trace sections, audio-thread health) as a trend over rounds on real devices, with a coarse gate, before the audio spine lands. The research and decisions are in docs/research/performance-profiling.md (sections 6–7, 9.1, 10–12); treat it as the source for technology choices in the plan, not as something to re-research. What a maintainer can do when this is done: (1) run an Android performance round from the repository with one command against a USB-connected Pixel, from CI, over SSH or by hand, with the existing device protocol; (2) run an iOS performance round with one command on the Apple Silicon Mac against the USB-connected iPhone 14 (manual runs only), same journeys and metric families where the platform allows, results in the same history; (3) read one self-contained HTML report for both platforms with regression onsets marked by the existing detector, per-platform device series kept separate, and a coarse gate; (4) instrument the app once and see the same named sections on both platforms, never on the audio callback, compiled out in release unless a perf build flag is on; (5) engine audio-thread health measurable from day one of the audio spine; (6) a perf build variant on both platforms plus the small app changes the pipeline needs; (7) a scheduled and on-demand workflow running the Android rounds on a self-hosted runner (the 2015 MacBook Pro, Android only). Constraints: reuse the existing trace-pulse pipeline by copying it into the repository and extending it for iOS and the gate; native journey drivers on each platform; no Maestro, no device farm, no field telemetry; metric mapping per report section 6.3; constitution rules unchanged; spikes from report section 10 before the plan. Out of scope: cloud device farms, Maestro, an iOS CI runner, remote-iPhone networking, field telemetry, baseline-profile generation."

## Clarifications

### Session 2026-10-08

- Q: How do rounds produced on different machines come together into one history and one report, and what is the history keyed by? → A: History is keyed by the device the tests ran on (device identity and model), never by the driving machine, which is recorded as round metadata only; each round becomes a small portable record (values, metadata, detection and gate inputs, without raw traces) synchronised through a dedicated history branch in the repository, from which any machine rebuilds the full per-device history and renders the combined report; raw traces stay on the producing machine.
- Q: Which events may start an unattended Android workflow on the owner's own Mac? → A: None in this feature: no self-hosted runner and no continuous-integration workflow are created; the feature delivers scripts that run the rounds by hand (locally or over SSH) on both platforms, structured so a CI job can call them unchanged once the owner decides how to run them on CI. The intended cadence is a labelled session of rounds per device after each feature is merged, so that any regression is attributed to the feature that introduced it.
- Q: Which metric families should the coarse gate cover by default, and what happens when a limit is exceeded? → A: Every family has a gate with a configurable limit; exceeding a limit never fails the command or the round, it flags the round and metric in the report (and in the command output) as above the limit.
- Q: What network state should the iPhone be in during a round, and should the round check it? → A: Network off where the pipeline can arrange or verify it (Android keeps its enforced airplane-mode check); on iOS, where the host cannot set it, the round runs regardless, records the observed network state as round metadata and shows it in the report; it never refuses for network state.

## User Scenarios & Testing *(mandatory)*

The user of this feature is a Tembra Audio maintainer. A **round** is one complete measurement of one build on one real device: the device is checked, the measurement build is installed fresh, warmed up once without measuring, each benchmark journey is run a fixed number of times, every measured value is kept per iteration together with its raw trace, and the result is appended to the **history** of that device's series. **Detection** looks across the history for step changes and confirms them statistically; the **report** shows the series, the onsets and the verdicts; the **gate** is a coarse tripwire that fails the run when a metric jumps past a configured limit.

### User Story 1 - Run an Android performance round with one command (Priority: P1)

A maintainer connects a Pixel over USB, runs one command from the repository, and gets a complete round appended to that device's history: device checks, measurement build, fresh install, warm-up launch, the benchmark journeys (cold startup, tuner, metronome, and a "stress" placeholder that becomes meaningful once the audio spine exists), per-iteration values and traces, detection and a regenerated report. The same command works when launched by hand on any machine with the device attached (including the second Mac) or over SSH, and is written so that a future CI job can call it unchanged.

**Why this priority**: it is the foundation every other story feeds into, and it is the lowest-risk slice because the Android pipeline already exists and has been validated on these devices; adopting it into the repository with Tembra's own journeys is the first useful deliverable.

**Independent Test**: on a fresh checkout with a Pixel attached, run the round command; a new round appears in the history with values for every journey, a report file is produced, and running the command a second time produces a second round on the same series. Delivers a reproducible measurement of the current build.

**Acceptance Scenarios**:

1. **Given** a Pixel attached, charged above the minimum, cool, in the required display and network state, **When** the maintainer runs the round command for that device, **Then** the command installs the measurement build fresh, performs one unmeasured warm-up launch, runs every configured journey the configured number of times, stores per-iteration values and one raw trace per iteration under the round, appends the round to the history, runs detection, regenerates the report, and exits successfully.
2. **Given** a Pixel that fails a device check (for example too warm or below the battery minimum), **When** the maintainer runs the round command, **Then** the command refuses before building or measuring, names the failed check and the threshold, and exits with a failure status without touching the history.
3. **Given** two Android devices attached, **When** the maintainer runs the round command without naming a device, **Then** the command refuses and asks for an explicit device identifier rather than guessing.
4. **Given** a journey that cannot complete (for example a UI anchor it expects is missing), **When** the round runs, **Then** the round is recorded as failed with the reason, no partial values enter the series, and the raw artifacts collected so far are kept for inspection.
5. **Given** the command is launched over SSH or from a script, **When** it runs, **Then** it never waits for interactive input and its exit status alone tells the caller whether the round succeeded, was refused, or failed.
6. **Given** no device is attached, **When** the maintainer runs the round command, **Then** it fails fast with a clear message and does not wait for a device.

---

### User Story 2 - Run an iOS performance round with one command (Priority: P2)

A maintainer connects the iPhone 14 to the Apple Silicon Mac, runs one command from the repository, and gets an iOS round appended to that device's history in the same history store as the Android rounds, measured with the platform's standard performance-test facilities. The journeys are the same ones as on Android; the metric families are the same where the platform offers an equivalent and are recorded as platform-specific families where it does not. Device checks that the host cannot read (thermal state, battery level) are made inside the measurement run and refuse the round the same way. iOS rounds are run by hand; no unattended iOS runner is part of this feature.

**Why this priority**: it is the half of the pipeline that does not exist yet and the reason the feature exists; it depends on the history, detection and report of Story 1.

**Independent Test**: with the iPhone attached, run the iOS round command; a round appears in the iPhone series with per-iteration values for every journey, the report shows the iPhone series next to the Pixel series, and a second run appends a second round. Delivers the first iOS measurement history for the app.

**Acceptance Scenarios**:

1. **Given** the iPhone attached, paired, in developer mode and unlocked, **When** the maintainer runs the iOS round command, **Then** the command builds the measurement configuration, installs it fresh, performs one unmeasured warm-up launch, runs every configured journey the configured number of times, reads per-iteration values from the platform's result bundle, stores the bundle as the round's raw artifact, appends the round to the iPhone series, runs detection, regenerates the report, and exits successfully.
2. **Given** the iPhone reports a thermal state other than nominal, or a battery level below the minimum, at the start of a journey, **When** the round runs, **Then** the round is refused with the failed check named and nothing enters the history.
2a. **Given** the iPhone has a network path available (airplane mode off, or Wi-Fi on), **When** the round runs, **Then** it proceeds, records that state on the round, and the report shows it; the maintainer is advised to turn the network off before a session but is never refused for leaving it on.
3. **Given** a metric family that exists on Android but has no iOS equivalent (per-frame duration percentiles), **When** the iOS round completes, **Then** the iOS series carries the platform's own frame-health family (hitch time ratio, hitch count, frame rate) under distinct names, and nothing claims the two families are comparable.
4. **Given** a journey wrapped in a named section on both platforms, **When** an iOS round completes, **Then** the section's duration appears as a series with the same name as on Android.

---

### User Story 3 - Read one report for both platforms, with a coarse gate (Priority: P3)

A maintainer opens a single self-contained report file and sees every device series (Pixel 6 Pro, Pixel 7 Pro, iPhone 14) with its history, regression onsets marked by the existing detector with their status (preliminary, suspect, confirmed, improved, environment-suspect), per-round quality badges, and links to the raw artifacts. The maintainer also configures a coarse gate per metric; when a round's median jumps past the configured limit relative to the recent clean rounds of the same series, the report and the command output flag that round and metric as above the limit. The flag is a notice, never a failure.

**Why this priority**: trend detection is the primary signal and already exists for one platform; extending the report to several platforms and adding the tripwire notice is what makes the pipeline readable at a glance without a human reading every chart.

**Independent Test**: with at least two series in the history, open the report; both appear with onsets. Configure a gate limit, inject a known slowdown into a journey, run a round; the report and the command output flag the metric as above the limit while the command still succeeds. Delivers the "did anything jump in this build?" answer in one file.

**Acceptance Scenarios**:

1. **Given** rounds exist for two or more device series, **When** the report is regenerated, **Then** one file shows each series separately with its onset markers, verdict statuses, quality badges and artifact links, opens offline, and never overlays series from different platforms on one axis for families marked non-comparable.
2. **Given** fewer rounds than the detector's minimum history on a series, **When** the report is regenerated, **Then** verdicts for that series are labelled preliminary and the report says how many more rounds are needed.
3. **Given** a gate limit configured for a metric and at least the configured number of clean rounds on the series, **When** a round's median for that metric exceeds the limit relative to the median of those clean rounds, **Then** the round succeeds, the command output names the metric, the limit, the observed change and the rounds it was compared against, and the round is recorded with the gate decision attached so the report flags that round and metric as above the limit; a clean round is one that succeeded, carries no noisy or thermal badge and is not part of an environment-suspect group (a gate flag does not make a round unclean).
4. **Given** fewer clean rounds than the gate needs, **When** a round runs, **Then** the gate is reported as inactive with the reason and the round proceeds on the trend path alone.
5. **Given** a metric that improved past the limit (lower is better and it dropped), **When** a round runs, **Then** the gate does not flag it; improvements are reported, never alarmed.
6. **Given** one or more rounds flagged by the gate, **When** the report is opened, **Then** a summary near the top lists every flagged round and metric with its observed change and limit, in addition to the mark on the series itself.

---

### User Story 4 - Instrument once, measure the same named section on both platforms (Priority: P4)

A maintainer wraps a piece of shared app code, or a piece of engine code, in a named section from a fixed catalogue (startup, tuner, metronome, timeline, engine families) and that section becomes measurable in rounds on both platforms without further work. Counters can be published the same way, and an "animation" variant marks scroll or zoom journeys so the platform can attribute frame health to them. Instrumentation is present only in measurement builds, costs nothing in release builds, and is never used from the audio callback.

**Why this priority**: without named sections the pipeline can only measure what the platforms expose by default; sections are what tie a regression to a feature. It is independent of the pipeline stories once the measurement build exists.

**Independent Test**: add a section around an existing operation, run one round per platform; a series with that section's name appears in both histories. Build the release configuration; the instrumentation is absent from it. Delivers feature-level attribution.

**Acceptance Scenarios**:

1. **Given** a section name from the catalogue wrapped around shared code, **When** a round runs on either platform, **Then** that section's duration (and count) appears as a series under the same name on that platform.
2. **Given** a counter published from the engine's non-real-time path, **When** an Android round runs, **Then** the counter's values are recorded in the round's trace and extracted as a series; **When** an iOS round runs, **Then** the counter's end-of-journey value is read into the series.
3. **Given** a journey marked with the animation variant, **When** an iOS round runs, **Then** the frame-health family for that journey is attributed to that section.
4. **Given** a release build, **When** it is inspected, **Then** no instrumentation code or section names are present in it.
5. **Given** a section name not in the catalogue, **When** the code is built, **Then** the build fails or the test suite fails; the catalogue is the only source of names.

---

### User Story 5 - Audio-thread health is measurable from the first day of the audio spine (Priority: P5)

A maintainer can see, for each round, the engine's audio-thread health: the last and maximum callback duration, the number of callbacks that exceeded their time budget, the number of underruns or overruns, and whether the audio thread holds real-time status. These values travel from the engine through its existing telemetry path, are surfaced during the "stress" journey, and become series on both platforms. This feature builds the counters and their plumbing; the audio spine fills them with real values.

**Why this priority**: the audio thread is the one place where a regression is audible rather than visible, and no platform tool reports it for an app; the counters must exist before the spine so the spine's first round already has them. It depends on Stories 1, 2 and 4.

**Independent Test**: with the fake engine scripted to report known counter values, run the stress journey on each platform; the audio series carry exactly those values. Delivers the measurement seam the spine will fill.

**Acceptance Scenarios**:

1. **Given** the engine reports callback duration (last and maximum), over-budget count, underrun/overrun count and real-time status through its telemetry, **When** a round runs the stress journey, **Then** each value becomes a series on that platform's history.
2. **Given** the audio callback is running, **When** the counters are updated, **Then** no allocation, lock, logging or blocking occurs on the callback; the counters are updated with atomic writes and read by a non-real-time path.
3. **Given** the engine is the fake used by the shared-code tests, **When** the telemetry plumbing is exercised, **Then** scripted counter values reach the point where the pipeline reads them, proving the path without the real engine.

---

### User Story 6 - Measure every feature as it lands (Priority: P6)

After a feature is merged, a maintainer runs a labelled session of rounds per device (the label names the feature, the session runs several consecutive rounds) by hand, publishes the round records, and reads in the report whether that feature moved any series. Rounds are expected after every feature from now on; how they are triggered on CI is decided later and is not part of this feature.

**Why this priority**: measuring each feature as it lands is the purpose of the pipeline; it comes last because it only composes Stories 1 to 3 with a label and a repeat count.

**Independent Test**: after merging a feature, run the session command on a Pixel and on the iPhone with the feature's label; the report shows the new rounds under that label on each device series, with a verdict for every metric that changed. Delivers attribution of a regression to the feature that introduced it.

**Acceptance Scenarios**:

1. **Given** a merged feature and a device attached, **When** the maintainer runs a session with that feature's label and a repeat count, **Then** the command runs that many consecutive rounds on the device, each a full round, stops at the first refusal or failure, publishes the records of the completed rounds, and regenerates the report.
2. **Given** rounds labelled by feature, **When** the report is read, **Then** each device series shows the feature boundaries, and an onset that falls at a boundary names the feature it coincides with.
3. **Given** a session in which some rounds exceed a gate limit, **When** it completes, **Then** the session still succeeds, its output lists the flagged rounds and metrics, and the report shows the flags on those rounds.

---

### Edge Cases

- The device disconnects mid-round: the round is marked failed, partial values are not stored in the series, collected artifacts are kept, and the command exits with a failure status.
- The phone is locked or asleep at round start: the round is refused with that reason rather than timing out silently.
- The iPhone's network state changes between rounds of one session: each round records its own state; the report shows the change on the series, and the detector treats it like any other fingerprint change (visible, not alarmed).
- The device's operating system was updated since the last round: the round is recorded with the new fingerprint, and the report marks the version change on that series so a step there is not mistaken for a code regression.
- The measurement build is identical to the previous round's build: the round still runs and is recorded; identical builds are how noise is measured.
- A journey needs the audio spine (the stress journey before the spine exists): it runs as a placeholder that exercises the existing tuner and metronome together, and its audio series carry whatever the engine reports (zeros with the current engine).
- A metric exists on one platform only: the report shows it on that platform and lists it as unavailable on the other; it never appears as a missing data point on the other platform's series.
- History and detection parameters change (for example a new gate limit): detection is replayed over the history with the new parameters; existing confirmed verdicts are never downgraded, and the change is recorded with the round that introduced it.
- Two rounds are started on the same device at once: the second refuses to start while the first holds the device.
- Two machines publish round records for different devices at the same time: both records land on the history branch without conflict, because records are one file per round and never edited after publication.
- A round is run on a device model that has no series yet: a new series is created and labelled preliminary until it reaches the detector's minimum history.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The repository MUST provide one command that performs a complete Android round against a named USB-connected device: device checks, measurement build, fresh install, one unmeasured warm-up launch, every configured journey for the configured number of iterations (20 by default), collection of per-iteration values and one raw trace per iteration, storage of the round, detection and report regeneration.
- **FR-002**: The repository MUST provide one command that performs a complete iOS round against a named USB-connected iPhone from the Apple Silicon Mac, using the platform's standard performance-test facilities, collecting per-iteration values from the platform's result bundle and storing that bundle as the round's raw artifact, with the same iteration count, storage, detection and report steps as the Android round.
- **FR-003**: Both round commands MUST be non-interactive and MUST signal success, refusal (a check failed before measuring) and round failure (measurement started and did not complete) through distinct exit statuses, with the reason written to the command output; a gate flag is reported in the output but MUST NOT change the exit status.
- **FR-004**: Before measuring, an Android round MUST check the device against the existing device protocol (battery level minimum, battery temperature maximum, pinned display refresh rate, airplane mode on, device awake and unlocked) and refuse with the failed check named; an iOS round MUST check thermal state and battery level from inside the measurement run and refuse the same way, MUST record the observed network state (any network path available or none) as round metadata without refusing, and the report MUST show that state on the round.
- **FR-005**: Each round MUST record the build identity (commit, dirty state), the label it was run under, the device identity and operating-system fingerprint, the driving machine, the device checks' results, per-round quality badges (noisy, thermal), and the locations of all raw artifacts, so that any round can be re-analysed later.
- **FR-006**: The history MUST hold Android and iOS rounds together, keyed by the device the round ran on (device identity and model); a series MUST never mix devices or platforms, and the machine that drove the round MUST be recorded on the round as metadata only, never as part of a series key.
- **FR-006a**: Each round MUST be written as a small portable record (per-iteration values, metadata, detection and gate inputs; no raw traces) that is synchronised through a dedicated history branch in the repository; any machine with the repository MUST be able to rebuild the full per-device history from those records and render the combined report, and raw traces MUST stay on the producing machine or travel as job artifacts, never in the repository.
- **FR-007**: The journeys MUST be: cold startup; a tuner journey; a metronome journey; and a stress journey that, until the audio spine exists, exercises the tuner and the metronome together. Journey definitions MUST exist for both platforms under the same names, driven by each platform's own UI-automation facility from inside the measurement run.
- **FR-008**: The metric catalogue MUST define, per platform, the families startup (first frame; fully drawn), frames (Android: per-frame duration percentiles and overrun; iOS: hitch time ratio, hitch count, frame rate), memory (peak; end of journey), app size, named sections (duration, count) and audio health, with the documented mismatches between platforms recorded in the catalogue; families marked non-comparable MUST never be compared across platforms by the detector, the gate or the report.
- **FR-009**: Detection MUST reuse the existing step-fit screening with statistical confirmation, status ratchet (verdicts never downgrade on recompute), preliminary-only verdicts below the minimum history, an environment-suspect grouping when several families step in the same round, and improvement reporting without alarms; its parameters MUST be configuration, not code.
- **FR-010**: The report MUST be one self-contained file that opens offline and shows every series with its history, onset markers by status, quality badges, device fingerprint changes, metric availability per platform, round drill-down and raw-artifact links.
- **FR-011**: Every metric in the catalogue MUST have a gate: a configurable limit on the change of a round's median relative to the median of the last N clean rounds of the same series (N configurable, default 5; per-metric limit defaults set in the catalogue, 30 % where nothing better is known). When exceeded in the worse direction the gate MUST flag the round and metric in the command output and in the report (series mark plus a summary list near the top); it MUST stay inactive with a stated reason when fewer than N clean rounds exist, MUST never flag improvements, MUST record its decision with the round, and MUST NOT fail the command, the round or the session.
- **FR-012**: The app MUST expose, in shared code, an instrumentation facility with: named sections (begin/end around a block), counters, and an animation-section variant; a fixed, single catalogue of section names grouped by family (startup, tuner, metronome, timeline, engine); the same name on both platforms; recorded by each platform's system tracing so that a round can measure it.
- **FR-013**: The engine MUST expose the same facility to its non-real-time code through one entry point, mapped to the same platform facilities and the same catalogue.
- **FR-014**: Instrumentation MUST be absent from release builds and present in measurement builds; it MUST NOT be callable from the audio callback, and the engine's test target MUST prove the real-time path contains none of it.
- **FR-015**: The engine's telemetry MUST carry audio-thread health counters: last and maximum callback duration, over-budget callback count, underrun/overrun count and real-time status, updated from the callback with atomic writes only and read on a non-real-time path; the pipeline MUST turn them into series on both platforms (from the round's trace on Android; from an end-of-journey readout on iOS).
- **FR-016**: Both platforms MUST have a measurement build configuration: release-level optimisations, profiling permitted, instrumentation enabled, no debugger attached, and the platform UI framework's own iOS tracing enabled at start-up.
- **FR-017**: The app MUST report "fully drawn" on its first screens when their content is ready, and every journey anchor MUST carry a stable identifier reachable by the UI-automation facility of both platforms.
- **FR-018**: This feature MUST NOT create a continuous-integration workflow or a self-hosted runner; the round and session commands MUST be runnable by hand on any machine with the device attached and over SSH, and MUST be structured so a CI job can call them unchanged later (no interactive input, exit statuses per FR-003, all configuration in files or arguments).
- **FR-018a**: The round command MUST accept a label (by convention the name of the feature just merged) and a repeat count, run that many consecutive full rounds on one device, stop at the first refusal or failure, publish the records of completed rounds to the history branch, and regenerate the report; the report MUST show label boundaries on each series and name the label an onset coincides with.
- **FR-019**: The existing pipeline tool MUST be adopted into the repository as one module, keeping its history store, detection, report and its regression-injection self-test, dropping the foreign-project scaffolder, and extending it with the iOS collection and the gate; its automated tests MUST remain green and MUST gain tests for the iOS collection, the gate and the metric catalogue.
- **FR-020**: The default build, the existing test lanes and their durations MUST be unaffected by the new module; zero compiler warnings remains a gate; the history branch MUST carry round records only (no raw traces, no reports), and rebuilt histories, raw artifacts and rendered reports MUST live outside version control, with the module's documentation stating where.
- **FR-021**: The module MUST document, in the repository, how to run a round on each platform, how to add a journey and a section name, how to read the report and the gate decision, and which checks each platform can and cannot make.

### Key Entities

- **Round**: one measurement of one build on one device; holds build identity, label, device identity and fingerprint, the driving machine (metadata only), check results, quality badges, status (succeeded, refused, failed), gate decisions, raw-artifact locations and timestamps.
- **Round record**: the portable, trace-free form of a round (per-iteration values, metadata, detection and gate inputs) that is synchronised through the history branch; the history is rebuilt from these records.
- **Series**: the ordered rounds of one metric on one device (identity and model) and platform; the unit the detector, the gate and the report operate on.
- **Sample**: one per-iteration value of one metric in one round; kept raw for statistical confirmation.
- **Journey**: a named user path (cold startup, tuner, metronome, stress) with a definition per platform under the same name.
- **Section catalogue**: the fixed list of instrumentation names grouped by family; the only source of section and counter names.
- **Metric catalogue**: the per-platform list of metric families, their units, direction (higher is worse), comparability across platforms, and gate defaults.
- **Detection**: an append-only verdict (preliminary, suspect, confirmed, improved, environment-suspect) attached to a metric and an onset round.
- **Gate decision**: per round and metric: inactive (with reason), within limit, or above limit (with limit, observed change and comparison rounds); informational only.
- **Report**: the single self-contained rendering of all series, detections, gate decisions and artifacts.
- **Raw artifact**: a per-iteration trace, a result bundle, a size measurement or a log stored under the round for re-analysis.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: From a fresh checkout with a Pixel attached, a maintainer produces a complete Android round, including the report, with one command and no interactive step, in 20 minutes of device time or less.
- **SC-002**: With the iPhone 14 attached to the Apple Silicon Mac, a maintainer produces a complete iOS round, including the report, with one command and no interactive step, in 20 minutes of device time or less.
- **SC-003**: Over ten consecutive rounds of an unchanged build on the same device under the device protocol, the round medians of the startup and memory families vary by 3 % or less (coefficient of variation), on each platform.
- **SC-004**: The pipeline's self-test (deliberately injected regressions on Android) still confirms every injected regression at its onset round within the detector's structural latency, with zero false confirmations on clean spans; on iOS, a deliberately injected 30 % startup slowdown is confirmed within the same latency.
- **SC-005**: A step past a metric's gate limit is flagged in the report and the command output on the very round it appears, the command still exits successfully, and an improvement of the same size is never flagged.
- **SC-006**: One section added to shared code produces a series with that name on both platforms after one round each, with no platform-specific code written by the maintainer.
- **SC-007**: The release build contains no instrumentation names or code, and the measurement build's startup first-frame median is within 5 % of the release build's.
- **SC-008**: The default build and the existing test lanes complete within 5 % of their durations before this feature, with zero new warnings.
- **SC-009**: A labelled session of five rounds launched over SSH on the second Mac with a Pixel attached completes without any interactive step, publishes five round records to the history branch, and the combined report rendered on the Apple Silicon Mac shows them under that label.
- **SC-010**: With the fake engine scripted to known audio-health values, the stress journey produces audio-health series carrying exactly those values on both platforms.

## Assumptions

- The existing pipeline tool is the owner's own, already validated on the Pixel 6 Pro and Pixel 7 Pro; adopting it into the repository rather than rebuilding it is a scope decision of this feature, and its validated detection parameters are kept unchanged.
- Devices for this feature are the owner's Pixel 6 Pro, Pixel 7 Pro and iPhone 14. Android rounds are run by hand, locally or over SSH, on any machine with the device attached, including the owner's second Mac (a 2015 MacBook Pro, which can drive Android devices but not a current iPhone); iOS rounds are run by hand from the Apple Silicon Mac with the iPhone on USB. No CI wiring is built; the commands are shaped so it can be added later.
- Operating cadence: after each feature is merged, a labelled session of rounds is run per device; a single round per feature only attributes an onset to that feature, and the detector's confirmation still needs its usual number of later rounds, so sessions of several rounds (five by default) are the expected practice.
- The journeys are limited to what the app has today (startup, tuner, metronome); the stress journey is a placeholder until the audio spine, and the timeline family in the section catalogue is reserved for later features.
- The audio-health counters are defined and plumbed now and carry real values only once the audio spine exists; with the current engine they report zeros.
- Round records live on a dedicated history branch in the repository; rebuilt histories, raw artifacts and rendered reports live outside version control on the machine that produced them; the round command publishes the round record to the history branch as its last step.
- The detector's minimum history and structural verdict latency are those of the existing tool; the first rounds on any new series yield preliminary verdicts only.
- The device protocol is enforced where the pipeline can enforce it (Android) and recorded where it cannot (iOS network state); iPhone rounds are expected to be run with the network off, but that is advice to the maintainer, not a check.
- The frames families are not comparable across platforms (per-frame percentiles on Android, hitch-based metrics on iOS); startup "fully drawn" and memory families have different definitions per platform and are tracked per platform only.
- This feature is tooling and does not change the constitution's build order; the audio spine remains the next product feature.
- Before planning, the spikes listed in `docs/research/performance-profiling.md` section 10 (items 1, 3, 5, 6 and 7) are run and their results folded into the plan; the plan takes its technology choices from that report.
- Out of scope, recorded as follow-ups: continuous-integration workflows and self-hosted runners for either platform (deferred until the manual sessions have proven themselves and the owner decides how to run them on CI), cloud device farms, third-party cross-platform UI drivers, driving the iPhone from another machine over the network, field telemetry from real users, and baseline-profile generation.
