# Contract: engine audio-thread health (C ABI + Kotlin telemetry)

Defined now, filled by the audio spine. Zeros with the current engine.

## C ABI (`engine/include/tembra_engine.h`, revision bump)

```c
typedef struct tembra_audio_health {
    uint64_t callbacks;     /* total render callbacks since stream start            */
    uint32_t last_us;       /* duration of the most recent callback, microseconds   */
    uint32_t max_us;        /* maximum callback duration since stream start         */
    uint32_t over_budget;   /* callbacks whose duration exceeded the buffer period  */
    uint32_t xruns;         /* underruns + overruns reported by the platform stream */
    uint8_t  realtime;      /* 1 when the audio thread holds real-time status       */
    uint8_t  _reserved[3];
} tembra_audio_health;

/* Non-real-time. Copies the atomics into *out. Returns 0, or -1 when no stream is open. */
int tembra_engine_audio_health(const tembra_engine* engine, tembra_audio_health* out);
```

Rules (Principle I): the callback updates `callbacks`, `last_us`, `max_us`, `over_budget` with relaxed atomic stores from a monotonic clock read at entry and exit (`clock_gettime(CLOCK_MONOTONIC)` / `mach_absolute_time`, both allocation-free and non-blocking); `xruns` is written by the drain thread on Android after `AudioStream::getXRunCount()` (never called on the callback, per Oboe's guidance) and by the engine's own detection on iOS (no public counter); `realtime` is set once at stream start (Oboe `PerformanceMode::LowLatency` granted; iOS `os_workgroup_join` result for worker threads, the I/O thread itself is real-time by construction).

## Kotlin (`:shared:engine`, commonMain)

```kotlin
data class AudioHealth(
    val callbacks: Long, val lastMicros: Int, val maxMicros: Int,
    val overBudget: Int, val xruns: Int, val realtime: Boolean,
)
// AudioEngine (expect) gains: fun audioHealth(): AudioHealth?   — null when no stream is open; non-RT; reads atomics through the C ABI
// FakeAudioEngine gains: fun scriptAudioHealth(health: AudioHealth)
```

Read on the telemetry drain (the frame loop that already exists for meters), never through `StateFlow` (Principle II). The drain publishes the values through `PerfTrace.counter(PerfCounter.AUDIO_*, value)` once per second in measurement builds.

## Pipeline mapping

| Series | Android source | iOS source |
|---|---|---|
| `audio.callbacks` | counter track `audio.callbacks` (last value) | `engine-health.json` |
| `audio.last_callback_us` / `audio.max_callback_us` | counter tracks | `engine-health.json` |
| `audio.over_budget` | counter track | `engine-health.json` |
| `audio.xruns` | counter track | `engine-health.json` |
| `audio.realtime` | counter track (0/1) | `engine-health.json` |
| `audio.ioproc_p99_us` (advisory) | — | `xctrace` Audio System Trace, table `audio-hal-client-io-cycle-ioproc`, p99 of `duration` during the stress journey |

`engine-health.json`: written by the measurement build at the end of the stress journey into the app's Documents directory (`{"schema":1,"journey":"stress","health":{…}}`), triggered by the journey's last step (a debug-only action reachable only when `PERF_ENABLED`); pulled with `xcrun devicectl device copy from --device <udid> --source Documents/engine-health.json --domain-type appDataContainer --domain-identifier <bundle> --destination <round-dir>/` (simulator: `xcrun simctl get_app_container <udid> <bundle> data`).

## Tests

- Catch2: the health struct round-trips through the ABI; a scripted callback of known duration produces `last_us`/`max_us`/`over_budget` as expected; the poison test from `tracing-facade.md` covers the callback path.
- `commonTest`: `FakeAudioEngine.scriptAudioHealth` reaches the drain and the drain emits six counters with the scripted values (recorded by the `PerfTraceSink`).
- Python: fixture Perfetto trace with the six counter tracks → six `audio.*` series; fixture `engine-health.json` → the same; SC-010.
