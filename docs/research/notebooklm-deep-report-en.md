# Technical Blueprint: Free, Open-Source Cross-Platform Mobile DAW (Android + iOS)

This document establishes the architectural requirements and implementation mandates for a professional-grade, low-latency Digital Audio Workstation (DAW) targeting mobile platforms. As Lead Architect, I require strict adherence to the threading and memory management protocols outlined below to ensure sample-accurate performance and hardware-level stability.

---

## 1. Cross-Platform Architectural Framework

To balance code maintainability with high-performance requirements, this DAW utilizes a hybrid architecture. **Kotlin Multiplatform (KMP)** manages the "Musical Reasoning" layer, while platform-specific Native Engines handle the Digital Signal Processing (DSP) and Hardware Abstraction Layer (HAL).

### Shared vs. Native Responsibilities
*   **KMP Layer (Shared):** Owns the `TempoMap`, the logic for converting frames to musical beats, the `SongPosition` state, and the `BeatEvent` data structures.
*   **Native Layer (Android C++ / iOS Swift):** Strictly reserved for real-time buffer delivery, hardware interaction, and the hot-path render loop.

### Library Recommendations and Licensing

| Library | Purpose | License / Safety |
| :--- | :--- | :--- |
| **Oboe (Android)** | High-performance C++ wrapper for AAudio. Mandates `SharingMode::Exclusive` for lowest latency. | Apache 2.0 |
| **AVAudioEngine (iOS)** | Native system framework. Use `AVAudioSourceNode` for the main processing path. | Proprietary (Standard OSS) |
| **libPD (Cross-Platform)** | Embeddable Pure Data engine for modular musical logic. | Standard BSD |
| **LockFreeQueue** | Single-reader, single-writer queue for cross-thread synchronization. | Standard BSD/MIT |

### Non-Negotiable Rules for the Audio Thread
The audio callback operates under a hard real-time deadline. Any breach of these rules will cause audible dropouts (underruns).
1.  **No Memory Allocation:** Prohibit `malloc`, `new`, or any object instantiation.
2.  **No Locking:** Mutexes and semaphores are strictly forbidden due to priority inversion risks.
3.  **No I/O:** Disk and network access must be delegated to background threads.
4.  **No Objective-C / Standard Swift Objects:** On iOS, callbacks must avoid Swift’s Automatic Reference Counting (ARC). Atomic increments/decrements in ARC trigger implicit locks.
5.  **No Bounds Checks:** Utilize `UnsafeMutableAudioBufferListPointer` in Swift to bypass uniqueness and bounds checks.

---

## 2. Professional Audio Processing and Timeline Management

### The Engine Lifecycle
We utilize a unified callback-driven model. On Android, Oboe’s `onAudioReady` requires the stream to be opened in `PerformanceMode::LowLatency` and `SharingMode::Exclusive` to bypass the system mixer. On iOS, we avoid "Taps" for processing (as they have unpredictable buffer sizes) and instead use `AVAudioSourceNode` for the main signal path.

### Sample-Accurate Scheduling
To prevent jitter and clock drift, the timeline is managed by counting elapsed audio frames rather than relying on system timers.
*   **Drift-Free Position Calculation:**
    $$mSongPositionMs = \frac{mCurrentFrame \times 1000}{sampleRate}$$
*   **Sample Rate Independence:** To avoid the "Donald Duck effect" (pitch shifting caused by hardware/session mismatches), implement a non-destructive resampling pipeline (e.g., using Oboe’s resampler or a custom C++ sinc interpolator) to ensure content sounds correct regardless of the hardware’s native rate (44.1kHz vs 48kHz).

### The Mixer and Automation
The `Mixer` class iterates through `Player` tracks, rendering single frames and summing them into the `audioData` buffer. Automation must be updated frame-by-frame within the render loop to prevent "zipper noise" during parameter changes.

---

## 3. Guitar and Cello Tuner Implementation

### Algorithm: McLeod Pitch Method (MPM)
For professional accuracy, we utilize MPM over standard autocorrelation.

| Method | Characteristics | Application |
| :--- | :--- | :--- |
| **MPM (McLeod)** | Uses **NSDF** and parabolic interpolation for sub-bin accuracy. | Superior for musical instruments. |
| **YIN** | Robust against noise but computationally expensive. | General pitch tracking. |
| **HPS** | Identifies peaks in the frequency domain. | High-harmonic content. |

**NSDF Normalization:** The **Normalized Square Difference Function (NSDF)** is critical. Unlike standard autocorrelation, NSDF normalizes the signal, preventing the "amplitude drop-off" errors that occur as the lag increases, ensuring stable detection of the fundamental frequency.

### Low-Frequency Handling (~65 Hz)
Capturing the fundamental of a Cello (C2 ≈ 65.4 Hz) requires:
*   **Recommended Sample Rate:** 48kHz.
*   **Window Size:** Minimum 2048 to 4096 samples to capture at least two full periods of a 65Hz wavelength.

### Pitch-to-Note Formulas
*   **MIDI Note ($n$):** $12 \times \log_2(f / 440) + 69$
*   **Cents:** Offset calculated via $100 \times \text{fractional remainder of } n$.

---

## 4. High-Precision Metronome Engine

### Buffer-Ahead Scheduling
We reject the use of `completionHandler` in `AVAudioPlayerNode` for timing; it is often called too late to re-schedule the next beat. Instead, we implement a **Look-Ahead** system.
1.  The UI thread enqueues beat events into a `LockFreeQueue`.
2.  The Audio thread "peeks" at the queue during the render callback.
3.  **Mandate:** Schedule the *next* beat while the current buffer is still being processed to ensure gapless, sample-accurate transitions.

### Audio Triggering
PCM data for "Accents" (Beat 1) and "Normal Beats" (2, 3, 4) must be pre-loaded into memory as raw floats. The engine triggers the appropriate buffer based on the `beatIndex` provided by the KMP Tempo Map.

---

## 5. Cross-Thread Communication and UI Synchronization

### The Three-Thread Model
1.  **Audio Thread:** DSP, frame counting, and event consumption.
2.  **Main/Logic Thread:** State management and stream lifecycle.
3.  **UI Thread:** Rendering and user input handling.

### Visual Feedback Sync (The UI Event Queue)
To synchronize a screen flash with a beat, the audio thread pushes a `BeatEvent` into a lock-free queue.
*   **Android:** The UI thread polls the queue during the frame tick.
*   **iOS:** To respect the main thread's loop, use a `DispatchQueue.main.async` or an `NSOperationQueue` to pop events and update the view. This ensures the visual "flash" aligns with the hardware's audio output.

---

## 6. Architectural Mandates

*   **Sharing Mode:** Android streams **must** use `SharingMode::Exclusive` to achieve professional-grade latencies.
*   **Resampling:** Mandate a non-destructive resampling pipeline (reference: Ardour 7.3) to handle hardware/session rate mismatches without pitch shift.
*   **Safety Bypass:** Use raw C++ pointers or `Unsafe` Swift pointers in the `onAudioReady` hot-path to eliminate bounds-check overhead.
*   **Lifecycle Management:** Monitor `Result::ErrorDisconnected` (Android) or `onErrorAfterClose` (iOS) to automatically reset the engine when the hardware configuration changes (e.g., plugging in headphones).
*   **Analysis vs. Path:** Use `AVAudioSinkNode` or custom units for analysis. Reserve `AVAudioSourceNode` for the main audio generation path to maintain deterministic latency.