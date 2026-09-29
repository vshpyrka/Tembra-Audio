# Contract: Metronome domain (`:shared:core`)

**Package**: `audio.tembra.core.metronome` | **Depends on**: nothing (pure Kotlin) | **Consumers**: `:shared:engine` (`MetronomeGrid.from`), `:shared:data`, `:shared:ui`, later the transport feature (constitution v1.2.0)

The types are defined in [data-model.md](../data-model.md) §1–§5. This contract fixes the
behaviour the tests assert (`kotlin-api-design`: single-field domain concepts are value classes,
`Tempo` and `AccentPattern`; parsing is owned by the target type's companion; no platform types).

| Type | Function | Behaviour |
|---|---|---|
| `Tempo` | `plus(delta)` | clamps to 40–240; `Tempo(39)` throws `IllegalArgumentException`; `clamped(n)` never throws |
| `TempoMarking` | `of(tempo)` | the first entry whose `high ≥ bpm`; `display` is `"Moderato (108-120)"` |
| `TimeSignature` | `byId` | `"6-8"` → `SixEight`; unknown → null; `unit` is `Eighth` for `SixEight` and `SevenEight` |
| `Subdivision` | `label(unit)` | `Quarter`: `1/4 1/8 1/16 3let`; `Eighth`: `1/8 1/16 1/32 3let` |
| `BeatLevel` | `next()` | `Accent → Click → Sub → Accent` |
| `AccentPattern` | `cycle(i)`, `resizedTo(n)`, `encode`/`decode` | `resizedTo` keeps the first `n` levels and appends `Click`; `decode("ACS", 4)` → `ACSC`; `decode("AXC", 3)` → null |
| `MetronomeStyle` | `pattern(sig)`, `matching(pattern, sig)` | the table of spec FR-010; `matching` returns the first style whose pattern equals the given one, else null |
| `TickGrid` | `from(...)` | the worked examples of the spec: `4/4 × 1/4 × Standard = A C C C`; `4/4 × 1/8 × Standard = A s C s C s C s`; `4/4 × 1/4 × Downbeat = A s s s`; `4/4 × 1/4 × Syncopated = C A C A`; `6/8 × 1/8 × Standard = A C C A C C`; `7/8 × 1/8 × Syncopated = C A C A C A C`; `3/4 × 3let × Standard = A s s C s s C s s`; readings: `4/4 × 1/8 × Syncopated`, `OffBeatTicks` = `s A s A s A s A`, `AllButOne` = `s C A C A C A C`; `OffBeatTicks` with one tick per beat equals `EvenBeats` |
| `TapTempoSequence` | `tap(t)`, `tempo` | one tap → null; taps at 0, 0.5 s → 120; eight taps at 600 ms ± 20 ms jitter → 100 ± 1; a tap 2.5 s after the last starts over (count 1); intervals of 200 ms → 240 (clamped); 1.8 s → 40 (clamped); at most the last eight intervals are averaged |
| `ClickSound` | — | `BuiltIn(sound)` and `Custom(displayName)`; `MetronomeSettings` defaults as in data-model.md §5 |

**Tests** (`commonTest`, JVM and iOS simulator): `TempoTest`, `TempoMarkingTest` (all 201 tempos),
`TimeSignatureTest`, `SubdivisionTest`, `AccentPatternTest`, `MetronomeStyleTest` (every style ×
every signature; `matching` round trip; a hand-edited pattern matches nothing), `TickGridTest`,
`TapTempoSequenceTest`, `ClickSoundTest` (ids unique, order of spec FR-021).
