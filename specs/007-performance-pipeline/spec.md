# Feature Specification: Performance Pipeline

**Feature Branch**: `007-performance-pipeline`

**Created**: 2026-10-08

**Status**: Draft

**Input**: User description: "Performance pipeline: profiling and regression measurement for Tembra Audio on Android and iOS. Why: the app will soon carry a real-time audio engine, a 60–120 Hz telemetry-driven UI and a timeline. We need to see performance regressions (startup, UI frames, memory, binary size, trace sections, audio-thread health) as a trend over rounds on real devices, with a coarse gate, before the audio spine lands. The research and decisions are in docs/research/performance-profiling.md (sections 6–7, 9.1, 10–12); treat it as the source for technology choices in the plan, not as something to re-research. What a maintainer can do when this is done: (1) run an Android performance round from the repository with one command against a USB-connected Pixel, from CI, over SSH or by hand, with the existing device protocol; (2) run an iOS performance round with one command on the Apple Silicon Mac against the USB-connected iPhone 14 (manual runs only), same journeys and metric families where the platform allows, results in the same history; (3) read one self-contained HTML report for both platforms with regression onsets marked by the existing detector, per-platform device series kept separate, and a coarse gate; (4) instrument the app once and see the same named sections on both platforms, never on the audio callback, compiled out in release unless a perf build flag is on; (5) engine audio-thread health measurable from day one of the audio spine; (6) a perf build variant on both platforms plus the small app changes the pipeline needs; (7) a scheduled and on-demand workflow running the Android rounds on a self-hosted runner (the 2015 MacBook Pro, Android only). Constraints: reuse the existing trace-pulse pipeline by copying it into the repository and extending it for iOS and the gate; native journey drivers on each platform; no Maestro, no device farm, no field telemetry; metric mapping per report section 6.3; constitution rules unchanged; spikes from report section 10 before the plan. Out of scope: cloud device farms, Maestro, an iOS CI runner, remote-iPhone networking, field telemetry, baseline-profile generation."

## User Scenarios & Testing *(mandatory)*

The user of this feature is a Tembra Audio maintainer. A **round** is one complete measurement of one build on one real device: the device is checked, the measurement build is installed fresh, warmed up once without measuring, each benchmark journey is run a fixed number of times, every measured value is kept per iteration together with its raw trace, and the result is appended to the **history** of that device's series. **Detection** looks across the history for step changes and confirms them statistically; the **report** shows the series, the onsets and the verdicts; the **gate** is a coarse tripwire that fails the run when a metric jumps past a configured limit.

### User Story 1 - Run an Android performance round with one command (Priority: P1)

A maintainer connects a Pixel over USB, runs one command from the repository, and gets a complete round appended to that device's history: device checks, measurement build, fresh install, warm-up launch, the benchmark journeys (cold startup, tuner, metronome, and a "stress" placeholder that becomes meaningful once the audio spine exists), per-iteration values and traces, detection and a regenerated report. The same command works when launched by hand, over SSH, or by an unattended runner.

**Why this priority**: it is the foundation every other story feeds into, and it is the lowest-risk slice because the Android pipeline already exists and has been validated on these devices; adopting it into the repository with Tembra's own journeys is the first useful deliverable.

**Independent Test**: on a fresh checkout with a Pixel attached, run the round command; a new round appears in the history with values for every journey, a report file is produced, and running the command a second time produces a second round on the same series. Delivers a reproducible measurement of the current build.

**Acceptance Scenarios**:

1. **Given** a Pixel attached, charged above the minimum, cool, in the required display and network state, **When** the maintainer runs the round command for that device, **Then** the command installs the measurement build fresh, performs one unmeasured warm-up launch, runs every configured journey the configured number of times, stores per-iteration values and one raw trace per iteration under the round, appends the round to the history, runs detection, regenerates the report, and exits successfully.
2. **Given** a Pixel that fails a device check (for example too warm or below the battery minimum), **When** the maintainer runs the round command, **Then** the command refuses before building or measuring, names the failed check and the threshold, and exits with a failure status without touching the history.
3. **Given** two Android devices attached, **When** the maintainer runs the round command without naming a device, **Then** the command refuses and asks for an explicit device identifier rather than guessing.
4. **Given** a journey that cannot complete (for example a UI anchor it expects is missing), **When** the round runs, **Then** the round is recorded as failed with the reason, no partial values enter the series, and the raw artifacts collected so far are kept for inspection.
5. **Given** the command is launched over SSH or by an unattended runner, **When** it runs, **Then** it never waits for interactive input and its exit status alone tells the caller whether the round succeeded, was refused, or failed.

