# Data Model: Metronome (feature 004)

**Spec**: [spec.md](spec.md) | **Research**: [research.md](research.md) | **Contracts**: [contracts/](contracts/)

All Kotlin types are immutable unless marked; every type lives in its own file (Constitution
VII). Values are stored in beats and ticks, never in samples (Constitution V); the engine
converts through the tempo at render time.

## 1. Tempo, markings, time signature and subdivision (`:shared:core`, `audio.tembra.core.metronome`)

```kotlin
@JvmInline value class Tempo(val bpm: Int) {                     // 40..240, whole numbers (spec FR-002)
    init { require(bpm in MIN..MAX) }
    fun plus(delta: Int): Tempo = Tempo((bpm + delta).coerceIn(MIN, MAX))
    companion object { const val MIN = 40; const val MAX = 240; val Default = Tempo(120); fun clamped(bpm: Int): Tempo }
}
enum class TempoMarking(val label: String, val low: Int, val high: Int) {   // spec FR-005; boundary → the slower name
    Largo("Largo", 40, 60), Larghetto("Larghetto", 60, 66), Adagio("Adagio", 66, 76), Andante("Andante", 76, 108),
    Moderato("Moderato", 108, 120), Allegro("Allegro", 120, 156), Vivace("Vivace", 156, 176), Presto("Presto", 176, 200),
    Prestissimo("Prestissimo", 200, 240);
    val display: String get() = "$label ($low-$high)"
    companion object { fun of(tempo: Tempo): TempoMarking = entries.first { tempo.bpm <= it.high } }   // 120 → Moderato, 121 → Allegro
}
enum class TimeSignature(val id: String, val label: String, val beats: Int, val unit: BeatUnit) {   // spec FR-006, in display order
    FourFour("4-4", "4/4", 4, Quarter), ThreeFour("3-4", "3/4", 3, Quarter), SixEight("6-8", "6/8", 6, Eighth),
    TwoFour("2-4", "2/4", 2, Quarter), FiveFour("5-4", "5/4", 5, Quarter), SevenEight("7-8", "7/8", 7, Eighth);
    companion object { val Default = FourFour; fun byId(id: String): TimeSignature? }
}
enum class BeatUnit { Quarter, Eighth }
enum class Subdivision(val id: String, val ticksPerBeat: Int) {          // spec FR-007, in display order
    One("1", 1), Two("2", 2), Four("4", 4), Triplet("3", 3);
    fun label(unit: BeatUnit): String   // Quarter: "1/4", "1/8", "1/16", "3let"; Eighth: "1/8", "1/16", "1/32", "3let"
    companion object { val Default = One; fun byId(id: String): Subdivision? }
}
```

Validation: `Tempo` refuses values outside 40–240 at construction; every `plus` clamps. Tests
cover every tempo (marking table), every boundary, and both label sets.

## 2. Beat levels, accent pattern, styles and the tick grid (`:shared:core`)

