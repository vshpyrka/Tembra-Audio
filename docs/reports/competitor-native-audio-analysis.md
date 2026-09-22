# Audio Apps — Native Library & Audio-Engine Analysis

Device: Pixel 6 Pro (raven), arm64-v8a · Pulled & analyzed 2026-08-23

All three apps ship as **split APKs**; every native `.so` lives in the
`split_config.arm64_v8a.apk` split (nothing in `base.apk`). Analysis done with
`objdump -p/-T` (NEEDED deps + JNI exports) and `strings` on the ELF binaries.

| App | Package | Version | Core audio engine | Native audio I/O | .so count |
|-----|---------|---------|-------------------|------------------|-----------|
| BandLab | `com.bandlab.bandlab` | 11.30.1 | In-house C++ (`libbandlab_audio_engine.so`) + **Oboe** | **AAudio (MMAP)** → OpenSL ES fallback | 25 |
| n-Track Studio | `com.ntrack.studio.demo` | 10.5.185 | In-house C++ DAW (ported desktop code) | **AAudio + OpenSL ES** (runtime pick) | 47 |
| Remixlive | `com.mixvibes.remixlive` | 8.6.2 | **JUCE** framework (`libnative-lib.so`) | JUCE Android backend: OpenSL ES + AAudio | 8 |

---

## 1. BandLab — `libbandlab_audio_engine.so` (11 MB)

**How it does audio.** A single large C++ engine exposing **1184 JNI methods** to
Kotlin. The bindings are **Djinni-generated** (Dropbox's IDL tool — note the
`com.bandlab.audiocore.generated.*_00024CppProxy.native_1*` symbol pattern).

- **Audio I/O = Google Oboe.** Strings reference `AudioDevice_Oboe.cpp`,
  `AudioInputDevice_Oboe`, `AudioOutputDevice_Oboe`, and a Kotlin
  `OboeDefaultsKt.setOboeMMapEnabled`. Oboe dynamically loads `libaaudio.so`
  and uses the full `AAudioStreamBuilder_*` API (MMAP low-latency path), falling
  back to **OpenSL ES** (`libOpenSLES.so` is a hard NEEDED dep).
- **Codecs = FFmpeg**, bundled as separate libs and linked as NEEDED:
  `libavcodec.so`, `libavformat.so`, `libavutil.so`, `libswresample.so`
  (decode + sample-rate conversion).