---

### User Story 2 - Run an iOS performance round with one command (Priority: P2)

A maintainer connects the iPhone 14 to the Apple Silicon Mac, runs one command from the repository, and gets an iOS round appended to that device's history in the same history store as the Android rounds, measured with the platform's standard performance-test facilities. The journeys are the same ones as on Android; the metric families are the same where the platform offers an equivalent and are recorded as platform-specific families where it does not. Device checks that the host cannot read (thermal state, battery level) are made inside the measurement run and refuse the round the same way. iOS rounds are run by hand; no unattended iOS runner is part of this feature.

**Why this priority**: it is the half of the pipeline that does not exist yet and the reason the feature exists; it depends on the history, detection and report of Story 1.

**Independent Test**: with the iPhone attached, run the iOS round command; a round appears in the iPhone series with per-iteration values for every journey, the report shows the iPhone series next to the Pixel series, and a second run appends a second round. Delivers the first iOS measurement history for the app.

**Acceptance Scenarios**:

1. **Given** the iPhone attached, paired, in developer mode and unlocked, **When** the maintainer runs the iOS round command, **Then** the command builds the measurement configuration, installs it fresh, performs one unmeasured warm-up launch, runs every configured journey the configured number of times, reads per-iteration values from the platform's result bundle, stores the bundle as the round's raw artifact, appends the round to the iPhone series, runs detection, regenerates the report, and exits successfully.
2. **Given** the iPhone reports a thermal state other than nominal, or a battery level below the minimum, at the start of a journey, **When** the round runs, **Then** the round is refused with the failed check named and nothing enters the history.
3. **Given** a metric family that exists on Android but has no iOS equivalent (per-frame duration percentiles), **When** the iOS round completes, **Then** the iOS series carries the platform's own frame-health family (hitch time ratio, hitch count, frame rate) under distinct names, and nothing claims the two families are comparable.
4. **Given** a journey wrapped in a named section on both platforms, **When** an iOS round completes, **Then** the section's duration appears as a series with the same name as on Android.

---

### User Story 3 - Read one report for both platforms, with a coarse gate (Priority: P3)

A maintainer opens a single self-contained report file and sees every device series (Pixel 6 Pro, Pixel 7 Pro, iPhone 14) with its history, regression onsets marked by the existing detector with their status (preliminary, suspect, confirmed, improved, environment-suspect), per-round quality badges, and links to the raw artifacts. The maintainer also configures a coarse gate per metric; when a round's median jumps past the configured limit relative to the recent clean rounds of the same series, the round command exits with a gate failure that an unattended runner turns into a failed job.

**Why this priority**: trend detection is the primary signal and already exists for one platform; extending the report to several platforms and adding the tripwire is what makes the pipeline actionable without a human reading every chart.

**Independent Test**: with at least two series in the history, open the report; both appear with onsets. Configure a gate limit, inject a known slowdown into a journey, run a round; the command fails with the gate named. Delivers the "is this build worse?" answer in one file and one exit status.

**Acceptance Scenarios**:

1. **Given** rounds exist for two or more device series, **When** the report is regenerated, **Then** one file shows each series separately with its onset markers, verdict statuses, quality badges and artifact links, opens offline, and never overlays series from different platforms on one axis for families marked non-comparable.
2. **Given** fewer rounds than the detector's minimum history on a series, **When** the report is regenerated, **Then** verdicts for that series are labelled preliminary and the report says how many more rounds are needed.
3. **Given** a gate limit configured for a metric and at least the configured number of clean rounds on the series, **When** a round's median for that metric exceeds the limit relative to the median of those clean rounds, **Then** the round command exits with a gate-failure status that names the metric, the limit, the observed change and the rounds it was compared against, and the round is still recorded in the history with the gate decision attached.
4. **Given** fewer clean rounds than the gate needs, **When** a round runs, **Then** the gate is reported as inactive with the reason and the round succeeds on the trend path alone.
5. **Given** a metric that improved past the limit (lower is better and it dropped), **When** a round runs, **Then** the gate does not fail; improvements are reported, never alarmed.

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