```kotlin
enum class BeatLevel(val id: Char, val label: String) { Accent('A', "Accent"), Click('C', "Click"), Sub('S', "Sub");
    fun next(): BeatLevel   // Accent → Click → Sub → Accent (spec FR-008)
    companion object { fun byId(id: Char): BeatLevel? }
}
@JvmInline value class AccentPattern(val levels: List<BeatLevel>) {   // one entry per beat; size == signature.beats
    fun cycle(beat: Int): AccentPattern
    fun resizedTo(beats: Int): AccentPattern            // keep existing beats, new beats Click (spec FR-010, no style selected)
    fun encode(): String                                // "ACSC"
    companion object { fun decode(text: String, beats: Int): AccentPattern? }   // null on a bad char; length mismatch → resizedTo
}
enum class MetronomeStyle(val id: String, val label: String) {         // spec FR-010
    Standard("standard", "Standard"), Downbeat("downbeat", "Downbeat"), Syncopated("syncopated", "Syncopated");
    fun pattern(signature: TimeSignature): AccentPattern
    //   Standard: beat 1 Accent, others Click; SixEight also beat 4 Accent
    //   Downbeat: beat 1 Accent, others Sub
    //   Syncopated: even-numbered beats Accent, odd (incl. beat 1) Click
    companion object { fun matching(pattern: AccentPattern, signature: TimeSignature): MetronomeStyle?; fun byId(id: String): MetronomeStyle? }
}
enum class SyncopationReading(val id: String) { EvenBeats("even-beats"), OffBeatTicks("off-beat-ticks"), AllButOne("all-but-one") }   // spec FR-037, debug only
data class TickGrid(val ticksPerBeat: Int, val levels: List<BeatLevel>) {   // levels.size == beats × ticksPerBeat, tick 0 = beat 1
    val beats: Int get() = levels.size / ticksPerBeat
    companion object {
        fun from(signature: TimeSignature, subdivision: Subdivision, pattern: AccentPattern, style: MetronomeStyle?,
                 reading: SyncopationReading = SyncopationReading.EvenBeats): TickGrid
        //   EvenBeats: beat ticks take the cell's level, subdivision ticks Sub
        //   OffBeatTicks (only while style == Syncopated): subdivision ticks Accent, beat ticks Sub; with ticksPerBeat 1 behaves as EvenBeats
        //   AllButOne  (only while style == Syncopated): beat ticks Accent except beat 1 Sub, subdivision ticks Sub
    }
}
```

Invariants: `pattern.levels.size == signature.beats` always (the ViewModel resizes on a
signature change); `style` is `null` exactly when `MetronomeStyle.matching(pattern, signature)`
is `null`. `TickGridTest` asserts every worked example of the spec and the three readings.

## 3. Tap tempo (`:shared:core`)

```kotlin
data class TapTempoSequence(val tapNanos: List<Long> = emptyList()) {   // most recent last; at most 9 taps kept (8 intervals)
    fun tap(atNanos: Long): TapTempoSequence        // a gap > WINDOW_NANOS (2 s) starts a new sequence
    val tempo: Tempo?                               // null with fewer than two taps; else 60e9 / mean(intervals) rounded, clamped (spec FR-016)
    val count: Int get() = tapNanos.size
    companion object { const val WINDOW_NANOS = 2_000_000_000L; const val MAX_INTERVALS = 8 }
}
```

## 4. Click sounds and the custom sound (`:shared:core`, `:shared:engine`)

```kotlin
enum class BuiltInClickSound(val id: String, val label: String) {       // spec FR-018, FR-021 order
    ClassicWood("classic-wood", "Classic Wood"), Rim808("808-rim", "808 Rim"), DigitalBeep("digital-beep", "Digital Beep"),
    Cowbell("cowbell", "Cowbell"), Mechanical("mechanical", "Mechanical")
}
sealed interface ClickSound {
    data class BuiltIn(val sound: BuiltInClickSound) : ClickSound
    data class Custom(val displayName: String) : ClickSound                 // the file name without extension
}
// :shared:engine
enum class ClickLevel { Sub, Click, Accent }                                // maps 1:1 to tembra_click_level (0, 1, 2)
enum class ClickSoundId(val native: Int) { ClassicWood(0), Rim808(1), DigitalBeep(2), Cowbell(3), Mechanical(4), Custom(5) }
data class CustomSoundInfo(val sourceRate: Int, val sourceChannels: Int, val sourceBits: Int, val keptFrames: Int, val trimmedMs: Float, val keptMs: Float)
sealed interface CustomSoundResult {
    data class Loaded(val info: CustomSoundInfo) : CustomSoundResult
    data object NotWav : CustomSoundResult          // TEMBRA_ERR_DECODE
    data object Silent : CustomSoundResult          // TEMBRA_ERR_SILENT
    data object Missing : CustomSoundResult         // the path cannot be opened (TEMBRA_ERR_ARGUMENT)
}
```