- **Mixing / DSP** (from embedded effect metadata JSON): 3-band **EQ**
  (Low/Mid/High band gain + bypass), **Flanger**, **Delay**, **Compressor**
  ("Punchy Compressor"), a **mastering limiter** effect chain, per-sample
  gain/pan, and full **parameter automation**. Also an **Arpeggiator** and MIDI
  processing (it's a multitrack DAW + MIDI, not just a recorder).
- **Other native libs:** `libmpeghJNI.so` (Fraunhofer **MPEG-H 3D/immersive
  audio** decode), `libonnxruntime.so` (ONNX ML runtime — on-device models),
  plus non-audio SDKs (FFmpeg AV, Crashlytics, a video/effects SDK
  `libtt_ugen_layout`/`libmadonna`, Unity Coherence, path/graphics helpers).

**Verdict:** Yes — heavy native audio. The mixing/effects/transport all run in
`libbandlab_audio_engine.so`; real-time I/O is Oboe→AAudio-MMAP.

---

## 2. n-Track Studio — layered native DAW (47 libs, the largest)

This is a **desktop DAW ported to Android**. `libnTrackWinOnMac.so` (16 MB) is a
Win/Mac source-compatibility shim, and the binaries carry desktop **VST**
strings (`"is a VST plug-in"`, `------- VST EFFECTS -------`,
`/Library/Audio/Plug-Ins/VST`).

**Native architecture (NEEDED graph):**
`libntrack_native_app.so` (17 MB, top layer, 539 JNI methods)
→ `libnTrackEngine.so` (audio engine)
→ `libnTrackDSPDll.so` (effects/DSP)
→ `libaudiomodule.so` (**low-level audio driver** — the only lib linking *just*
   `libOpenSLES.so`, 30 JNI methods; this is the HAL)
→ `libnTrackFramework.so`, `libnTrackNativeControls.so`, `libntrackjc.so`,
  `libntrackusb.so` (USB audio interfaces).

- **Audio I/O = both AAudio and OpenSL ES**, chosen at runtime. Strings show
  paired classes `AudioDriverInputAAudio/OutputAAudio` and
  `AudioDriverInputOpenSLES/OutputOpenSLES`, plus AAudio format negotiation
  (`AAUDIO_FORMAT_PCM_FLOAT/I16`, MMAP timestamps). It also supports **USB audio
  interfaces** (`libntrackusb.so`).
- **Mixing / DSP** (rich, DAW-grade): **Dynamic EQ** (2nd/4th-order low/high-pass
  filter templates), **FDN reverb** + convolution reverb (`libntrackconvolverb.so`),
  **Compressor** (attack/ratio/release/threshold + gate + auto make-up gain),
  **Chorus**, **PitchShift**, guitar **Amp-sim host** (`libntrackampsim.so`),
  **BQResampler** (Breakfast-Quay-style high-quality resampler), a MixerHost /
  TimelineHost / EffectShellHost object model, per-track pan/gain.
- **Instruments/codecs:** SoundFont synth (`libsfsynth.so`, 2.7 MB), drum engine
  (`libntrackdrums`), metronome, FLAC/Opus/Ogg + FFmpeg.
- **AI (PyTorch):** `libtorch_cpu.so` (63 MB!) + `libc10.so` + `libomp.so` +
  `libntrackpytorch.so`. Used for **AI stem separation** (`SeparateStemsAutoDelete`),
  a **denoiser**, and **mastering** — the ML-heavy "song → parts" features.

**Verdict:** Yes — by far the most native audio code here (a full DAW engine,
VST hosting, USB audio, and PyTorch-based AI processing).

---

## 3. Remixlive — `libnative-lib.so` (13 MB), built on JUCE

A DJ / loop-triggering app whose entire audio engine is the **JUCE** C++
framework. Build paths are baked in and unmistakable, e.g.
`…/RLCommon/src/main/RLAudioEngine/extern/JUCE/modules/juce_dsp/...`.
**212 JNI methods** under `com.mixvibes.common.nativeInterface` (`RLEngine`,
`RLAutomatables`, `RLDjMixRecorder`, `NotificationCenter`).

- **Audio I/O = JUCE's Android backend** → `libOpenSLES.so` (NEEDED) + AAudio
  (dlopen `libaaudio.so`) + `libmediandk.so` (NDK codecs for decoding
  loops/samples).
- **Mixing / DSP:** a `MasterMixer`, JUCE `DryWetMixer`, `Compressor`, `Limiter`,
  `Reverb`, `DelayLine`; app-specific effects `RL_Chorus`, `RL_Flanger`,
  `RL_Limiter`, `VCVDubDelay`, `VCVDistortion`, multiple chorus/reverb variants,
  **TimeStretch** + high-quality resamplers (`CHQResample`, `CFastResample`), a
  `LoopProcessor`, `RL_Metronome`, and full automation (`RL_Automation`).
- **Tempo sync:** **Ableton Link** (`RLEngine.enableABLink…`) for BPM sync across
  devices; BPM inc/dec controls.
- **AI (TensorFlow Lite):** `libtensorflowlite_jni.so` + `libtensorflowlite_gpu_jni.so`
  power the **stem separator / "Unmixer"** (`RL_UnmixerAudioProcessor`,
  `RL_StemPreviewer`, `enableStemsExport`) — split a track into vocals/drums/etc.
- **Non-audio:** `libpairipcore.so` = Google Play **Automatic Integrity
  Protection** (anti-tamper), plus Crashlytics.

**Verdict:** Yes — real-time mixing via JUCE; effects, time-stretch, DJ mixer,
Ableton Link, and TFLite stem separation are all native.

---

## Cross-app summary

- **Every app** relies on native `.so` code for real-time audio; none use only
  Java/Kotlin audio APIs.
- **Real-time I/O:** all three use **OpenSL ES** and/or **AAudio**. BandLab
  reaches them through **Oboe**; Remixlive through **JUCE**; n-Track through its
  own driver layer (`libaudiomodule.so`).
- **Engine strategy differs:** BandLab = in-house engine + Oboe + Djinni bindings;
  n-Track = ported desktop DAW (VST, USB audio, huge native surface);
  Remixlive = JUCE-framework app.
- **AI is now core to all three:** ONNX (BandLab), **PyTorch/libtorch** (n-Track),
  **TensorFlow Lite** (Remixlive) — mainly for stem separation / denoise /
  mastering.
- **Codecs:** FFmpeg in BandLab and n-Track; NDK `libmediandk` in Remixlive.

---

## Appendix — How the effects (reverb, compressor, gate, EQ, chorus, time-stretch) are actually built

Evidence: retained C++ source-file paths, RTTI/mangled class names, and library
signature strings inside the `.so` files.

### Remixlive → the **JUCE `juce_dsp` module** (+ commercial time-stretch)
The effect DSP is JUCE's stock processors — the exact source files are compiled in:
- Dynamics: `juce_Compressor.cpp`, `juce_Limiter.cpp`, **`juce_NoiseGate.cpp`** (the gate),
  with `juce_BallisticsFilter.cpp` as the attack/release envelope follower they share.
- Modulation: `juce_Chorus.cpp`, `juce_Phaser.cpp` (modulated `juce_DelayLine`).
- Filters/EQ: `juce_IIRFilter` (biquad), `juce_FIRFilter`, **`juce_LinkwitzRileyFilter`**
  (crossovers), `juce_StateVariableTPTFilter`, `juce_LadderFilter`, `juce_FilterDesign`.
- Reverb: JUCE `Reverb` (Freeverb-type) **plus a Dattorro plate-reverb** and
  **FFT convolution** (`juce_Convolution.cpp` + `juce_FFT.cpp`).
- Utility: `juce_DryWetMixer`, `juce_Oversampling`, `juce_Panner`.
- On top, Mixvibes' own effects: `RL_Chorus`, `RL_Flanger`, `RL_Limiter`, `VCV*`.
- **Time-stretch/pitch = two engines:** commercial **zplane élastique V3**
  (`CElastiqueV3`, `RL_Elastique.cpp`) as primary, **SoundTouch** (`RLSoundTouch`,
  `soundtouch::RateTransposer`) as alternate; FFT via **pffft** (`CzplFFTpffft`).

### n-Track → custom DAW DSP + **Rubber Band** for stretch
- **Dynamic EQ (`DynEq`)** = a hand-written **biquad filter bank**: band types
  Peak / WidePeak / LowShelf / HighShelf / LowPass / HighPass, each in **2nd-order
  (12 dB/oct) and 4th-order (24 dB/oct)** cascades, templated for float & double.
  "Dynamic" = a level detector modulates each band's gain (compressor-per-band).
- **Reverb** = **`FDNReverb`** (Feedback Delay Network) + a separate
  **convolution reverb** (`libntrackconvolverb.so`, impulse-response based).
- **Compressor** = custom, params Attack/Release (L/R), Threshold, Ratio,
  **Auto Make-up Gain**; also a noise gate. Guitar **amp-sim** = `libntrackampsim.so`.
- **Time-stretch/pitch = Rubber Band Library** (536 refs) with mode presets
  `Vocal / Melodic / Percussive` (`PitchAdapterRubberband*`), **SoundTouch** as an
  alternate (`PitchAdapterSoundTouch`); resampling via Breakfast-Quay `BQResampler`
  (same author as Rubber Band).

### BandLab → custom C++ engine structured like the **Web Audio API**
Source names reveal a Web-Audio-style DSP node graph ported to native C++:
- EQ/filters: `BiquadFilterNode.cpp` (Web-Audio BiquadFilter: LP/HP/peaking/shelf).
- Dynamics: `DynamicsCompressorNode.cpp` + `DynamicsCompressorKernel.cpp` +
  `EnvelopeDetector.cpp`, `CompressorEffect.cpp`, and a `MultibandCompressor.cpp`
  for mastering. Includes a **UREI 1176 emulation** (`SerafiniCompressor1176Effect`,
  slug `compr1176`).
- Gate: `GaterEffect.cpp` / `SerafiniExpGateEffect` (rhythmic gater) + `looperGater`.
- Reverb: **`Freeverb.cpp`** (classic Schroeder–Moorer comb+allpass; `LowPassCombFilter`).
- Runs inside `libbandlab_audio_engine.so`; I/O via Oboe (see §1).

### One-line comparison
| Effect area | BandLab | n-Track | Remixlive |
|---|---|---|---|
| Framework | custom (Web-Audio-style C++) | custom DAW C++ | **JUCE juce_dsp** |
| EQ / filters | biquad nodes | biquad bank (2nd/4th order) | JUCE IIR/Linkwitz-Riley/SVF |
| Compressor/gate | DynamicsCompressor + 1176 model, Gater | custom comp + gate + auto-makeup | JUCE Compressor/Limiter/NoiseGate |
| Reverb | Freeverb | FDN + convolution | Freeverb-type + Dattorro + convolution |
| Chorus/mod | custom | custom | JUCE Chorus/Phaser |
| Time-stretch/pitch | (Oboe resampler / FFmpeg) | **Rubber Band** + SoundTouch | **zplane élastique** + SoundTouch |
| FFT | — | fftw ref | **pffft** |

### Folder layout
```
docs/reports/competitor-native-audio-analysis.md   (this file)
reference/competitor-apps/                          (binaries, not committed to git)
├── bandlab/{apk,extracted}/     com.bandlab.bandlab 11.30.1
├── ntrack/{apk,extracted}/      com.ntrack.studio.demo 10.5.185
└── remixlive/{apk,extracted}/   com.mixvibes.remixlive 8.6.2
```
Each `apk/` holds the pulled splits; each `extracted/<split>/lib/arm64-v8a/`
holds the decompressed `.so` files.
