# Handoff — Android audio-app native-library analysis

Date: 2026-08-23 · Host: macOS (darwin arm64)

## Goal of the session
Pull three installed music apps off a connected Pixel device, extract them, and
analyze **how they process/mix audio in native code** — specifically what native
libraries (`.so` / JNI) each uses and how the DSP effects (dynamic EQ, reverb,
compressor, gate, chorus, time-stretch, etc.) are implemented.

**Status: complete.** Both questions answered. A full write-up already exists —
do not re-derive it, read it:

- **Primary artifact:** `docs/reports/competitor-native-audio-analysis.md` (repo-relative)
  - §1–3: per-app engine + native-lib breakdown.
  - Appendix "How the effects are actually built": per-app DSP implementation +
    a one-line comparison table. **This is the answer to the follow-up question.**
- Pulled APK splits + extracted `.so` trees live under
  `reference/competitor-apps/{bandlab,ntrack,remixlive}/{apk,extracted}/` (repo-relative, not committed to git).

## Key facts (so the next agent doesn't re-discover them)
- Device: **Pixel 6 Pro (codename raven)**, arm64-v8a, adb serial `19021FDEE001AH`.
  Note: user called it "Pixel 6"; it is actually the **Pro**. It was the only device attached.
- `adb` is at `~/Tools/platform-tools/adb` (v37.0.1). Device authorized.
- Packages / versions pulled:
  - BandLab — `com.bandlab.bandlab` 11.30.1
  - n-Track Studio — `com.ntrack.studio.demo` 10.5.185 (**demo** build)
  - Remixlive — `com.mixvibes.remixlive` 8.6.2
- All three are **split APKs**; every native `.so` is in `split_config.arm64_v8a.apk`
  (nothing in `base.apk`). `.so` counts: bandlab 25, ntrack 47, remixlive 8.

## Findings in one paragraph (detail is in REPORT.md)
- **Audio I/O:** BandLab uses **Oboe** (AAudio-MMAP → OpenSL ES). n-Track uses its
  own driver `libaudiomodule.so` over **AAudio + OpenSL ES**. Remixlive uses the
  **JUCE** Android backend (OpenSL ES + AAudio + libmediandk).
- **DSP implementation approach differs:** Remixlive = **JUCE `juce_dsp`** stock
  processors (Compressor/Limiter/NoiseGate/Chorus/Phaser/LinkwitzRiley/Convolution)
  + **zplane élastique V3** and **SoundTouch** for time-stretch + **pffft**.
  n-Track = **hand-written** biquad filter bank (DynEq 2nd/4th order), **FDN reverb**
  + convolution reverb, custom compressor/gate, **Rubber Band Library** (+SoundTouch)
  for stretch. BandLab = custom C++ engine **modeled on the Web Audio API**
  (`BiquadFilterNode`, `DynamicsCompressorNode`, `EnvelopeDetector`), **Freeverb**
  reverb, a modeled **UREI 1176** compressor, `GaterEffect`.
- All three bundle an **ML runtime** (BandLab ONNX; n-Track PyTorch/libtorch 63 MB;
  Remixlive TensorFlow Lite) mainly for **stem separation / denoise / mastering**.

## Method / tooling notes (reusable)
- No NDK, `readelf`, or `llvm-*` on this box. macOS **`/usr/bin/objdump` is LLVM-based
  and reads ELF fine**:
  - `objdump -p <so> | grep NEEDED` → shared-lib dependencies.
  - `objdump -T <so> | grep Java_` → JNI exports.
  - `strings -a <so>` → retained source paths, mangled class names, library signatures.
- Library identity came from retained build paths (e.g. Remixlive's
  `.../RLAudioEngine/extern/JUCE/...`) and RTTI/mangled names (`CElastiqueV3`,
  `PitchAdapterRubberband`, `DynEq...FourthOrderPeak`, `SerafiniCompressor1176Effect`).

## Possible next steps (none requested yet; offered to user)
1. Trace one effect end-to-end (e.g. dump n-Track `DynEq` band class layout, or list
   every JUCE processor Remixlive instantiates).
2. Inspect Remixlive's `split_embeddedPacks.apk` (bundled loop/sample content).
3. Look at the ML models (ONNX / TorchScript `.pt` / `.tflite`) for stem separation.
4. Runtime profiling of the real-time audio path (would need the app running + a trace).

## Suggested skills for the next agent
- **android-cli** — for any further device/adb work (re-pull, screenshots, `pm` queries).
  Core `.so` pulling is plain `adb`; the skill is mainly for device orchestration.
- **perfetto-trace-analysis** + **perfetto-sql** — only if step 4 (profile the live
  audio callback / underruns while an app runs) is taken; needs a captured Perfetto trace.
- **artifact-design** — if the deliverable becomes a shareable visual comparison/report
  rather than terminal text.
- Binary inspection itself (objdump/strings) needs **no** skill — just the commands above.

## Notes
- No secrets/keys encountered. User PII (email) deliberately omitted from this doc.
- Do not duplicate the competitor report here — it is the source of truth; update it if findings change.