Preparation rules (research R4): decode the first 30 s; onset = first frame whose 2 ms RMS is
≥ −40 dB relative to the file peak, backed up 1 ms with a 1 ms fade-in; keep ≤ 2 000 ms after
the onset; 5 ms fade-out; Click peak −9 dBFS, Accent +6 dB limited to −1 dBFS, Sub −8 dB; resample
to the stream rate. A file with peak < −60 dBFS is `Silent`.

## 5. Metronome settings and preferences (`:shared:core`, `:shared:data`)

```kotlin
data class MetronomeSettings(                                            // the remembered state (spec FR-026)
    val tempo: Tempo = Tempo.Default,
    val signature: TimeSignature = TimeSignature.Default,
    val subdivision: Subdivision = Subdivision.Default,
    val pattern: AccentPattern = MetronomeStyle.Standard.pattern(TimeSignature.Default),
    val style: MetronomeStyle? = MetronomeStyle.Standard,
    val sound: ClickSound = ClickSound.BuiltIn(BuiltInClickSound.ClassicWood),
    val customSoundPath: String? = null,                                  // the app's copy; null = no custom sound imported
)
// :shared:data, audio.tembra.data.metronome
data class MetronomePreferencesSnapshot(val settings: MetronomeSettings = MetronomeSettings()) {
    fun encode(): Map<String, String>                                     // keys of MetronomePreferenceKeys
    companion object { fun decode(values: Map<String, String?>): MetronomePreferencesSnapshot }   // unknown id → default; levels re-fitted
}
object MetronomePreferenceKeys { tempo = "metronome.tempo"; signature = "metronome.signature"; subdivision = "metronome.subdivision";
    levels = "metronome.levels"; style = "metronome.style"; sound = "metronome.sound"; customName = "metronome.customName"; customPath = "metronome.customPath" }
interface MetronomePreferences { val snapshot: Flow<MetronomePreferencesSnapshot>; suspend fun update(transform: (MetronomePreferencesSnapshot) -> MetronomePreferencesSnapshot) }
interface CustomClickSoundStore {
    val path: String                                   // <documents>/metronome/custom.wav
    val stagingPath: String                            // <documents>/metronome/custom.wav.tmp
    suspend fun commit(): Boolean                      // atomic rename staging → path; false if staging is missing
    suspend fun exists(): Boolean
    suspend fun delete()
}
```

Decoding rules: `tempo` outside 40–240 or not an integer → 120; `sound == "custom"` without a
`customPath` → Classic Wood; `style` absent and the pattern matching a style → that style is
shown selected (derived, not stored).

## 6. Engine-side grid, sound and tick snapshot (`:shared:engine`, `engine/`)

```kotlin
data class MetronomeGrid(val tempo: Tempo, val ticksPerBeat: Int, val levels: List<ClickLevel>) {   // ≤ 32 levels
    companion object { fun from(grid: TickGrid, tempo: Tempo): MetronomeGrid }
}
class TickSnapshot {                                   // mutable scratch, filled by readTick, never allocated per frame
    var tickIndex = 0L; var beatIndex = 0; var level = ClickLevel.Click; var audibleInNanos = 0L; var beatPeriodNanos = 0L; var running = false; var sampleRate = 0
}
```

C ABI structs (contracts/engine-c-abi-v3.md): `tembra_metronome_grid { double bpm; uint32_t
ticks_per_beat; uint32_t ticks_per_bar; uint8_t levels[32]; }`, `tembra_custom_sound_info`,
`tembra_metronome_tick { uint64_t tick_index; uint32_t beat_index; uint32_t level; int64_t
audible_in_ns; int64_t beat_period_ns; uint32_t running; uint32_t sample_rate; }`.

Engine state machine (`tembra::EngineCore`, atomic `state_`):