### User Story 6 - Unattended Android rounds on a schedule (Priority: P6)

A maintainer registers the second Mac (Android only) as a self-hosted runner with the Pixels attached, and a workflow runs an Android round per device on a schedule and on demand, uploads the report and the round's raw artifacts, and fails the job when a round is refused, fails, or trips the gate. iOS is never part of this workflow.

**Why this priority**: unattended rounds are what turn the pipeline into a trend; they come last because they only orchestrate Story 1 and Story 3.

**Independent Test**: trigger the workflow on demand with a Pixel attached to the runner; a round is produced, the report and artifacts are attached to the job, and the job's status reflects the round's status. Delivers rounds without a human at the keyboard.

**Acceptance Scenarios**:

1. **Given** the runner is registered and a Pixel is attached, **When** the schedule fires or a maintainer triggers the workflow, **Then** the workflow runs one round per configured device, uploads the report and raw artifacts, and succeeds.
2. **Given** the device fails a check, the round fails, or the gate trips, **When** the workflow runs, **Then** the job fails with the same reason the command reports, and whatever artifacts were collected are still uploaded.
3. **Given** no device is attached, **When** the workflow runs, **Then** the job fails fast with a clear message and does not wait for a device.

---

### Edge Cases

- The device disconnects mid-round: the round is marked failed, partial values are not stored in the series, collected artifacts are kept, and the command exits with a failure status.
- The phone is locked or asleep at round start: the round is refused with that reason rather than timing out silently.
- The device's operating system was updated since the last round: the round is recorded with the new fingerprint, and the report marks the version change on that series so a step there is not mistaken for a code regression.
- The measurement build is identical to the previous round's build: the round still runs and is recorded; identical builds are how noise is measured.
- A journey needs the audio spine (the stress journey before the spine exists): it runs as a placeholder that exercises the existing tuner and metronome together, and its audio series carry whatever the engine reports (zeros with the current engine).
- A metric exists on one platform only: the report shows it on that platform and lists it as unavailable on the other; it never appears as a missing data point on the other platform's series.
- History and detection parameters change (for example a new gate limit): detection is replayed over the history with the new parameters; existing confirmed verdicts are never downgraded, and the change is recorded with the round that introduced it.
- Two rounds are started against the same history at once: the second refuses to start while the first holds the history.
- A round is run on a device model that has no series yet: a new series is created and labelled preliminary until it reaches the detector's minimum history.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The repository MUST provide one command that performs a complete Android round against a named USB-connected device: device checks, measurement build, fresh install, one unmeasured warm-up launch, every configured journey for the configured number of iterations (20 by default), collection of per-iteration values and one raw trace per iteration, storage of the round, detection and report regeneration.
- **FR-002**: The repository MUST provide one command that performs a complete iOS round against a named USB-connected iPhone from the Apple Silicon Mac, using the platform's standard performance-test facilities, collecting per-iteration values from the platform's result bundle and storing that bundle as the round's raw artifact, with the same iteration count, storage, detection and report steps as the Android round.
- **FR-003**: Both round commands MUST be non-interactive and MUST signal success, refusal (a check failed before measuring), round failure (measurement started and did not complete) and gate failure through distinct exit statuses, with the reason written to the command output.
- **FR-004**: Before measuring, an Android round MUST check the device against the existing device protocol (battery level minimum, battery temperature maximum, pinned display refresh rate, airplane mode on, device awake and unlocked) and refuse with the failed check named; an iOS round MUST check thermal state and battery level from inside the measurement run and refuse the same way.
- **FR-005**: Each round MUST record the build identity (commit, dirty state), the device identity and operating-system fingerprint, the device checks' results, per-round quality badges (noisy, thermal), and the paths of all raw artifacts, so that any round can be re-analysed later.
- **FR-006**: The history MUST hold Android and iOS rounds together, keyed by platform and device model; a series MUST never mix devices or platforms.
- **FR-007**: The journeys MUST be: cold startup; a tuner journey; a metronome journey; and a stress journey that, until the audio spine exists, exercises the tuner and the metronome together. Journey definitions MUST exist for both platforms under the same names, driven by each platform's own UI-automation facility from inside the measurement run.
- **FR-008**: The metric catalogue MUST define, per platform, the families startup (first frame; fully drawn), frames (Android: per-frame duration percentiles and overrun; iOS: hitch time ratio, hitch count, frame rate), memory (peak; end of journey), app size, named sections (duration, count) and audio health, with the documented mismatches between platforms recorded in the catalogue; families marked non-comparable MUST never be compared across platforms by the detector, the gate or the report.
- **FR-009**: Detection MUST reuse the existing step-fit screening with statistical confirmation, status ratchet (verdicts never downgrade on recompute), preliminary-only verdicts below the minimum history, an environment-suspect grouping when several families step in the same round, and improvement reporting without alarms; its parameters MUST be configuration, not code.
- **FR-010**: The report MUST be one self-contained file that opens offline and shows every series with its history, onset markers by status, quality badges, device fingerprint changes, metric availability per platform, round drill-down and raw-artifact links.
- **FR-011**: The gate MUST be configurable per metric as a limit on the change of a round's median relative to the median of the last N clean rounds of the same series (N configurable, default 5; limit default 30 %); it MUST fail the command when exceeded in the worse direction, stay inactive with a stated reason when fewer than N clean rounds exist, never fail on improvements, and record its decision with the round.
- **FR-012**: The app MUST expose, in shared code, an instrumentation facility with: named sections (begin/end around a block), counters, and an animation-section variant; a fixed, single catalogue of section names grouped by family (startup, tuner, metronome, timeline, engine); the same name on both platforms; recorded by each platform's system tracing so that a round can measure it.
- **FR-013**: The engine MUST expose the same facility to its non-real-time code through one entry point, mapped to the same platform facilities and the same catalogue.
- **FR-014**: Instrumentation MUST be absent from release builds and present in measurement builds; it MUST NOT be callable from the audio callback, and the engine's test target MUST prove the real-time path contains none of it.
- **FR-015**: The engine's telemetry MUST carry audio-thread health counters: last and maximum callback duration, over-budget callback count, underrun/overrun count and real-time status, updated from the callback with atomic writes only and read on a non-real-time path; the pipeline MUST turn them into series on both platforms (from the round's trace on Android; from an end-of-journey readout on iOS).
- **FR-016**: Both platforms MUST have a measurement build configuration: release-level optimisations, profiling permitted, instrumentation enabled, no debugger attached, and the platform UI framework's own iOS tracing enabled at start-up.
- **FR-017**: The app MUST report "fully drawn" on its first screens when their content is ready, and every journey anchor MUST carry a stable identifier reachable by the UI-automation facility of both platforms.
- **FR-018**: A workflow MUST run the Android rounds on a self-hosted runner on a schedule and on demand, one round per configured device, upload the report and raw artifacts, and fail with the round's reason; it MUST NOT include iOS.
- **FR-019**: The existing pipeline tool MUST be adopted into the repository as one module, keeping its history store, detection, report and its regression-injection self-test, dropping the foreign-project scaffolder, and extending it with the iOS collection and the gate; its automated tests MUST remain green and MUST gain tests for the iOS collection, the gate and the metric catalogue.
- **FR-020**: The default build, the existing test lanes and their durations MUST be unaffected by the new module; zero compiler warnings remains a gate; measurement history and reports MUST live outside version control, with the module's documentation stating where.
- **FR-021**: The module MUST document, in the repository, how to run a round on each platform, how to add a journey and a section name, how to read the report and the gate decision, and which checks each platform can and cannot make.

