# NotebookLM Q&A: profiling a KMP app (Android trace-pulse + iOS stock tooling)

Raw answers to eight targeted questions against notebook `5cecd7ef-85e6-485e-a26d-b38922cb65c5` (73 sources), 2026-10-06. Unedited. Where an answer says a fact is not established by the sources, `performance-profiling.md` records whether a local check or a primary source settled it (per-iteration values are in the xcresult metrics JSON; the default iteration count stays unverified; `-test-iterations` repeats whole tests and is not the measure block's iteration count).


# q1-ios-stock  (refs: 39)

Apple's stock command-line tools offer unattended performance measurement capabilities for USB-connected iPhones using **`xcodebuild test`**, **`xcresulttool`**, and **`xctrace`** [1, 2].

---

### 1. Automated Performance Measurement via `xcodebuild test` and `XCTMetric`

`xcodebuild test` executes XCTest performance test suites on a connected physical iOS device without GUI interaction [1, 2].

* **Every `XCTMetric` Class Named in the Sources**:
  * **`XCTClockMetric`**: Measures total wall-clock execution time elapsed during a test block [3-6].
  * **`XCTCPUMetric`**: Tracks processing density, CPU time, instruction counts, and CPU cycle consumption per thread [3-6].
  * **`XCTMemoryMetric`**: Tracks physical memory usage, peak memory footprint, and heap allocations [3, 4, 7, 8].
  * **`XCTStorageMetric`**: Quantifies the volume of data logically written to non-volatile storage during test execution [3, 4, 8, 9].
  * **`XCTOSSignpostMetric`**: Measures the elapsed execution time of custom code regions marked by `os_signpost` interval bounds [3, 4, 7, 8]. It also provides specific helper properties for UI transitions and scrolling (`customNavigationTransitionMetric`, `navigationTransitionMetric`, `scrollingAndDecelerationMetric`) [10-12].
  * **`XCTApplicationLaunchMetric`**: Measures application launch latency from process execution to first frame display, with optional configuration to wait until the app becomes responsive (`init(waitUntilResponsive: Bool)`) [3, 4, 8, 9, 13].
  * **`XCTHitchMetric`**: Measures UI hitch density (hitch duration per second of animation, \\(\text{ms/s}\\)), capturing visual stutter during scroll and transition passes [3, 4, 7, 8].

* **Default Iteration Count**:
  * **Not established in sources**: The sources document that custom iteration counts can be specified via command-line flags such as `-test-iterations <count>` or `-run-tests-until-failure` [14, 15], but they do **not** state what default number of iterations XCTest runs if omitted.

* **Baseline Storage, Keys, and Thresholds**:
  * **Storage Location**: Performance baselines are stored in `.xcbaselines` property list files inside the test bundle [1].
  * **Keys and Thresholds**: During test execution, XCTest compares newly measured values against baseline values stored in `.xcbaselines` [1].
  * **Not established in sources**: The sources do **not** disclose the internal key names used inside `.xcbaselines` property lists, nor do they state the exact default percentage or standard-deviation threshold values XCTest applies to trigger a failure.

* **What `-enablePerformanceTestsDiagnostics YES` Adds**:
  * Passing `-enablePerformanceTestsDiagnostics YES` to `xcodebuild test` enables diagnostic collection during execution, generating a detailed `.xcresult` bundle containing performance logs, baseline comparison results, and trace attachments [1, 2].

* **Regression Failure Behavior**:
  * **Yes**: XCTest compares measured values against stored baseline values and triggers a test failure (which fails the overall `xcodebuild` run) if performance degrades significantly past the baseline [1, 16].

---

### 2. Result Parsing via `xcresulttool`

The `xcresulttool` command-line utility extracts performance measurements from generated `.xcresult` bundles [2, 17].

* **Extraction Commands**:
  * `xcrun xcresulttool get --format json --path PerformanceResults.xcresult > results.json`: Extracts result bundle data into structured JSON format [2].
  * `xcresulttool get test-results metrics --path Example.xcresult [--test-id <id>]`: Prints performance metrics associated with a specific test or all tests in the bundle [18, 19].
  * `xcresulttool export metrics --path Example.xcresult --output-path ./Out [--test-id <id>]`: Exports performance measurements into a CSV file for automated post-processing [20, 21].

* **Not Established in Sources**:
  * The sources do **not** establish whether raw, per-iteration measurement values are explicitly included inside the structured JSON returned by `xcresulttool get`, or if per-iteration breakdowns are only exported via `export metrics` to CSV format [2, 18, 20].

---

### 3. Headless Trace Collection and Export via `xctrace`

The `xctrace` command-line tool drives headless Instruments recording sessions on USB-connected physical iOS devices and exports trace data to XML [2, 22-24].

* **Templates Named in Sources**:
  * `"Audio System Trace"` [2]
  * `"Time Profiler"` [2, 25, 26]
  * `"Allocations"` [27, 28]
  * `"Metal System Trace"` [26]
  * `"System Trace"` [29]
  * Custom templates (e.g., `--template 'MyCustomTemplate'`) [29, 30]

* **`xctrace record` Flags**:
  * `--device <name | UDID>`: Targets a specific physical device or simulator by UDID or device name (e.g., `--device 00008101-000123456789002E` or `--device-name "Chad's iPhone"`) [2, 25, 26, 31].
  * `--launch -- <command> [arguments]`: Launches and records the specified app process with provided arguments (must be supplied as the last option in the command) [2, 26, 32].
  * `--attach <pid | name>`: Attaches to and profiles an already running process by name or PID [25, 32].
  * `--time-limit <time [ms|s|m|h]>`: Restricts the recording duration (e.g., `--time-limit 15s` or `--time-limit 30s`) [2, 25, 27, 28, 31].

* **`xctrace export` Flags**:
  * Output format: Exports trace file data into structured XML format [22, 33].
  * `--toc`: Exports the Table of Contents of a `.trace` bundle, showing available runs, data tracks, and table schemas [22, 29, 34].
  * `--xpath <expression>`: Executes an XPath query on the Table of Contents to select and export specific data tables/entities into XML [22, 27, 28, 34, 35] (e.g., `xcrun xctrace export --input audio_profile.trace --xpath '/trace-toc/run[@number="1"]/data/table[@name="time-profile"]' --output time_profile.xml` [22]).
  * Time restriction flags: `--time-start`, `--time-end`, and `--duration` accept unit suffixes (`ns`, `us`, `ms`, `s`) to crop exported data [36, 37].

---

### Explicit Summary of What the Sources Do NOT Establish

1. **Default XCTest Iteration Count**: The sources do not establish how many iterations XCTest runs by default when `-test-iterations` is omitted [14, 15].
2. **`.xcbaselines` Internal Schema and Numerical Thresholds**: The sources do not establish the exact plist keys or exact percentage/sigma threshold values stored inside `.xcbaselines` [1].
3. **Per-Iteration Values in `xcresult` JSON**: The sources do not establish whether per-iteration raw values exist in the `xcresulttool get` JSON output or if they are restricted to CSV exports [2, 18, 20].
4. **Allocations Call Tree Export via `xctrace`**: The sources explicitly note that `xctrace export` can export flat "Allocations List" tables, but does **not** support exporting "Call Tree" breakdown format in XML via command line [27, 28].
5. **xctrace CLI Deferred Mode**: The sources do not establish whether `xctrace record` supports initiating deferred recording mode via CLI [38, 39].

💡 Would you like to compare these iOS `xctrace` and `XCTest` capabilities with the Android Macrobenchmark and Perfetto trace collection pipeline?

# q2-kmp-ios  (refs: 23)

Based on the provided sources, here is what is documented regarding profiling Kotlin/Native and Compose Multiplatform code on iOS using Xcode Instruments:

---

### 1. Symbolication of Kotlin Frames in Xcode Instruments
* **dSYM Debug Symbols**: Translating memory addresses into human-readable Kotlin source locations (functions and line numbers) requires a debug symbol (`.dSYM`) file [1, 2]. The Kotlin/Native compiler generates `.dSYM` files for release (optimized) binaries on Apple platforms by default, which Xcode automatically detects for symbolication when building or profiling [2, 3]. Kotlin debug information is compatible with the **DWARF 2** specification [4].
* **Debug Compiler Flags**: To include debug information in Kotlin binaries on other build configurations or platforms, add the compiler flag `-Xadd-light-debug` or `-Xadd-light-debug=enable` via `freeCompilerArgs` in the Gradle build configuration [3, 5]:
  ```kotlin
  kotlin {
      targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
          binaries.all {
              freeCompilerArgs += "-Xadd-light-debug=enable"
          }
      }
  }
  ```
* **Source Line Stack Traces**: To include source file locations and line numbers in exception stack traces, configure `-Xbinary=sourceInfoType=coresymbolication` (available on Apple targets) or `libbacktrace` [6].

---

### 2. Garbage Collector Logging and Selection
* **GC Logging**: To log garbage collection performance and pauses, set the compiler flag `-Xruntime-logs=gc=info` in your Gradle build script [5, 7]. The logs are printed to `stderr` [7].
* **GC Selection (`kotlin.native.binary.gc`)**: You can select different garbage collector algorithms by setting `kotlin.native.binary.gc=<value>` in `gradle.properties` or passing `-Xbinary=gc=<value>` to the compiler [6, 8, 9]:
  * **`cms`** *(default since Kotlin 2.4.0)*: Concurrent Mark and Sweep collector [6, 10].
  * **`pmcs`**: Parallel Mark Concurrent Sweep collector [5, 6, 11].
  * **`stwms`**: Simple Stop-The-World Mark and Sweep collector [6].
  * **`noop`**: Disables garbage collection entirely (memory usage grows continuously; intended for short-lived runs/testing) [6, 12].
* **Single-Threaded Marking**: To disable parallel marking in the GC, set `kotlin.native.binary.gcMarkSingleThreaded=true` [6, 11].

---

### 3. Safepoint Signposts in Xcode Instruments
* **Enabling Safepoints**: To map Kotlin/Native GC pause durations into Xcode Instruments timelines, enable safepoint signposts by adding `kotlin.native.binary.enableSafepointSignposts=true` in `gradle.properties` or passing `-Xbinary=enableSafepointSignposts=true` [9, 13, 14].
* **Instruments Configuration**:
  1. Open Xcode Instruments (`Product | Profile` or `Cmd + I`) and select the **`os_signpost`** template [13].
  2. Configure the signpost filter with the following exact names:
     * **Subsystem**: `org.kotlinlang.native.runtime` [13, 14]
     * **Category**: `safepoint` [13, 14]
  3. Start recording. Each signpost event (represented as a blue blob/interval) corresponds to a GC pause or thread-stop phase [13, 14], allowing developers to verify whether UI frame hitches correlate with Kotlin GC pauses [14].

---

### 4. Skiko, Metal, and CADisplayLink Frame Production
* **Rendering Architecture**: Compose Multiplatform (CMP) on iOS bypasses UIKit layout rendering and draws its UI hierarchy directly [15]:
  1. **Compose UI Hierarchy**: Recomposition and layout logic execute in Kotlin Multiplatform [15].
  2. **Skiko (Skia for Kotlin)**: Converts Compose vector drawing calls into low-level Skia graphics commands [15].
  3. **Apple Metal Framework**: Rasterizes Skia commands into GPU hardware command buffers on a Metal GPU surface (`CAMetalLayer`) inside a custom `UIViewController` [15].
  4. **`CADisplayLink` Loop**: Synchronizes frame submission with the display refresh rate [15].
* **Frame Budgets & Hitches**: On Apple ProMotion displays (\\(120\text{ Hz}\\)), the frame budget is \\(8.33\text{ ms}\\) (compared to \\(16.67\text{ ms}\\) at \\(60\text{ Hz}\\)) [16]. If Compose layout evaluation or Skia command generation exceeds this budget, `CADisplayLink` drops a frame, triggering an animation hitch captured by `XCTHitchMetric` [16].
* **ProMotion Support**: To prevent ProMotion displays from locking at \\(60\text{ Hz}\\), the app's `Info.plist` must specify `CADisableMinimumFrameDurationOnPhone = true` [17].

---

### 5. Compose Multiplatform `benchmarks/multiplatform` Project
* **What It Measures**: A dedicated JetBrains benchmark suite measuring performance across Compose Multiplatform targets (iOS, macOS, Desktop, Web) [18].
  * **Benchmark Catalog**: Includes `AnimatedVisibility` (image visibility toggling), `LazyGrid` (12,000-item grid jump/scroll), `LazyGrid-ItemLaunchedEffect`, `LazyGrid-SmoothScroll`, `LazyGrid-SmoothScroll-ItemLaunchedEffect`, `VisualEffects` (particles/stars), `LazyList` (complex list with pull-to-refresh), `MultipleComponents`, `MultipleComponents-NoVectorGraphics`, `TextLayout`, `CanvasDrawing`, and `HeavyShader` [19].
  * **Execution Modes**: Supports `SIMPLE`, `VSYNC_EMULATION`, `REAL`, and `STARTUP` modes [20].
* **How It Runs on iOS**:
  * **IDE Execution**: Built in Release configuration from Xcode (`iosApp/iosApp.xcodeproj`) or Fleet / Android Studio using the `iosApp` run configuration [21].
  * **Device Discovery**: Target iOS devices are identified using `xcrun xctrace list devices` [22].
  * **CLI Execution**: Executed via Kotlin main script or shell script [22]:
    ```bash
    ./run_ios_benchmarks.main.kts <DEVICE ID> benchmarks=AnimatedVisibility,LazyGrid
    # or via shell script:
    ./iosApp/run_ios_benchmarks.sh <DEVICE ID>
    ```
  * **Process Isolation**: Adding `separateProcess=true` forces each benchmark into an isolated process to prevent inter-benchmark pollution [20, 23].
  * **Output Formats**: Reports are written to `benchmarks/build/benchmarks/text-reports/` (via `.main.kts`) or `benchmarks_result/` (via `.sh`) [23], and can be configured to save structured CSV or JSON reports via `saveStatsToCSV=true` or `saveStatsToJSON=true` [20].

💡 Would you like to explore how to set up automated statistical regression tracking (like PELT change-point detection) for these iOS benchmark runs in CI?

# q3-facade  (refs: 16)

### 1. Sources Describing Multiplatform Tracing Abstractions

The sources describe a multiplatform tracing architecture where high-level UI/application sections and low-level C++ engine events route to platform-native tracing subsystems:

* **High-Level UI Tracing Abstraction**:
  * **Android (`androidx.tracing.Trace.beginSection`)**: `androidx.tracing.Trace.beginSection(sectionName)` and `Trace.endSection()` (or the Kotlin `trace(sectionName) { ... }` block wrapper) mark custom trace sections in Android application code [1, 2]. These sections are captured by Android system tracing and reported in Jetpack Macrobenchmark runs via `TraceSectionMetric` [1, 2].
  * **iOS (`os_signpost`)**: Apple's `os_signpost` framework uses `beginInterval` / `endInterval` or `os_signpost_interval_begin` / `os_signpost_interval_end` to record time-based intervals displayed in Xcode Instruments timelines and measured in XCTest via `XCTOSSignpostMetric` [3-5].
  * **Unified KMP Facade**: **"Performance Profiling and Automated Regression Tracking for Kotlin Multiplatform Mobile Applications"** [3, 6] specifies a unified `expect`/`actual` `PlatformTracer` object in Kotlin Multiplatform:
    * `commonMain`: Exposes `expect object PlatformTracer` with `beginSection(sectionName: String)` and `endSection()`.
    * `androidMain`: Executes `actual fun beginSection` by delegating directly to `androidx.tracing.Trace.beginSection(sectionName)`.
    * `iosMain`: Executes `actual fun beginSection` by delegating to a C shim wrapper (`kmp_os_signpost_interval_begin(sectionName)`).

* **Low-Level C++ Engine Tracing**:
  * **Perfetto SDK Track Events in C++**: For real-time or native code (such as C++ audio rendering loops), C++ logic integrates the Perfetto Tracing SDK directly (`perfetto.h` / `perfetto.cc`) using `TRACE_EVENT("category", "name")` macros and `perfetto::TrackEvent::Register()` [7-9].

---

### 2. Perfetto SDK In-Process Backend Support on iOS

* **Official Perfetto SDK Documentation**: According to **"Tracing SDK - Perfetto Tracing Docs"** [10], the in-process backend (`perfetto::kInProcessBackend`) hosts the tracing service and data sources entirely inside the app process without OS daemon dependencies, and is documented as supported on **Android, Linux, macOS, and Windows** (iOS is **not** listed in the official platform support list) [10].
* **KMP Mobile Profiling Architecture**: **"Performance Profiling and Automated Regression Tracking for Kotlin Multiplatform Mobile Applications"** [3, 11] documents compiling the amalgamated Perfetto C++ SDK directly into native iOS builds and initializing `perfetto::kInProcessBackend` on iOS to allow in-app, cross-platform C++ engine tracing without requiring root privileges or platform daemon support.

---

### 3. Calling `os_signpost` from Kotlin/Native (`cinterop` vs. C Shim)

* **A C Shim is Required**: `os_signpost` functions in Apple's `<os/signpost.h>` header are implemented as **C preprocessor variadic macros** (e.g., `#define os_signpost_interval_begin(...)`).
* Kotlin/Native's `cinterop` tool cannot directly bind or wrap C variadic preprocessor macros through standard `platform.darwin` or `platform.os` definitions [12, 13].
* As a result, calling `os_signpost` from Kotlin/Native requires a **C shim**—a static wrapper function exposing a plain C ABI header (such as `kmp_os_signpost_interval_begin(const char* name)`) that `cinterop` can successfully parse and bind [6, 12, 13].

---

### 4. Sentry Kotlin Multiplatform as a Tracing Facade

* **Not Documented as a Tracing Facade**: The sources do **NOT** document or mention Sentry Kotlin Multiplatform spans or transactions as a performance tracing facade.
* **Actual Documented Scope**: The official Sentry KMP documentation (**"Kotlin Multiplatform | Sentry for Kotlin Multiplatform"**) [14-16] covers initialization (`Sentry.init`), exception capturing (`Sentry.captureException`), crash reporting, and automatic breadcrumbs for app lifecycle and UI events.
* In the profiling taxonomy outlined in **"Performance Profiling and Automated Regression Tracking for Kotlin Multiplatform Mobile Applications"** [3], Sentry Mobile is categorized under **Production Telemetry**, whereas performance trace section instrumentation relies on the `expect`/`actual` `PlatformTracer` (`androidx.tracing.Trace` / `os_signpost`) and the Perfetto C++ SDK [3, 6].

---

💡 Would you like to review how the `PlatformTracer` expect/actual facade is implemented in C++ and Kotlin for cross-platform trace section capture?

# q4-audio  (refs: 0)

Based on the sources in your notebook, here is a detailed breakdown of real-time audio thread measurement and scheduling on Android and iOS, organized by platform and explicitly distinguishing official documentation from developer forum findings.

---

### 1. Android Real-Time Audio Thread Measurement & Scheduling

#### **Google / Android Official Documentation (Oboe Guide & Android NDK)**
* **Oboe `AudioStream::getXRunCount()`**: 
  * *Oboe Full Guide (`FullGuide.md`)* documents that `getXRunCount()` queries the total number of buffer underruns or overruns (xruns) that have occurred on an open `AudioStream`. It can be safely called on an active stream or inside error callbacks like `onErrorBeforeClose(stream, error)` before closing the stream.
* **AAudio XRun Counters**: 
  * *Google / Android NDK Docs* state that AAudio streams maintain internal xrun counters to detect missed deadlines where the hardware buffer depleted before the app supplied new PCM frames, resulting in audible clicks or dropouts.
* **Callback Timing & Non-Blocking Rules**:
  * *Oboe Full Guide* specifies that `AudioStreamDataCallback::onAudioReady()` executes on a high-priority audio thread. To prevent audio glitches, code inside `onAudioReady()` must **never** perform blocking operations, including:
    * Memory allocations (`malloc`, `new`).
    * File or network I/O.
    * Mutexes or synchronization primitives.
    * Sleeping or calling `stop()` / `close()` / `read()` / `write()` on the invoking stream.
* **`SCHED_FIFO` Scheduling**:
  * *Android Developer Documentation* establishes that low-latency audio streams (using AAudio or Oboe in `PerformanceMode::LowLatency`) assign their underlying worker processing threads to the `SCHED_FIFO` real-time scheduling class, preventing preemption by lower-priority application tasks.

#### **Synthesis & Research Briefs**
* *Performance Profiling Paper & DAW Internals*: Confirm that `Oboe::AudioStream::getXRunCount()` is the primary counter used on Android to detect real-time audio deadline failures (\\(t_{\text{buffer}} = \frac{N_{\text{samples}}}{f_s}\\), e.g., \\(\approx 5.8\text{ ms}\\) for 256 samples @ \\(44.1\text{ kHz}\\)).

---

### 2. iOS Real-Time Audio Thread Measurement & Scheduling

#### **Apple Official Documentation & Command-Line Man Pages**
* **Instruments "Audio System Trace" Template & `xctrace`**:
  * *Apple Documentation & `xctrace(1)` Man Page*: "Audio System Trace" is a stock Xcode Instruments template used to profile Core Audio thread execution, scheduling, and system overloads.
  * **Can `xctrace` record it? Yes.** The `xctrace(1)` man page and the *Performance Profiling Paper* confirm that `xctrace record` can run "Audio System Trace" headlessly on a USB-connected device via CLI:
    ```bash
    xcrun xctrace record \
      --template "Audio System Trace" \
      --device <UDID> \
      --launch -- com.example.app \
      --time-limit 15s \
      --output audio_profile.trace
    ```
* **Core Audio Overloads**:
  * *Apple Documentation & DAW Internals*: Overloads (xruns/underruns) occur when the render callback (e.g., `AURemoteIO` or `AVAudioEngine`) fails to complete within its strict frame budget (e.g., \\(\approx 2.67\text{ ms}\\) for 128 frames @ \\(48\text{ kHz}\\)). Overload events are logged by Core Audio and captured in Audio System Trace recordings.
* **`os_workgroup` & `kAudioOutputUnitProperty_OSWorkgroup`**:
  * *Apple AudioToolbox Documentation*: Core Audio exposes the kernel workgroup object (`os_workgroup_t`) attached to an active I/O Audio Unit (`AURemoteIO` / `AUHAL`).
  * Developers query `AudioUnitGetProperty` using the property selector `kAudioOutputUnitProperty_OSWorkgroup` to retrieve the `os_workgroup_t` handle.
* **`THREAD_TIME_CONSTRAINT_POLICY`**:
  * *Mach Scheduling and Thread Interfaces (Apple Kernel Programming Guide)*: Documents that real-time thread priority on Darwin/iOS is configured via `thread_policy_set` using `THREAD_TIME_CONSTRAINT_POLICY`. The `thread_time_constraint_policy_data_t` structure requires:
    * `period`: The interval between deadlines in Mach absolute time units.
    * `computation`: Estimated CPU execution time required per buffer.
    * `constraint`: Maximum allowable latency from period start to completion (`constraint` \\(\ge\\) `computation`).
    * `preemptible`: Flag (0 or 1) specifying if the thread can be preempted.

---

### 3. Insights Discovered in Community & Developer Forums

#### **Apple Developer Forums & JUCE Developer Forum**
* **The `os_workgroup_join` + `THREAD_TIME_CONSTRAINT_POLICY` Requirement**:
  * *JUCE Forum ("Os_workgroup_join consistently returns EINVAL")*: Community members discovered a critical, undocumented requirement—calling `os_workgroup_join(audioWorkgroup, &joinToken)` on a parallel worker thread will consistently return `EINVAL` (Invalid Argument) **unless the worker thread has already been promoted to real-time priority using `THREAD_TIME_CONSTRAINT_POLICY` via `thread_policy_set`**.
  * *JUCE Forum ("MacOS Audio Thread Workgroups")*: Developers documented that joining parallel worker threads to the Core Audio device `os_workgroup_t` informs the macOS/iOS scheduler that those threads share the hardware render deadline. On Apple Silicon, this prevents the OS scheduler from demoting audio worker threads to Efficiency Cores (E-cores), ensuring they remain scheduled on Performance Cores (P-cores).
  * *Token Lifetime*: Forum participants noted that the join token (`os_workgroup_join_token_s`) must be thread-local (`thread_local`) to avoid crashes when multiple worker threads join concurrently.

#### **Stack Overflow**
* *iOS / Swift Timing Discussions*: Developers demonstrate converting nanoseconds/milliseconds to Mach absolute time using `mach_timebase_info` before populating `thread_time_constraint_policy_data_t` for `thread_policy_set`.

---

### Summary Table: Official Docs vs. Forum Insights

| Feature / Topic | Official Documentation Source | Developer Forum / Community Source |
| :--- | :--- | :--- |
| **Android XRuns** | Oboe Guide (`AudioStream::getXRunCount()`) & AAudio NDK Docs | — |
| **Android Scheduling** | Android NDK Docs (`SCHED_FIFO` real-time class) | — |
| **iOS Audio System Trace** | `xctrace(1)` man page (`--template "Audio System Trace"`) | — |
| **`os_workgroup` Property** | Apple AudioToolbox API Docs (`kAudioOutputUnitProperty_OSWorkgroup`) | — |
| **Mach Real-Time Policy** | Apple Kernel Guide (`THREAD_TIME_CONSTRAINT_POLICY` fields) | Stack Overflow code implementations |
| **`os_workgroup_join` `EINVAL` Fix** | *Not documented in Apple official guides* | **JUCE Forum**: Proves `THREAD_TIME_CONSTRAINT_POLICY` must be set *before* calling `os_workgroup_join` |
| **P-Core vs. E-Core Pinning** | *Not explicitly detailed in standard docs* | **JUCE Forum**: Joining `os_workgroup` keeps audio worker threads on Apple Silicon P-cores |

💡 Would you like to review the complete C++ implementation snippet for setting `THREAD_TIME_CONSTRAINT_POLICY` and joining `os_workgroup_t` on iOS?

# q5-farm  (refs: 41)

### 1. Running Performance and Instrumentation Tests on Physical Cloud Devices

* **Android Macrobenchmark**:
  * **Supported on Firebase Test Lab (FTL)**: Benchmarks can run on physical Android devices in cloud test labs such as Firebase Test Lab [1, 2]. Running on emulators is strongly discouraged due to non-deterministic CPU throttling and software GPU artifacts [1-3].
  * **Gradle Integration**: FTL integration uses the `com.google.firebase.testlab` Gradle plugin to provision physical managed devices (e.g., `device = "shiba"`, `apiLevel = 34`) [4].
* **iOS XCTest (`XCTMetric`)**:
  * **Firebase Test Lab & AWS Device Farm Support**: FTL supports executing iOS XCTest suites on physical iOS devices using packaged `.zip` / `.xctestrun` bundles [5-7]. Similarly, AWS Device Farm supports uploading `.ipa` and `.xctest.zip` packages to run XCTests on real physical devices [8-10].
  * **CI Diagnostic Setup**: For headless execution with performance diagnostic collection (`-enablePerformanceTestsDiagnostics YES`), sources also describe using macOS GitHub self-hosted runners or local hardware pools connected to physical iPhones via USB [11-13].

---

### 2. Returned Test Artifacts & Pulling Flags

* **Android Macrobenchmark**:
  * **Artifacts Returned**:
    1. **`benchmarkData.json`** (or `*-benchmarkData.json`): Contains full execution context (brand, device model, CPU core count, CPU max frequency, total memory) along with metric statistics (minimum, maximum, median, and per-run timing arrays) [14-16].
    2. **Perfetto Traces**: Macrobenchmark generates raw Perfetto trace files (`.perfetto-trace` or `.trace`), outputting one trace file per measured iteration [15, 17].
  * **Retrieval & Pull Flags**:
    * **Gradle FTL Plugin**: Pulled automatically by specifying target device directories in the Gradle build script using `testOptions.results.directoriesToPull.addAll("/storage/emulated/0/Android/media/${android.namespace}")` [4].
    * **ADB Command Line**: Files saved under `/storage/emulated/0/Android/media/<package>/` can be retrieved manually using `adb pull` or scripted via `adb shell find /sdcard/Download -name "*-benchmarkData.json" | xargs adb pull` [15, 18].
* **iOS XCTest (`XCTMetric`)**:
  * **Artifacts Returned**:
    1. **`.xcresult` Bundles**: Xcode outputs an `.xcresult` bundle containing test session results, performance logs, baseline comparison results, and trace attachments [11, 19].
    2. **`.trace` Files**: `xctrace` headless recording produces `.trace` bundles [20].
  * **Extraction Tools & Commands**:
    * Structured metric data is extracted from `.xcresult` bundles using `xcrun xcresulttool get --format json --path PerformanceResults.xcresult` or exported to CSV via `xcresulttool export metrics --path Example.xcresult --output-path ./Out` [20-22].
    * `xctrace` trace bundles are exported to structured XML using `xcrun xctrace export --input <file>.trace --toc` (Table of Contents) and filtered with `--xpath` queries (e.g., `--xpath '/trace-toc/run[@number="1"]/data/table[@name="time-profile"]'`) [20, 23, 24].

---

### 3. Firebase Test Lab Quotas & Pricing (Spark vs. Blaze)

* **Spark Plan (No-Cost / Daily Limits)**:
  * **Total Test Runs**: Up to **15 test runs per day** total [25].
  * **Virtual Devices**: **10 test runs per day** [25].
  * **Physical Devices**: **5 test runs per day** [25].
* **Blaze Plan (Pay-As-You-Go)**:
  * **Daily No-Cost Time Allowance**:
    * **30 minutes of test time per day** on physical devices [25].
    * **60 minutes of test time per day** on virtual devices [25].
  * **Per-Device-Minute Rates (Above Allowance)**:
    * **Physical Devices**: **\$5.00 per hour** (approx. **\$0.0833 per minute**) [25].
    * **Virtual Devices**: **\$1.00 per hour** (approx. **\$0.0167 per minute**) [25].
  * **Billing Rules**: Charges are calculated per minute of active test time (excluding app installation and result collection time), rounded up to the nearest whole minute [26].

---

### 4. iPhone Models Listed Across the Sources

The following iPhone models are explicitly named in the sources:
* **iPhone 17 Pro** (A19 chip) [27, 28]
* **iPhone 17** [29, 30]
* **iPhone 16** [29, 30]
* **iPhone 13 Pro** [31, 32]
* **iPhone 13 mini** [33]
* **iPhone 13** [34]
* **iPhone X** [34, 35]
* **iPhone 7 Plus** [35]

---

### 5. Marathon Test Runner & iOS Mac Worker Fan-Out

* **Overview & Core Capabilities**: Marathon is an open-source, platform-independent test runner designed for parallel test execution, flakiness reduction, and execution stability [36, 37]. It provides test batching, device pooling, test sharding, test sorting (running longer tests first), preventive retries (queuing retries for tests statistically expected to fail), and post-factum retry quotas [37-39].
* **Fanning Out iOS Tests Across Macs**:
  * **`Marathondevices` Configuration**: Since iOS does not support automatic remote device discovery, device topologies are declared in a `Marathondevices` configuration file [33, 40].
  * **SSH Transport & Remote Workers**: Workers are defined under the `workers` key using `transport: type: local` for local simulators or remote macOS worker nodes over **SSH** (`transport: type: ssh`) [33].
  * **Cross-Platform Host Execution**: The machine running Marathon does not need to be a Mac; a **Linux host or CI runner** can execute Marathon and fan out iOS UI/unit tests across multiple remote macOS instances connected over SSH [41].

💡 Would you like to review a sample `Marathonfile` and `Marathondevices` configuration for running sharded iOS test suites across remote Mac runners?

# q6-critique  (refs: 0)

Adding an iOS collector to **`trace-pulse`** while retaining the core SQLite schema (`data/tracepulse.db`), Skia step-fitting engine (`detect.py`), Mann-Whitney U / Benjamini-Hochberg (MW/BH) confirmation, and self-contained HTML reporting (`report.py`) is architecturally sound. Because `trace-pulse`'s analysis layer operates purely on generic time-series tables (`samples` and `aggregates`), the detection algorithms are platform-agnostic.

However, bridging Android's `Macrobenchmark` + `Perfetto` baseline with Apple's `XCTest` + `xctrace` stack introduces specific protocol and measurement mismatches. Here is an honest critique of the proposed plan based on `SPEC.md`, `synthesis.md`, and the Apple/Android performance sources.

---

### 1. Iteration Count & `XCTMeasureOptions`

* **`trace-pulse` Standard**: `trace-pulse` specifies **20 iterations per benchmark** (`collection.iterations: 20` in `SPEC.md` and `synthesis.md`).
* **XCTest Behavior**: The default iteration count for XCTest `measure()` blocks is **10 iterations**. Running only 10 iterations would cut the per-round sample size in half (from 20 to 10 per round).
* **Statistical Impact**: `synthesis.md` notes that 2-round pooling for Mann-Whitney confirmation requires \\(20 \text{ iters/round} \times 2 = 40\text{v}40\\) samples to ensure the theoretical minimum \\(p\\)-value can clear the Benjamini-Hochberg \\(q=0.05\\) threshold across the 8-series metric family. A 10-iteration default (\\(20\text{v}20\\)) would degrade statistical power and risk failing `trace-pulse`'s guardrail unit test (which asserts that configured sample sizes can reach corrected \\(\alpha\\)).
* **Fix / Alignment**: `XCTMeasureOptions` allows configuring `iterationCount` in Swift code (e.g., `options.iterationCount = 20`), or `-test-iterations 20` can be passed to `xcodebuild test`. To preserve statistical parity with Android, the iOS collector **must explicitly enforce 20 iterations** via `XCTMeasureOptions` or `xcodebuild` flags rather than relying on XCTest defaults.

---

### 2. Raw Per-Iteration Extraction for Mann-Whitney Confirmation

* **`trace-pulse` Requirement**: As established in `SPEC.md` §9 and `synthesis.md` §2.1, 5v5 round-median Mann-Whitney tests are mathematically uncorrectable (minimum two-sided \\(p = \frac{2}{252} \approx 0.0079\\), which exceeds the BH floor at \\(m=9\\)). Thus, confirmation **must operate on raw per-iteration values** (\\(40\text{v}40\\) across 2 rounds per side), which are stored in the `samples(round_id, benchmark, metric, iteration, value)` table.
* **XCTest / `xcresulttool` Capabilities**:
  * `xcresulttool export metrics --path Performance.xcresult --output-path ./Out` exports performance measurements into a CSV file.
  * `xcrun xcresulttool get --format json --path Performance.xcresult` extracts structured JSON from the result bundle.
* **What the Sources Leave Open**: The sources confirm that `xcresulttool export metrics` produces CSV measurement exports, but **do NOT establish** whether raw, per-iteration arrays are directly included in the `xcresulttool get --format json` schema or if per-iteration extraction strictly requires parsing the exported CSV files. The iOS collector script must be prepared to parse `xcresulttool export metrics` CSVs to populate `samples`.

---

### 3. Device Protocol Alignment & Variance Control

`SPEC.md` §12 outlines a strict preflight protocol enforced by `tracepulse doctor` on unrooted hardware. Here is how those controls map to physical iOS devices:

| Protocol Requirement | Android (`tracepulse doctor`) | iOS Adaptation | Sources & Platform Fact |
| :--- | :--- | :--- | :--- |
| **Thermal Gate** | Battery temp \\(\le 35.0^\circ\text{C}\\) (pre-round gate) | `ProcessInfo.processInfo.thermalState` | `ProcessInfo.thermalState` returns `.nominal`, `.fair`, `.serious`, or `.critical`. Preflight must assert `.nominal` before running a round; `.serious`/`.critical` triggers a `THERMAL` badge or round abort. |
| **Refresh-Rate Pinning** | Display refresh pinned to 60 Hz | `Info.plist` setting | ProMotion displays (\\(120\text{ Hz}\\)) switch dynamically. `Info.plist` must set `CADisableMinimumFrameDurationOnPhone = true` to allow high refresh rates, or lock the refresh rate via display/UI settings so adaptive switching does not fake step changes in frame metrics. |
| **Network Isolation** | Airplane mode **ON** | Airplane mode **ON** | Supported on physical USB-connected iPhones via iOS Settings/CLI protocols. |
| **Battery Gate** | Battery \\(\ge 50\%\\) | Battery \\(\ge 50\%\\) | Queried on iOS via `UIDevice.current.batteryLevel` or system power utilities. |
| **Warm-Up Launch** | Fresh uninstall + reinstall + 1 unmeasured warm-up launch | Fresh reinstall + 1 unmeasured launch | Prevents first-iteration bimodality (e.g., initial disk cache creation or OS authorization prompts). |

---

### 4. Mapping `trace-pulse` Metrics to iOS (`XCTMetric`)

| `trace-pulse` Metric Key | Android Source | iOS Metric Equivalent | Mapping Notes & Mismatches |
| :--- | :--- | :--- | :--- |
| **`startup.ttid_ms`** | `StartupTimingMetric` (`timeToInitialDisplayMs`) | `XCTApplicationLaunchMetric(waitUntilResponsive: false)` | Measures launch duration from process execution to first frame rendering. |
| **`startup.ttfd_ms`** | `StartupTimingMetric` (`timeToFullDisplayMs`) | `XCTApplicationLaunchMetric(waitUntilResponsive: true)` | Measures launch duration until the application becomes fully responsive. |
| **`frames.p50_ms`**<br>**`frames.p90_ms`**<br>**`frames.p99_ms`** | `FrameTimingMetric` (`frameDurationCpuMs` P50/P90/P99) | `XCTOSSignpostMetric` interval duration | `XCTOSSignpostMetric` measures durations of custom signpost intervals (e.g. `scrollingAndDecelerationMetric`). |
| **`frames.overrun_p90_ms`** | `FrameTimingMetric` (`frameOverrunMs.P90`) | **`XCTHitchMetric`** | **Unit Mismatch**: Android `frameOverrunMs` measures buffer deadline overruns in milliseconds (\\(\text{ms}\\)). iOS `XCTHitchMetric` measures UI hitch density in hitch duration per second of animation (\\(\text{ms/s}\\)). While both capture UI stutter, the screening gate `min_delta_abs: 2.0 ms` must be adapted for \\(\text{ms/s}\\) units on iOS. |
| **`memory.rss_anon_max_kb`** | `MemoryUsageMetric(Max, RssAnon)` | **`XCTMemoryMetric`** (`physical_memory`) | Tracks physical memory footprint, peak footprint, and heap allocations. |
| **`memory.heap_last_kb`** | `MemoryUsageMetric(Last, HeapSize)` | **`XCTMemoryMetric`** (heap allocations) | Measures resident/heap memory at completion of the benchmark journey. |
| **`apk.size_bytes`** | `demoRelease` APK file size | `.app` bundle / `.ipa` size | Measures main binary + resource bundle size in bytes. |

---

### 5. Role of Xcode Baselines (`.xcbaselines`) vs. `trace-pulse` Trend Detector

* **`.xcbaselines` as a Coarse Gate**: `.xcbaselines` property lists reside in the test bundle. When `xcodebuild test` runs, XCTest compares measured values against baseline averages and triggers an immediate test failure if performance degrades past stored limits. This can serve as a **local, single-run coarse gate** that halts execution when a massive regression occurs.
* **Why `.xcbaselines` Cannot Replace `detect.py`**:
  1. `.xcbaselines` checks single runs in isolation (\\(N\text{ vs }N-1\\)), whereas `SPEC.md` §3.1 emphasizes that single-run comparisons are noisy and prone to false positives.
  2. `.xcbaselines` lacks step-fitting across multi-round history (Skia step-fit radius window \\(W=5\\), \\(11\\) round-medians).
  3. `.xcbaselines` does not perform multi-testing false-positive control (Benjamini-Hochberg \\(q=0.05\\) across metric families) or environment spike suppression (`ENVIRONMENT-SUSPECT`).
* **Integrated Architecture**: Use `.xcbaselines` as an optional coarse build-breaker, while feeding raw `.xcresult` / `xctrace` artifacts into `tracepulse store` and `detect.py` for multi-round step detection, onset attribution, and HTML reporting.

---

### 6. Honest Summary of Mismatches & What Sources Leave Open

1. **`XCTHitchMetric` vs. `frameOverrunMs` Unit Parity**: Android reports overrun in milliseconds (\\(\text{ms}\\)), whereas iOS reports hitch density in \\(\text{ms/s}\\). The `tracepulse.yml` screening gates (`min_delta_abs: 2.0`) cannot be applied blindly to `XCTHitchMetric` without recalibrating unit-specific floors.
2. **Per-Iteration Raw Extraction Schema**: The sources do not establish whether `xcresulttool get --format json` includes raw per-iteration measurement arrays, leaving open whether the iOS collector must rely on `xcresulttool export metrics` (CSV) to populate `samples`.
3. **`.xcbaselines` Internal Schema**: The sources do not disclose the exact plist key structure or default standard deviation/percentage threshold formulas inside `.xcbaselines`, leaving open how tightly XCTest's coarse gate can be configured via CLI versus `tracepulse.yml`.
4. **Default Iteration Count**: Sources document that custom iteration counts can be set via `-test-iterations` or `XCTMeasureOptions`, but do not state the exact default iteration count if omitted (typically 10 on iOS vs. `trace-pulse`'s required 20).

💡 Would you like to draft the `iosMain` `PlatformTracer` C-shim implementation for capturing custom `os_signpost` intervals on iOS?

# q7-field  (refs: 31)

For a mobile Digital Audio Workstation (DAW) application—where real-time audio thread deadlines and smooth UI rendering are critical—**Apple’s MetricKit** and **Android Vitals / JankStats** provide complementary field data telemetry [1-3]. Below is a detailed comparison based on your sources covering reported metrics, delivery cadence, build environment availability, and platform limitations.

---

### 1. What Each Tool Reports

#### **Apple MetricKit**
MetricKit provides on-device telemetry and operating system diagnostics collected from real user devices [4]. It is split into two primary payload types:

* **Daily Metrics (`MXMetricPayload` / `MetricReport`)**:
  * **Launch & Responsiveness**: Time to first draw (`TimeToFirstDrawMetric`, `OptimizedTimeToFirstDrawMetric`), application resume time (`ApplicationResumeTimeMetric`), extended launch durations (`ExtendedLaunchMetric`), hang time (`HangTimeMetric`), and animation hitch ratio (`HitchTimeMetric` / `HitchTimeRatio`) [5-7].
  * **CPU & Memory**: Total CPU time (`CPUTimeMetric`), CPU instruction counts (`CPUInstructionsCountMetric`), peak memory footprint (`PeakMemoryMetric`), and average suspended memory footprint (`SuspendedMemoryMetric`) [6, 8].
  * **GPU & Display**: GPU execution time (`GPUTimeMetric`), Metal layer frame rate statistics (`MetalFrameRateMetric`), and OLED average pixel luminosity (`PixelLuminanceMetric`) [8, 9].
  * **Disk & Network**: Logical data written to disk (`LogicalDiskWritesMetric`), total disk capacity and file count/size attributed to the app [10], and WiFi/cellular upload/download volumes [9, 10].
  * **Terminations & Custom Signposts**: Counts of foreground and background terminations by reason (`ForegroundTerminationMetric`, `BackgroundTerminationMetric`) [11], and custom code region durations marked via `mxSignpost` or `MXSignpostIntervalMetric` [11].

* **Immediate Diagnostics (`MXDiagnosticPayload` / `DiagnosticReport`)**:
  * **Main-Thread Hangs (`MXHangDiagnostic`)**: Captured when the app is too busy to handle user input responsively [12, 13].
  * **CPU Exceptions (`MXCPUExceptionDiagnostic`)**: Triggered when the app exceeds CPU resource usage limits [6, 12].
  * **Disk Write Exceptions (`MXDiskWriteExceptionDiagnostic`)**: Triggered when the app executes excessive logical disk writes [10, 14].
  * **Crashes (`MXCrashDiagnostic`)**: Stack trace trees (`CallStackTree`) captured during unhandled crashes or fatal terminations [12, 13, 15].

#### **Android Vitals & JankStats**
Android splits field monitoring between Google Play server-side telemetry (Android Vitals) and an in-app Jetpack library (JankStats):

* **Android Vitals (Google Play Console Telemetry)**:
  * **Stability**: Crash rate, Application Not Responding (ANR) rate, and Low Memory Killer (LMK) terminations [16, 17].
  * **Rendering & Responsiveness**: Slow rendering (percentage of daily active users experiencing >16 ms or >33 ms frame delays), frozen frames (frames taking >700 ms), and slow app launch times [16-18].
  * **Resource & Battery Health**: Stuck partial wake locks, excessive background Wi-Fi scans/network usage, and DEX code optimization state [16, 17].

* **JankStats (In-App Jetpack Library)**:
  * **Per-Frame UI Timing**: Tracks individual frame render durations and flags visual jank on the client device in real time [1, 17].
  * **UI State Context**: Allows developers to annotate frame metrics with active UI state (e.g., scroll state, screen name) using `PerformanceMetricsState` to correlate rendering delays with specific application paths [1, 17].

---

### 2. Delivery Cadence and Data Aggregation

| Dimension | Apple MetricKit | Android Vitals | Android JankStats |
| :--- | :--- | :--- | :--- |
| **Delivery Cadence** | **Daily / Event-Driven**: Daily metrics (`MXMetricPayload`) are aggregated on-device and delivered once per 24 hours [4]. Diagnostic payloads (`MXDiagnosticPayload`) arrive immediately when an event (hang, crash, CPU spike) occurs [4]. | **Daily Aggregated**: Google Play aggregates telemetry server-side from opted-in production devices and displays daily/rolling metrics on the Play Console [17]. | **Real-Time On-Device**: Listens to frame render times on every display frame in client memory [1, 17]. |
| **Aggregation Level** | **On-Device Histograms**: Aggregates 24-hour usage into statistical histograms and average bucketing [7]. *(Note: In recent iOS releases, the Swift-first `MetricManager` delivers `MetricReport` values via async sequences alongside `StateReporting` [19-21]).* | **Population Percentiles**: Aggregates field data across device models into population-level benchmarks and "bad behavior" thresholds [17]. | **Raw Per-Frame Events**: Measures exact frame durations in milliseconds on the device, allowing custom in-app logging or immediate upload to your own backend [1, 17]. |

---

### 3. Availability Across Environments

* **Apple MetricKit**:
  * **Supported Builds**: Active in **App Store** release builds, **TestFlight** beta releases, and **Development** builds [3, 4, 22].
  * **Development Testing**: Xcode provides a debug command (`Debug > Simulate MetricKit Payloads`) to trigger test metric payloads locally without waiting 24 hours [23].
* **Android Vitals**:
  * **Supported Builds**: Restricted to **Production (Google Play)** and open/closed testing tracks published through the Google Play Console [17].
  * **Requirements**: Requires devices running Google Play services with user telemetry opt-in [17, 24].
* **Android JankStats**:
  * **Supported Builds**: Included directly as a library dependency; available across **Development**, **Local Debug/Profileable**, **Beta/Internal**, and **Production App Store/Play** builds [1, 17].

---

### 4. Field Telemetry Limits for a Mobile DAW App

While MetricKit, Android Vitals, and JankStats excel at general app health, a mobile DAW has unique real-time engine constraints that highlight specific limits in these field tools:

1. **No Real-Time C++ Audio Thread Visibility**:
   * **Real-Time Audio Deadlines**: DAW audio callbacks run under strict buffer time budgets (e.g., \\(\approx 2.67\text{ ms}\\) for 128 frames @ \\(48\text{ kHz}\\) on iOS or low-latency burst callbacks on Android) [25].
   * **Telemetry Gap**: Neither MetricKit, Android Vitals, nor JankStats monitors C++ audio callback execution or buffer underruns (xruns) [4, 17, 26]. Detecting audio glitches in the field requires platform-specific native counters (e.g., querying `Oboe::AudioStream::getXRunCount()` on Android [26] or logging Core Audio overload flags / `kAudioOutputUnitProperty_OSWorkgroup` on iOS [26]).

2. **UI Frame vs. Audio Thread Confusion**:
   * JankStats and MetricKit's `HitchTimeMetric` measure **UI frame rendering and display composition** (e.g., `CAMetalLayer` or Compose `RenderThread`) [2, 8, 27]. A smooth \\(120\text{ Hz}\\) UI animation reported by JankStats or MetricKit does **not** guarantee that the underlying real-time audio thread is free of glitches [25, 27, 28].

3. **Daily Aggregation Masking Short Audio Sessions**:
   * MetricKit’s 24-hour daily report (`MXMetricPayload`) combines multiple launches and sessions into a single histogram [4, 29]. Dissecting whether a high peak memory footprint (`PeakMemoryMetric`) or hang occurred during active multitrack playback versus idle UI navigation requires adopting Apple's `StateReporting` framework or custom `mxSignpost` intervals [11, 19, 21].

4. **Telemetry Overhead in Real-Time Loops**:
   * Invoking field reporting or telemetry APIs directly inside the high-priority audio thread triggers thread starvation [25, 30]. Field metrics must be bridged out of C++ rendering loops using non-blocking single-producer single-consumer (SPSC) lock-free ring buffers or atomic flags [30, 31].

---

💡 **Next Steps**: Would you like to explore how to implement lock-free SPSC queues to bridge C++ audio thread xrun counts safely to the UI for custom telemetry uploads?

# q8-maestro  (refs: 14)

Based on the **Maestro documentation** and related profiling sources in the notebook, here is a breakdown of what the sources establish regarding Maestro, its ecosystem, and its interaction with trace recorders.

---

### 1. Overview & Core Purpose
* **What Maestro Is**: Maestro is documented as a framework for mobile and web UI automation that uses declarative YAML flows to simulate real human interactions [1, 2].
* **Control & Scripting**: Flows are architecture-agnostic and support advanced control flow, including nested flows, loops, conditional logic, hooks, and JavaScript for data manipulation and external API calls [2, 3].

---

### 2. Supported Platforms (iOS Simulators/Devices, Android Emulators/Devices)
* **What the Sources Say**: The main index page of the Maestro documentation lists a subpage titled **"Supported platforms"** under its *Get Started* navigation structure [4].
* **What the Sources Do NOT Cover**: The provided excerpt of the Maestro documentation is an index/overview and **does not contain the full text of the "Supported platforms" page** [1, 4]. It does not detail specific OS version requirements, simulator vs. physical iPhone capabilities, or Android emulator vs. physical device constraints for Maestro flows.

---

### 3. CI Execution & Cloud Options
* **Maestro CLI in CI**: Maestro flows can be executed via terminal commands using the **Maestro CLI** and configured globally via `config.yaml` within a repository workspace [2, 3].
* **Maestro Cloud**: The sources document **Maestro Cloud** as a service to scale testing by integrating with CI platforms (such as GitHub Actions) to run tests in parallel [2].
* **What the Sources Do NOT Cover**: The documentation excerpts do **not** mention any successor product name or rebrand for Maestro Cloud, nor do they detail Cloud runner device specifications or pricing tiers.

---

### 4. Recording & Visual Test Creation
* **Maestro Studio**: Maestro provides **Maestro Studio**, a desktop application for visually creating tests with zero IDE setup and instant device connection [2].
* **What the Sources Do NOT Cover**: The documentation index does not provide step-by-step instructions or CLI flags for video screen recording during flow execution.

---

### 5. Performance Measurement and Timing Features
* **Functional Automation Focus**: Maestro is documented purely as an end-to-end functional UI testing framework [1, 2].
* **No Native Metric Engine Documented**: The Maestro documentation excerpts contain **no performance-measurement features, frame timing metrics, or CPU/memory telemetry APIs** [1, 3]. In contrast, performance benchmarking is covered by dedicated platform tools such as Android **Jetpack Macrobenchmark** (`StartupTimingMetric`, `FrameTimingMetric`) [5, 6] and iOS **XCTest** (`XCTMetric`, `XCTHitchMetric`, `XCTCPUMetric`) [7, 8].

---

### 6. Driving an App with Maestro While an External Recorder Captures a Trace

While the Maestro documentation does not explicitly detail external profiler integration, the CLI documentation for **`xctrace`** and **`perfetto`** demonstrates that this workflow is technically feasible:

* **iOS (`xctrace`)**:
  * `xctrace record` supports attaching to an already running process using `--attach <pid | name>` or profiling all system processes using `--all-processes` on a targeted `--device` [9, 10].
  * *Workflow*: A build pipeline can launch the iOS application, trigger `xcrun xctrace record --attach <app_name> --template "Time Profiler" --output trace.trace` in a background process, execute the UI journey via Maestro CLI (`maestro test flow.yaml`), and terminate `xctrace` when the flow completes.
* **Android (`perfetto` CLI)**:
  * Android's `perfetto` CLI tool and helper scripts (`record_android_trace` / `cpu_profile`) run as out-of-process daemons via ADB or UNIX socket IPC [11-13].
  * *Workflow*: A script can start `python3 record_android_trace` or `adb shell perfetto`, execute `maestro test flow.yaml` to drive the Android app UI, and pull the resulting `.perfetto-trace` file upon completion [11, 14].

---

### Summary of Gaps (What the Sources Do NOT Cover)
1. **Device Support Matrix**: The full text for Maestro's *Supported Platforms* page (detailing physical iPhone vs. simulator constraints or Android ADB requirements) is missing from the source index [4].
2. **Maestro Cloud Successor**: No successor product or alternative cloud service name is listed beyond Maestro Cloud [2].
3. **Built-in Performance Instrumentation**: Maestro has no documented APIs for capturing performance or timing metrics during UI flows [1, 3].
4. **Official Combined Tracing Recipes**: No official documentation or sample scripts are provided for orchestrating Maestro flows alongside `xctrace` or `perfetto` CLI tracing.

💡 Would you like to explore writing a bash or Python wrapper script to coordinate `xctrace` or `perfetto` background recording alongside a Maestro CLI test run?