| State | Enter | Leave | Refuses |
|---|---|---|---|
| `IDLE` | stop of anything; one-shot end | — | — |
| `LISTENING` | `tembra_tuner_start` | `tembra_tuner_stop`, watchdog | tones, samples, metronome start |
| `TONE` | `tembra_tone_start`, `tembra_sample_play`, `tembra_metronome_preview` | end or `tembra_tone_stop` | listening, metronome start |
| `METRONOME` | `tembra_metronome_start` | `tembra_metronome_stop` | listening, tones, samples, preview |

`set_grid`, `select_sound`, `load_custom_sound`, `unload_custom_sound` and `set_output_latency_ns`
are accepted in every state (they publish snapshots the renderer reads when it runs).

Scheduler state (RT-owned, `MetronomeRenderer`): `framePos: int64`, `lastTickFrame: double`,
`tickIndex: uint32` (position in the bar), `gridSlot`, `soundSlot`, `voice`, `releasingVoice`;
transitions per research R1 (grid change mapping), R5 (release), and `anchor()` for the transport.

## 7. Playback state and the lifecycle policy (`:shared:ui`)

```kotlin
enum class PlaybackState { Stopped, Starting, Running }        // Starting: host acquired, engine start pending (< 100 ms)
object PlaybackPolicy {                                         // pure, unit-tested
    fun onEvent(state: PlaybackState, event: PlaybackEvent): PlaybackTransition
    // PlaybackEvent: PlayPressed, PausePressed, ScreenHidden, AudioInterrupted, OutputRouteLost, DeviceLost, StopRequested, StartFailed(reason)
    // PlaybackTransition: Start, Stop(message: MetronomeMessage?), Ignore
    //   Running + {ScreenHidden, AudioInterrupted, OutputRouteLost, DeviceLost, StopRequested, PausePressed} → Stop
    //   Stopped + PlayPressed → Start; anything else → Ignore; nothing ever restarts by itself (spec FR-027)
}
```

Start sequence (ViewModel): `host.keepAlive()` (Android: focus + service; iOS: true) → on
false: `Stop(message = PlaybackRefused)`; else `audio.startMetronome()` → on `Error`: release the
host, `Stop(message = PlaybackRefused)`; else `Running`. Stop sequence: `audio.stopMetronome()`
then `host.release()`. The order guarantees the process is foreground-privileged before the
stream opens and that focus is abandoned after the stream closes.

## 8. `MetronomeUiState` (`:shared:ui`)

```kotlin
data class MetronomeUiState(
    val loaded: Boolean = false,                        // preferences read; controls are inert before
    val settings: MetronomeSettings = MetronomeSettings(),
    val playback: PlaybackState = PlaybackState.Stopped,
    val currentBeat: Int = -1,                          // 10 Hz sample for semantics and tests; the light itself is draw-phase (research R7)
    val tap: TapTempoSequence = TapTempoSequence(),
    val tapCaption: TapCaption = TapCaption.Rest,       // Rest("Sync finger rhythm") | Again("Tap again") | Counting(n)
    val customSound: CustomSoundState = CustomSoundState.None,   // None | Importing | Available(displayName) | Unavailable(displayName)
    val syncopationReading: SyncopationReading = SyncopationReading.EvenBeats,   // debug only
) {
    val marking: TempoMarking get() = TempoMarking.of(settings.tempo)
    val subdivisionLabels: List<String>                 // for the current beat unit
    val grid: TickGrid                                  // derived from settings and the reading
    val running: Boolean get() = playback == PlaybackState.Running
    val canStepDown: Boolean; val canStepUp: Boolean
}
sealed interface MetronomeMessage { NotWav, Silent, TooLarge, ImportFailed, CustomSoundMissing, PlaybackRefused }   // one-shot snackbar copy in MetronomeCopy
```

Intents (plain functions on `MetronomeViewModel`): `setTempo(Tempo)`, `stepTempo(delta: Int)`,
`selectSignature`, `selectSubdivision`, `cycleBeat(index)`, `selectStyle`, `selectSound(ClickSound)`,
`importCustomSound()`, `replaceCustomSound()`, `tap(atNanos)`, `togglePlayback()`,
`screenVisible(Boolean)`, `useDebugSettings(MetronomeDebugRequest?)`. Every settings intent
persists through `MetronomePreferences.update` and, while running, sends a new grid or sound to
the engine; a sound selection while stopped previews one Click.

