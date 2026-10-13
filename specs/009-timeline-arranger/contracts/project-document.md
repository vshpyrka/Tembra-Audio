# Contract: the project document (format version 1)

**Where**: `:shared:core`, package `audio.tembra.core.project`; serialised by
`ProjectDocumentCodec` in `:shared:data` with `kotlinx.serialization` JSON. Research R8; spec
FR-046, FR-049; data model §1.

## Guarantees

1. **One text document** per project, `project.json`, UTF-8, pretty-printed with two-space
   indentation and stable key order (declaration order) so diffs are readable.
2. **Versioned**: `formatVersion` is the first key; readers migrate upward through
   `ProjectDocumentMigrations` and refuse a version above their own with
   `DocumentTooNew(version)`.
3. **Forward-compatible**: `Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true; prettyPrint = true }`.
   Unknown keys are dropped on read and therefore not preserved on write — the migration
   policy for a *newer* document is to refuse (rule 2), never to round-trip blindly.
4. **Stable identity**: `id` on the project, every track and every clip (UUID strings); the
   asset id is the SHA-256 of the file bytes. Ids never change for the life of the entity;
   a duplicate project gets new project, track and clip ids and keeps the asset ids.
5. **Beats, not samples**: `startBeats` is a double in beats of the signature's unit; clip
   `offsetFrames` and `lengthFrames` are facts about the audio at the asset's own sample rate
   and never depend on the project tempo.
6. **Nothing device-specific**: no path, URI, cache state, selection, last-opened or window
   state. `zoom` and `grid` are stored because the spec remembers them per project; they are
   view conveniences a server may ignore.
7. **Evolution rules** (the same rules FlatBuffers and Protobuf enforce): a field's name, type
   and meaning never change; new fields are optional with defaults; a removed field's name is
   never reused; enums only grow and readers map unknown enum values to a documented fallback
   (`InstrumentLabel` → `Other`, `MusicalKey` → `null`, `GridSize` → `Sixteenth`,
   `AudioFormat` → refuse the asset as unavailable).
8. **Wire readiness**: the same classes serialise to ProtoBuf or CBOR through
   `kotlinx.serialization` when a server arrives; the field order in this contract is the
   future `@ProtoNumber` order (1-based, declaration order), recorded here so it never shifts.

## Shape (version 1)

```json
{
  "formatVersion": 1,
  "project": {
    "id": "0b6f1c9a-…",
    "name": "Demo project",
    "description": "Four loops to play with",
    "key": "Dmin",
    "tempo": 124,
    "timeSignature": "4/4",
    "masterVolume": 80,
    "metronomeOn": false,
    "grid": "1/16",
    "zoom": 24.0,
    "assets": {
      "3f8a…sha256…": { "fileName": "drums.wav", "format": "wav", "channels": 2, "sampleRate": 48000, "frames": 1486452, "byteSize": 5945808 }
    },
    "tracks": [
      {
        "id": "…", "name": "Drums", "label": "drums", "colorIndex": 0,
        "gainDb": 0.0, "muted": false, "soloed": false,
        "clips": [
          { "id": "…", "assetId": "3f8a…", "startBeats": 0.0, "offsetFrames": 0, "lengthFrames": 1486452, "name": "drums" }
        ]
      }
    ]
  }
}
```

Field notes: `tempo` is the integer BPM (the metronome's `Tempo`); `timeSignature` is the
metronome's id string ("4/4", "3/4", "6/8", "2/4", "5/4", "7/8"); `key` is one of the 24 display
names or absent; `gainDb` uses `"-Infinity"` for silence (the JSON codec is configured with
`allowSpecialFloatingPointValues = true`); `zoom` is pixels per beat at 1× density or the string
`"fit"`; `label` is the lower-case enum name or absent; `colorIndex` 0–5.

## Validation on read (`ProjectValidator`)

Rejected documents open as "unavailable project" in the library with the reason: duplicate ids,
a clip whose asset is missing from `assets` (not the file — a missing *file* makes the clip
unavailable, not the document), `offset + length > frames`, tempo or signature out of range,
a track count above 64 or a clip count above 1024 (the engine's bounds), names out of length.

## Tests (written first, `commonTest`, both lanes)

- Round trip of the demo project and of a 32-track project: equal after `decode(encode(p))`.
- Unknown top-level and nested keys are ignored; a document with `formatVersion: 2` is refused;
  a document with version 0 is migrated (the test registers a fake migration).
- Unknown `label`, `key`, `grid` and `format` values map to the documented fallbacks.
- Serialised text contains no `/`-separated path, no `file:` or `content:` prefix (a regex guard
  that fails if anyone adds a path field later).
- `-Infinity` gain round-trips; `startBeats` keeps full double precision.
- Key order and indentation are stable (golden file `fixtures/demo-project.json`).
- Field numbering table in this contract matches the class declaration order (a reflection-free
  test over the `SerialDescriptor` element names).
