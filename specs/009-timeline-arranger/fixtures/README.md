# Audio fixtures (feature 009)

Synthesised by `make_fixtures.py` (Python 3 standard library; FLAC through `ffmpeg` or macOS `afconvert`, MP3 through
`ffmpeg`, `lame` or the `lameenc` wheel: `uv run --with lameenc python make_fixtures.py`). Every file is generated,
project-owned content with no third-party licence; re-running the script rewrites them identically except the MP3,
whose encoder build may differ in its padding.

| File | Purpose |
|---|---|
| `tone-48k-stereo.wav` | the reference stereo WAV (440 Hz left, 660 Hz right, 1 s): decoder, clip source, seek and overview tests |
| `tone-44k1-mono-16.wav` | resampling to the 48 kHz stream (frame count within ±2 of `frames × 48000 / 44100`), mono duplication |
| `tone-8bit-8k.wav` | 8-bit unsigned PCM at 8 kHz: the low end of the decoder matrix |
| `tone-float32-192k.wav` | 32-bit float at 192 kHz: the high end of the decoder matrix |
| `silence-5s.wav` | all-zero overview buckets |
| `tone-4ch.wav` | four channels: reported with `channels == 4`, refused by the importer (`TooManyChannels`) |
| `tone.flac` | the stereo tone losslessly: FLAC acceptance and sample-exact seeks |
| `tone.mp3` | the stereo tone at 128 kbps: MP3 acceptance and seeks within one MP3 frame (1152 samples) |
| `not-audio.txt` | refused with `TEMBRA_ERR_DECODE` |
| `truncated.wav` | the first 100 bytes of the stereo WAV: refused with `TEMBRA_ERR_DECODE` |
| `long-31min.wav` (not committed) | `make_fixtures.py --long <dir>`: refused by the importer as too long; the overview builder's partial header |

The Catch2 tests find this folder through `TEMBRA_FIXTURES_DIR` (set by `app/engine/tests/CMakeLists.txt`); the Kotlin
importer tests script the inspector instead and never read these files.