## 9. Layout, dial geometry and new design tokens (`:shared:ui`)

`MetronomeLayout` (pure, unit-tested; same bands as `TunerLayout`): `Stacked` (width under 600 dp,
or under 840 dp when the window is 480 dp or taller), `Wide` (840 dp or wider and 480 dp or
taller), `Landscape` (600 dp or wider and shorter than 480 dp); `from(windowSizeClass)`.

`TempoDialGeometry` (pure, unit-tested): ring radius 0.4 of the box, track from −90° (12 o'clock)
clockwise over 275°, `sweepFor(tempo) = (bpm − 40) / 200 × 275°`, `tempoAt(angleFromTop) =
40 + round(angle.coerceIn(0, 275) / 275 × 200)`, eight tick marks at 45° from 0.408 to 0.442 of
the box, stroke 6 dp (fill and track), glow stroke 8 dp at 50 %.

Tokens added (tested for parity with research R10 in `MetronomeTokensTest`):

| Token | Value |
|---|---|
| `TembraTypographyExtras.tempoNumber` | Space Grotesk 64 sp / 64 sp, weight 700, −0.05 em, tabular figures |
| `TembraTypographyExtras.sectionLabel` | Space Grotesk 10 sp / 14 sp, weight 600, 0.05 em, uppercase in use |
| `TembraSpacing.chipHeight` / `pillHeight` | 32 dp / 28 dp (44 dp targets) |
| `TembraSpacing.accentCellBarHeight` | 64 dp |
| `TembraSpacing.tempoDialMaxWidth` / `tempoDialMaxWidthWide` | 270 dp / 420 dp |
| `TembraSpacing.tempoStepper` | 44 dp |
| `TembraSpacing.tapTempoHeight` / `playButton` | 64 dp / 64 dp |
| `TembraSpacing.soundMenuWidth` / `soundTileHeight` / `soundTileGlyphBox` | 160 dp / 44 dp / 28 dp |
| `TembraSpacing.wideSettingsMinWidth` | 320 dp (the wide layout's left column, 1/3 of the width at least this) |
| `TembraGradients.tempoArc` | linear, start → end of the dial box: `primary` (#CEBDFF) → `primaryContainer` (#A78BFA) at 50 % → `secondary` (#FFAFD3) |
| `TembraGradients.tempoDialCard` | radial at (0.5, 0.5): `primaryContainer` 8 % → #1E1B2E 40 % at 40 % → `surfaceDim` 95 % at 100 % |
| `TembraGradients.tempoDialOverlay` | vertical linear: `primary` 10 % → transparent at 50 % → `secondary` 10 % |
| `TembraGradients.accentGlow` | `Glow(secondary 50 %, 12 dp)` |
| `TembraGradients.playGlow` | `Glow(secondary 35 %, 24 dp)` |
| `CardDiscs.TopStartBottomEnd` (default, the tuner's) / `CardDiscs.TopEndBottomStart` (the tablet dial card) | placement of the existing disc tokens |

`TembraIcon` gains `Remove, Add, TouchApp, MusicNote, GraphicEq, RadioButtonChecked,
Notifications, Sensors, Tune, Check`. No colour is added to `TembraColorScheme` or `DawColors`.

## 10. Debug request (`:shared:ui`; research R14)

```kotlin
data class MetronomeDebugRequest(val syncopation: SyncopationReading) {
    companion object { fun parse(value: String?): MetronomeDebugRequest? }   // "even-beats" | "off-beat-ticks" | "all-but-one"; else null
}
val LocalMetronomeDebug = staticCompositionLocalOf<MetronomeDebugRequest?> { null }
```

Android extra `tembra.metronome.syncopation` and iOS argument `--tembra-metronome-syncopation=`,
both read only in debug builds.
