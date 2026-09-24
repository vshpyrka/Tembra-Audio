# Contract: `AudioEngine` facade (scaffold subset)

**Module**: `:shared:engine`, package `audio.tembra.engine` | **Consumers**: `:shared:ui` ViewModels via DI; tests via `FakeAudioEngine`

```kotlin
// AudioEngine.kt — the only way Kotlin talks to the native engine.
interface AudioEngine {
    fun describe(): EngineDescriptor
}

// EngineDescriptor.kt — see data-model.md §3
data class EngineDescriptor(
    val name: String,
    val version: SemanticVersion,
    val compiledLibraries: List<String>,
    val rawDescription: String,
)

// NativeAudioEngine.kt (commonMain): expect class with platform actuals.
expect class NativeAudioEngine() : AudioEngine

// FakeAudioEngine.kt (commonMain, published for tests of other modules):
class FakeAudioEngine(
    private val descriptor: EngineDescriptor = EngineDescriptor(
        name = "Tembra Engine", version = SemanticVersion(0, 0, 0),
        compiledLibraries = emptyList(), rawDescription = "Tembra Engine 0.0.0 ()",
    ),
) : AudioEngine {
    var describeCalls: Int = 0; private set
    override fun describe(): EngineDescriptor { describeCalls++; return descriptor }
}

// EngineDescriptorParser.kt (commonMain, internal): parses the C ABI describe string.
internal object EngineDescriptorParser {
    fun parse(raw: String): EngineDescriptor   // throws IllegalArgumentException on malformed input
}
```

**Rules**
- `describe()` completes in under 1 ms, allocates only the returned strings, is thread-safe
  and may be called from any non-real-time thread.
- `NativeAudioEngine` performs no I/O, opens no audio device and requests no permission.
- `expect`/`actual` is used only here (Constitution II); everything else depends on the
  `AudioEngine` interface.
- The Android actual loads `tembra_engine` in a companion `init`; the iOS actual calls the
  cinterop functions directly.

**Tests**
- `commonTest`: `EngineDescriptorParserTest` (valid string, missing parenthesis, bad version),
  `FakeAudioEngineTest` (records calls, returns configured descriptor). Run on the Android host
  (JVM) and on the iOS simulator.
- `androidDeviceTest` and `iosTest`: `NativeAudioEngineTest` asserts `describe().name ==
  "Tembra Engine"`, `version == SemanticVersion(0, 1, 0)`, and that `compiledLibraries`
  contains `miniaudio`, `dr_wav` and `signalsmith-stretch` (plus `oboe` on Android). These
  source sets are the only Kotlin tests that link the real engine.