### Key Entities

- **Round**: one measurement of one build on one device; holds build identity, device fingerprint, check results, quality badges, status (succeeded, refused, failed, gate-failed), gate decision, raw-artifact paths and timestamps.
- **Series**: the ordered rounds of one metric on one device model and platform; the unit the detector, the gate and the report operate on.
- **Sample**: one per-iteration value of one metric in one round; kept raw for statistical confirmation.
- **Journey**: a named user path (cold startup, tuner, metronome, stress) with a definition per platform under the same name.
- **Section catalogue**: the fixed list of instrumentation names grouped by family; the only source of section and counter names.
- **Metric catalogue**: the per-platform list of metric families, their units, direction (higher is worse), comparability across platforms, and gate defaults.
- **Detection**: an append-only verdict (preliminary, suspect, confirmed, improved, environment-suspect) attached to a metric and an onset round.
- **Gate decision**: per round and metric: inactive (with reason), passed, or failed (with limit, observed change and comparison rounds).
- **Report**: the single self-contained rendering of all series, detections, gate decisions and artifacts.
- **Raw artifact**: a per-iteration trace, a result bundle, a size measurement or a log stored under the round for re-analysis.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: From a fresh checkout with a Pixel attached, a maintainer produces a complete Android round, including the report, with one command and no interactive step, in 20 minutes of device time or less.
- **SC-002**: With the iPhone 14 attached to the Apple Silicon Mac, a maintainer produces a complete iOS round, including the report, with one command and no interactive step, in 20 minutes of device time or less.
- **SC-003**: Over ten consecutive rounds of an unchanged build on the same device under the device protocol, the round medians of the startup and memory families vary by 3 % or less (coefficient of variation), on each platform.
- **SC-004**: The pipeline's self-test (deliberately injected regressions on Android) still confirms every injected regression at its onset round within the detector's structural latency, with zero false confirmations on clean spans; on iOS, a deliberately injected 30 % startup slowdown is confirmed within the same latency.
- **SC-005**: A 30 % step in a gated metric fails the round command on the very round it appears, and a 30 % improvement never does.
- **SC-006**: One section added to shared code produces a series with that name on both platforms after one round each, with no platform-specific code written by the maintainer.
- **SC-007**: The release build contains no instrumentation names or code, and the measurement build's startup first-frame median is within 5 % of the release build's.
- **SC-008**: The default build and the existing test lanes complete within 5 % of their durations before this feature, with zero new warnings.
- **SC-009**: Five consecutive scheduled runs of the unattended Android workflow complete, upload their report and artifacts, and report the correct status without human intervention.
- **SC-010**: With the fake engine scripted to known audio-health values, the stress journey produces audio-health series carrying exactly those values on both platforms.

