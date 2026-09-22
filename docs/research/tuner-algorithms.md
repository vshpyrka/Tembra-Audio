# Instrument Tuner Algorithms — Guitar & Cello

Research brief for a free, open-source Kotlin Multiplatform (Android + iOS) music app.
Goal: an accurate, low-latency **monophonic tuner** that reliably resolves fundamentals
down to ~60 Hz (cello C2 ≈ 65.41 Hz, guitar low E2 ≈ 82.41 Hz).

**Author:** DSP research pass · **Date:** 2026-08-23

---

## TL;DR recommendation

Use a **time-domain periodicity detector** — **McLeod Pitch Method (MPM / NSDF)** as the
primary estimator, with **YIN (or YINFFT)** as an equally valid alternative / cross-check.
Both are far more robust to the strong overtones of bowed and plucked strings than raw FFT
peak-picking, and both resist octave errors by design. Reuse a battle-tested implementation:
**[TarsosDSP](https://github.com/JorenSix/TarsosDSP)** (`McLeodPitchMethod`, `Yin`, `FastYin`)
on Android/JVM and a **YIN/MPM port** (or [AudioKit `PitchTap`](https://github.com/AudioKit/AudioKit)/
[Beethoven](https://github.com/vadymmarkov/Beethoven)) on iOS.

Concrete parameters: **48 kHz**, **window 4096 samples (~85 ms)**, **hop 1024 (75 % overlap)**,
band-pass **~60–1300 Hz** pre-filter, **parabolic interpolation** of the NSDF/CMNDF peak for
sub-cent precision, then median smoothing + note/cents mapping with A4 = 440 Hz.

---

## 1. Pitch-detection algorithms compared

All the algorithms below target **monophonic** pitch (one note at a time), which is exactly the
tuner case. They split into **time-domain** (autocorrelation, YIN, pYIN, MPM) and
**frequency-domain** (HPS, cepstrum) families.

### 1.1 Time-domain autocorrelation (ACF)

The autocorrelation function `r(τ) = Σ x(t)·x(t+τ)` peaks at lags τ equal to the signal period.
Pick the highest non-zero-lag peak → period → f0 = fs / τ. Simple and cheap, but:

- The **zero-lag peak dominates** and tapering/edge effects bias the peak location.
- **Strong overtones** produce competing peaks; picking the wrong one gives **octave errors**
  (often too *high* — a sub-multiple of the true period). A documented naive-ACF example detected
  **282 Hz for an 83 Hz guitar string** (a ~3–4× overtone confusion), whereas MPM returned
  82.58 Hz ([sevagh/pitch-detection notes](https://github.com/sevagh/pitch-detection/blob/master/misc/mcleod/README.md)).
- Accuracy is decent after interpolation but robustness is poor. This is the baseline everything
  else improves on.

### 1.2 YIN (de Cheveigné & Kawahara, 2002)

YIN is "based on the well-known autocorrelation method with a number of modifications that combine
to prevent errors." Six steps ([YIN paper, JASA 111(4):1917–1930](http://audition.ens.fr/adc/pdf/2002_JASA_YIN.pdf)):

1. **Difference function** `d(τ) = Σ (x(t) − x(t+τ))²` instead of the product ACF (minimum at the
   period, not a maximum — removes the zero-lag dominance problem).
2. **Cumulative Mean Normalized Difference Function (CMNDF)**
   `d'(τ) = d(τ) / [ (1/τ) Σ_{j=1..τ} d(j) ]`, with `d'(0)=1`. This normalization removes the
   tendency to choose τ=0 and **suppresses the sub-octave (too-high) error** by keeping d′ large at
   small lags.
3. **Absolute threshold**: choose the *smallest* τ whose CMNDF dips below a threshold
   (default **0.10–0.15**) — not the global minimum — which further guards against octave-down errors.
4. **Parabolic interpolation** of the chosen dip for sub-sample period precision.
5. **Best local estimate** over a small neighborhood (±20 %) to refine.

Reported result: the six add-ons drop gross error from **17 % → ~0.5 %** on a laryngograph-verified
speech database, "about **three times lower than the best competing methods**," using a **25 ms
integration window** ([YIN paper](http://audition.ens.fr/adc/pdf/2002_JASA_YIN.pdf)). YIN is the de-facto
reference estimator and the basis of aubio's default.

### 1.3 Probabilistic YIN (pYIN — Mauch & Dixon, 2014)

pYIN removes YIN's single-threshold brittleness: it runs YIN across a **distribution of thresholds**
to emit **multiple f0 candidates with probabilities per frame** (stage 1), then **Viterbi-decodes an
HMM** to pick a smooth pitch track (stage 2)
([Mauch & Dixon, ICASSP 2014](https://webspace.eecs.qmul.ac.uk/s.e.dixon/pub/2014/MauchDixon-PYIN-ICASSP2014.pdf)).
It is the state of the art for *offline* monophonic transcription and gives excellent voiced/unvoiced
decisions. For a **real-time tuner** the HMM/Viterbi tracking adds latency and complexity that a
simple median/EMA smoother already covers — so pYIN is overkill here, though its candidate-probability
idea inspires the confidence gating in the pipeline below.

### 1.4 McLeod Pitch Method (MPM / NSDF) — McLeod & Wyvill, 2005

MPM uses a **Normalized Square Difference Function**
`NSDF(τ) = 2·r(τ) / m(τ)`, where `r(τ)=Σ x(t)x(t+τ)` and `m(τ)=Σ (x(t)²+x(t+τ)²)` over the window.
NSDF is bounded in **[−1, 1]** (1 = perfect correlation at that lag), which "is less affected by edge
problems" than raw ACF and yields a natural **clarity/confidence measure**
([McLeod & Wyvill, "A Smarter Way to Find Pitch", ICMC 2005](https://quod.lib.umich.edu/i/icmc/bbp2372.2005.107)).

Peak picking (the octave-error defense):
1. Find all **local maxima** between positive-going zero crossings of NSDF.
2. Take the **highest maximum** `nMax`.
3. Accept the **first** maximum whose height ≥ **k · nMax** (k ≈ **0.8–0.93**). Choosing the *first*
   sufficiently tall peak — not the globally tallest — is what prevents jumping to an octave-related
   overtone peak.
4. **Parabolic interpolation** around that peak → sub-sample period and a refined clarity value.

Crucially, MPM "runs in real time at 44.1 kHz **without low-pass filtering**, so it works on sounds
with high harmonic content such as a violin" — i.e. exactly the rich-overtone regime of bowed cello
and plucked guitar. In the guitar example it returned **82.58 Hz for an ~83 Hz string (0.5 % error)**
where naive ACF failed badly
([TarsosDSP MPM docs](https://0110.be/releases/TarsosDSP/TarsosDSP-latest/TarsosDSP-latest-Documentation/be/tarsos/dsp/pitch/McLeodPitchMethod.html)).

### 1.5 Harmonic Product Spectrum (HPS)

FFT the frame, then multiply the magnitude spectrum by **downsampled copies of itself** (×2, ×3, …):
`HPS(f) = Π_{n=1..N} |X(n·f)|`. Harmonics reinforce at the true f0. Cheap and simple, robust to some
noise, but:
- **Frequency resolution is bin-limited**: at 48 kHz with a 4096 FFT, bins are **11.7 Hz** apart — far
  too coarse to distinguish E2 = 82.41 Hz from F2 = 87.31 Hz (a 4.9 Hz gap) without huge windows or
  interpolation ([29a.ch tuner](https://29a.ch/2020/04/15/guitar-tuner),
  [FFT bin calculator](https://www.cmuse.org/fft-bin-size-calculator)).
- Because it multiplies harmonics, HPS is prone to the **opposite octave error (too low)** when a
  missing/weak fundamental makes a sub-harmonic look strong. Best for a rough estimate or a coarse
  guide that a time-domain method then refines.

### 1.6 Cepstrum

Take the **inverse FFT of the log-magnitude spectrum**; a peak in the resulting **quefrency** axis at
the pitch period gives f0 ([John D. Cook, "Cepstrum, quefrency, & pitch"](https://www.johndcook.com/blog/2016/05/18/cepstrum-quefrency-and-pitch/)).
Cepstrum deconvolves excitation (pitch) from spectral envelope, so it is classic for **speech**.
For instruments it is only moderately accurate, quefrency resolution is again sample/bin-limited, and
it can pick a formant/rahmonic instead of f0 — another **octave-error** source. Rarely the best choice
for a string tuner.

### 1.7 Comparison table

| Algorithm | Domain | Typical accuracy (with interp.) | Latency (window need) | CPU cost | Overtone / octave robustness | Noise robustness | Notes |
|---|---|---|---|---|---|---|---|
| **Autocorrelation (naive)** | Time | ~few–tens of cents | ≥2 periods | Low (O(N²) direct, O(N log N) via FFT) | **Poor** — octave-up errors common | Fair | Baseline; needs many fixes to be usable |
| **YIN** | Time | **~1 cent** / gross err ~0.5 % | 25 ms window (≥2× τmax) | Moderate (O(N²) direct; **YINFFT/FastYin O(N log N)**) | **Very good** — CMNDF + abs. threshold kill octave errors | Good | Reference method; aubio default |
| **pYIN** | Time + HMM | Best-in-class, smooth track | Higher (HMM/Viterbi) | High | Excellent (multi-candidate) | Excellent | Overkill for real-time tuner |
| **MPM / NSDF** | Time | **~1 cent** / 0.5 % on 83 Hz string | ≥2 periods (~2 window periods) | Moderate (ACF via FFT) | **Very good** — first-peak ≥ k·max rule | Good; clarity measure gates noise | Works on rich harmonics **without LPF**; great for cello/violin |
| **HPS** | Freq | Bin-limited unless zero-padded/interp. | Long window for low f0 | Low | Prone to **octave-down** on weak f0 | Good | Good coarse guide, weak for low strings alone |
| **Cepstrum** | Freq | Moderate, quefrency-limited | Long window | Moderate (2× FFT) | Moderate; formant confusion | Good (log spectrum) | Best for speech, not strings |

**Why YIN and MPM win for instrument tuners.** Both are *periodicity* detectors that measure the
signal against a lag-shifted copy of itself, so they lock onto the **true fundamental period** even
when the fundamental is weak and overtones are strong — the norm for bowed cello and plucked guitar.
Their central innovations are explicitly **anti-octave-error**: YIN's cumulative-mean normalization
plus absolute-threshold "first dip" selection, and MPM's "first peak ≥ k · highest peak" rule. Both
give a **built-in confidence** (YIN's aperiodicity / d′ value; MPM's NSDF clarity in [−1,1]) that a
tuner uses to reject bad frames. After **parabolic interpolation** of the chosen lag they reach
**sub-sample period resolution → ~1 cent or better**, which pure FFT bin-picking cannot match at low
frequencies without impractically long windows. MPM's extra edge for strings: it needs **no low-pass
pre-filter** even on very harmonic-rich sources.

---

## 2. The low-frequency challenge and window/rate sizing

The physics: a time-domain periodicity detector must observe **at least two full periods** (in
practice 3–5 for a stable, well-normalized estimate) inside its analysis window, and its lag search
must extend to **τmax = fs / f0,min**.

Target strings:

| Instrument | String | f0 (Hz) | Period (ms) | Period (samples @48k) | Period (samples @44.1k) |
|---|---|---|---|---|---|
| Cello | **C2** | **65.41** | 15.29 | 734 | 674 |
| Cello | G2 | 98.00 | 10.20 | 490 | 450 |
| Cello | D3 | 146.83 | 6.81 | 327 | 300 |
| Cello | A3 | 220.00 | 4.55 | 218 | 200 |
| Guitar | **E2** | **82.41** | 12.13 | 582 | 535 |
| Guitar | A2 | 110.00 | 9.09 | 436 | 401 |
| Guitar | D3 | 146.83 | 6.81 | 327 | 300 |
| Guitar | G3 | 196.00 | 5.10 | 245 | 225 |
| Guitar | B3 | 246.94 | 4.05 | 194 | 179 |
| Guitar | E4 | 329.63 | 3.03 | 146 | 134 |

**Design for a floor of ~60 Hz** (a little below cello C2 for headroom): period ≈ 16.7 ms ≈ **800
samples @ 48 kHz**. Then:

- **τmax ≥ 800 samples**, so the analysis window must be **≥ 2 × τmax ≈ 1600 samples** just to
  compute the difference/NSDF out to that lag. For a *stable* low-note estimate you want ~4–5 periods,
  i.e. **~3200–4000 samples**.
- ⇒ **Window = 4096 samples** is the sweet spot: **85 ms @ 48 kHz** (93 ms @ 44.1 kHz), giving ~5.1
  periods at 60 Hz and ~6.9 at E2 — comfortably stable while still updating fast enough to feel
  responsive. (2048 samples ≈ 43 ms works down to ~E2 but is marginal at cello C2 — only ~2.6 periods
  at 60 Hz — so prefer 4096 when the cello low C matters.)

**The latency ↔ low-frequency accuracy trade-off.** Lower target f0 ⇒ longer required window ⇒ higher
latency and slower visual response. FFT methods make this worse: raw bin spacing is `fs/N`, so
resolving E2 vs F2 (4.9 Hz) at 48 kHz needs **N ≳ 16384 (2.9 Hz bins)** — ~340 ms of audio — which is
sluggish ([29a.ch](https://29a.ch/2020/04/15/guitar-tuner)). Time-domain YIN/MPM sidestep this: a
±1-sample lag error at 82 Hz/48 kHz is already only ~0.19 Hz (~4 cents), and **parabolic interpolation
removes even that**, so a 4096 window gives ~1-cent accuracy with far less audio than FFT needs.

**Recommended capture/analysis settings**

| Parameter | Value | Rationale |
|---|---|---|
| Sample rate | **48 000 Hz** (44 100 acceptable) | 48 kHz is the native rate on most Android/iOS hardware → no resampling; either rate easily covers the ≤1.3 kHz band of interest |
| Analysis window (buffer) | **4096 samples** (~85 ms @48k) | ~5 periods at 60 Hz; stable cello-C2 estimate; ~1-cent capable with interpolation |
| Hop size | **1024 samples** (75 % overlap) | New estimate every ~21 ms → smooth, responsive meter without recomputing every sample |
| Ring buffer | ≥ 2 × window (e.g. 8192) | Decouple audio-callback block size from analysis window; assemble 4096-sample frames as hops arrive |
| Lag search range | τ ∈ [fs/1320, fs/55] ≈ [36, 873] @48k | Covers ~55–1320 Hz (below cello C2 to above guitar E4 + a little harmonic margin) |

---

## 3. Accuracy techniques

- **Band-limit pre-filter.** Apply a band-pass (~**60–1300 Hz**; e.g. a 2nd–4th-order Butterworth or
  biquad HPF at ~55 Hz + LPF at ~1.3 kHz) to remove mains hum/rumble below and hiss/overtone clutter
  above the playable range. Reduces octave-up errors and improves SNR. (MPM tolerates skipping the LPF;
  a gentle HPF to kill DC/rumble is still worthwhile.)
- **Windowing.** For any FFT-based step apply a Hann window to cut spectral leakage. For pure ACF/NSDF
  a rectangular window is fine; NSDF's normalization is specifically designed to be **robust to window
  edge effects**.
- **FFT for speed, not resolution.** Compute the ACF/difference function via FFT
  (`ACF = IFFT(|FFT(x)|²)`) so cost is **O(N log N)** — this is exactly what YINFFT/FastYin and MPM
  implementations do. **Zero-pad to 2N** before the FFT to get a *linear* (not circular) autocorrelation.
- **Parabolic / quadratic interpolation** (the key sub-sample trick). Around the chosen peak/dip at
  integer lag τ with neighbors y(τ−1), y(τ), y(τ+1), the refined offset is
  `δ = 0.5·(y(τ−1) − y(τ+1)) / (y(τ−1) − 2y(τ) + y(τ+1))`, giving true lag `τ* = τ + δ` and
  `f0 = fs / τ*`. This yields **sub-sample period → ~0.1 Hz → well under 1 cent** at low frequencies,
  and is what makes both YIN and MPM tuner-grade. (At 82.41 Hz one cent is only **~0.048 Hz**, so
  interpolation is mandatory.)
- **Avoiding octave errors.** (1) Use the algorithms' native defenses — YIN absolute threshold /
  first dip; MPM "first peak ≥ k·max" with k≈0.9. (2) Reject frames whose confidence is low (NSDF
  clarity < ~0.5, or YIN aperiodicity > threshold). (3) **Octave sanity gate**: constrain the accepted
  f0 to the plausible string range and, on a jump, verify it is not an exact 2× / 0.5× of the previous
  stable reading before accepting. (4) Optional harmonic check: confirm energy exists at 2·f0, 3·f0.

---

## 4. Frequency → note name + cents

Standard equal temperament, **A4 = 440 Hz** (make the reference configurable, e.g. 432/442):

```
# Continuous MIDI-ish pitch number
n_float = 69 + 12 * log2(f / 440)

# Nearest note
n = round(n_float)                       # nearest MIDI note number
cents = 100 * (n_float - n)              # signed: + = sharp, - = flat, range (-50, +50]

# Equivalent direct cents form against the nearest note's frequency:
f_note = 440 * 2^((n - 69) / 12)
cents  = 1200 * log2(f / f_note)

# Name + octave
names = [C, C#, D, D#, E, F, F#, G, G#, A, A#, B]
name   = names[n mod 12]
octave = (n / 12) - 1                    # MIDI 60 = C4 (middle C)
```

References: [MIDI = 69 + 12·log2(f/440)](https://www.cmuse.org/frequency-to-note-converter),
[cents = 1200·log2(f2/f1)](https://caesarcipher.org/calculators/frequency-to-note-calculator).

**UI convention.** Show the detected **note name + octave** (e.g. "E2"), a needle/arc centered at 0,
deflecting **left = flat (negative cents), right = sharp (positive cents)**, typically ±50 cents full
scale. Turn the indicator green / light an "in-tune" LED within **±3–5 cents**. Smooth the displayed
value (median-of-5 then a light EMA on cents) so the needle is steady, and show the clarity/confidence
so the meter goes idle when no clear note is sounding.

---

## 5. Open-source references and reusable implementations

- **aubio** (C, LGPL) — methods `yinfft` (default), `yin`, `yinfast`, `fcomb`, `mcomb`, `schmitt`,
  `specacf`; default **buffer 2048, hop 256**; YINFFT tolerance 0.85, YIN 0.15; YINFast is O(N log N)
  identical to YIN. Callable from C/C++ (KMP via cinterop / Oboe C++ layer).
  [manual](https://aubio.org/manual/latest/cli.html),
  [pitch.h](https://github.com/aubio/aubio/blob/master/src/pitch/pitch.h).
- **TarsosDSP** (pure Java, GPL — mind licensing for app distribution) — `Yin`, `FastYin`
  (FFT-based, ~3× faster, contributed by M. Mauch), `McLeodPitchMethod`, plus a wavelet tracker.
  Pure-Java, no native deps → drops straight into Android/JVM; there are Android/Kotlin forks
  (e.g. [TarsosDSPAndroid](https://github.com/AnandaAp/TarsosDSPAndroid)).
  [repo](https://github.com/JorenSix/TarsosDSP),
  [MPM javadoc](https://0110.be/releases/TarsosDSP/TarsosDSP-latest/TarsosDSP-latest-Documentation/be/tarsos/dsp/pitch/McLeodPitchMethod.html).
- **iOS / Swift** — [AudioKit `PitchTap`](https://github.com/AudioKit/AudioKit/blob/v5.1.0/Sources/AudioKit/Taps/PitchTap.swift)
  (taps an `AVAudioEngine` node, returns frequency+amplitude callbacks) and
  [Beethoven](https://github.com/vadymmarkov/Beethoven) (Swift; YIN and other strategies; uses
  `Pitchy` for note/octave/cents). Also [sevagh/pitch-detection](https://github.com/sevagh/pitch-detection)
  (C++ MPM + YIN, MIT) — a clean, portable core usable from both platforms.
- **KMP strategy.** The cleanest cross-platform option is a **single shared pitch-detection core**:
  either (a) port ~200 lines of MPM/YIN to **common Kotlin** (only needs an FFT — e.g. a small
  Kotlin radix-2 FFT in `commonMain`) so *one* implementation serves both targets, or (b) wrap a
  C/C++ core (aubio or sevagh) via **cinterop** and share it. Keep only **mic capture** in
  platform code.

**Mic capture.**
- **Android:** low-latency input via **[Oboe](https://developer.android.com/games/sdk/oboe/low-latency-audio)**
  (C++; wraps AAudio on API 27+, OpenSL ES below) with `PerformanceMode::LowLatency` +
  `SharingMode::Exclusive`; or pure-Kotlin **`AudioRecord`** (`MediaRecorder.AudioSource.UNPROCESSED`
  or `VOICE_RECOGNITION` to avoid AGC/noise-suppression coloring the pitch), 48 kHz, mono, PCM float.
  Feed blocks into the ring buffer.
- **iOS:** **AVAudioEngine** — `engine.inputNode.installTap(onBus:0, bufferSize:, format:)`; the tap
  delivers `AVAudioPCMBuffer` frames you copy into the ring buffer. Set the `AVAudioSession` category
  to `.record`/`.playAndRecord` with a low `preferredIOBufferDuration`.

---

## 6. Pipeline spec (mic → buffer → filter → detect → smooth → note+cents)

Turn this into the tuner's block diagram.

```
┌────────────┐   PCM float @48k, mono
│  MIC INPUT │   Android: Oboe/AAudio (LowLatency, Exclusive) or AudioRecord (UNPROCESSED)
│  (capture) │   iOS:     AVAudioEngine inputNode.installTap(onBus:0)
└─────┬──────┘
      │ callback blocks (e.g. 256–512 frames)
      ▼
┌────────────────────┐
│  RING BUFFER        │  size ≥ 8192; assemble analysis frames
│  frame=4096, hop=1024 (75% overlap) → new frame every ~21 ms
└─────┬──────────────┘
      ▼
┌────────────────────┐
│  PRE-FILTER         │  DC removal + band-pass ~60–1300 Hz (biquad HPF@55 + LPF@1300)
│  (+ optional Hann)  │  remove rumble/hum/hiss; improve SNR & cut octave errors
└─────┬──────────────┘
      ▼
┌────────────────────┐
│  PITCH DETECTION    │  PRIMARY: MPM/NSDF  (or YIN/YINFFT)
│                     │   1. ACF/difference via FFT (zero-pad to 2N) — O(N log N)
│                     │   2. NSDF = 2r(τ)/m(τ)  [-1..1]   |  YIN: CMNDF d'(τ)
│                     │   3. peak/dip pick: first peak ≥ k·max (k≈0.9)  | YIN: first dip < 0.10–0.15
│                     │   4. PARABOLIC INTERPOLATION → τ*  → f0 = fs/τ*
│                     │   5. confidence = NSDF clarity  (| YIN aperiodicity)
│  lag range τ ∈ [36, 873] samples ≈ 55–1320 Hz
└─────┬──────────────┘
      │ f0, confidence
      ▼
┌────────────────────┐
│  VALIDATION GATE    │  reject if confidence < ~0.5 OR f0 outside 55–1320 Hz
│                     │  octave sanity: reject exact 2×/0.5× jumps vs last stable f0
└─────┬──────────────┘
      ▼
┌────────────────────┐
│  SMOOTHING          │  median-of-5 on f0 (kills outliers) → light EMA on cents (steady needle)
└─────┬──────────────┘
      ▼
┌────────────────────┐
│  NOTE + CENTS       │  n = round(69 + 12·log2(f0/A4));  cents = 100·(n_float − n)
│                     │  name = names[n mod 12];  octave = n/12 − 1
└─────┬──────────────┘
      ▼
┌────────────────────┐
│  UI                 │  note+octave; needle L=flat / R=sharp, ±50¢; green within ±3–5¢;
│                     │  show confidence → idle when no clear note
└────────────────────┘
```

### Concrete recommended configuration

| Stage | Setting |
|---|---|
| Sample rate | 48 000 Hz (mono, float32) |
| Window / hop | 4096 / 1024 samples (75 % overlap; ~21 ms update) |
| Pre-filter | biquad HPF ≈55 Hz + LPF ≈1300 Hz (band-pass), DC removal |
| Primary algorithm | **MPM / NSDF**, peak threshold k ≈ 0.9, parabolic interpolation |
| Alternative | YIN / YINFFT, threshold 0.10–0.15 (cross-check or user option) |
| Lag search | τ ∈ [36, 873] samples → ~55–1320 Hz |
| Confidence gate | NSDF clarity ≥ 0.5 (else "no note") |
| Smoothing | median-of-5 f0 → EMA on cents (α ≈ 0.2) |
| Reference | A4 = 440 Hz (configurable 432/442) |
| In-tune window | ±3–5 cents |
| Libraries | TarsosDSP (Android/JVM), AudioKit/Beethoven or shared MPM core (iOS); or one KMP `commonMain` port |

---

## Sources

- de Cheveigné & Kawahara, *YIN, a fundamental frequency estimator for speech and music*, JASA 111(4), 2002 — http://audition.ens.fr/adc/pdf/2002_JASA_YIN.pdf
- McLeod & Wyvill, *A Smarter Way to Find Pitch*, ICMC 2005 — https://quod.lib.umich.edu/i/icmc/bbp2372.2005.107 · PDF: http://dl.icdst.org/pdfs/files4/b56e1f975f0b9b3fca904fb2a7778c15.pdf
- Mauch & Dixon, *pYIN: A fundamental frequency estimator using probabilistic threshold distributions*, ICASSP 2014 — https://webspace.eecs.qmul.ac.uk/s.e.dixon/pub/2014/MauchDixon-PYIN-ICASSP2014.pdf
- aubio manual (pitch methods & defaults) — https://aubio.org/manual/latest/cli.html · pitch.h — https://github.com/aubio/aubio/blob/master/src/pitch/pitch.h
- TarsosDSP — https://github.com/JorenSix/TarsosDSP · MPM javadoc — https://0110.be/releases/TarsosDSP/TarsosDSP-latest/TarsosDSP-latest-Documentation/be/tarsos/dsp/pitch/McLeodPitchMethod.html
- sevagh/pitch-detection (C++ MPM/YIN) — https://github.com/sevagh/pitch-detection · MPM notes — https://github.com/sevagh/pitch-detection/blob/master/misc/mcleod/README.md
- AudioKit PitchTap — https://github.com/AudioKit/AudioKit/blob/v5.1.0/Sources/AudioKit/Taps/PitchTap.swift · Beethoven — https://github.com/vadymmarkov/Beethoven
- Oboe low-latency audio — https://developer.android.com/games/sdk/oboe/low-latency-audio
- FFT resolution / low-frequency tuner discussion — https://29a.ch/2020/04/15/guitar-tuner · https://www.cmuse.org/fft-bin-size-calculator
- Frequency→note/cents formulas — https://www.cmuse.org/frequency-to-note-converter · https://caesarcipher.org/calculators/frequency-to-note-calculator
- Cepstrum / quefrency — https://www.johndcook.com/blog/2016/05/18/cepstrum-quefrency-and-pitch/