## Assumptions

- The existing pipeline tool is the owner's own, already validated on the Pixel 6 Pro and Pixel 7 Pro; adopting it into the repository rather than rebuilding it is a scope decision of this feature, and its validated detection parameters are kept unchanged.
- Devices for this feature are the owner's Pixel 6 Pro, Pixel 7 Pro and iPhone 14; the Android runner is the owner's second Mac (a 2015 MacBook Pro, which can drive Android devices but not a current iPhone); iOS rounds are run by hand from the Apple Silicon Mac with the iPhone on USB.
- The journeys are limited to what the app has today (startup, tuner, metronome); the stress journey is a placeholder until the audio spine, and the timeline family in the section catalogue is reserved for later features.
- The audio-health counters are defined and plumbed now and carry real values only once the audio spine exists; with the current engine they report zeros.
- Measurement history, raw artifacts and reports live outside version control on the machine that produced them; the unattended workflow publishes the report and artifacts as job attachments.
- The detector's minimum history and structural verdict latency are those of the existing tool; the first rounds on any new series yield preliminary verdicts only.
- The frames families are not comparable across platforms (per-frame percentiles on Android, hitch-based metrics on iOS); startup "fully drawn" and memory families have different definitions per platform and are tracked per platform only.
- This feature is tooling and does not change the constitution's build order; the audio spine remains the next product feature.
- Before planning, the spikes listed in `docs/research/performance-profiling.md` section 10 (items 1, 3, 5, 6 and 7) are run and their results folded into the plan; the plan takes its technology choices from that report.
- Out of scope, recorded as follow-ups: cloud device farms, third-party cross-platform UI drivers, an iOS unattended runner, driving the iPhone from another machine over the network, field telemetry from real users, and baseline-profile generation.
