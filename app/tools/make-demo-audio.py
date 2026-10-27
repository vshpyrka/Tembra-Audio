#!/usr/bin/env python3
"""Synthesises the demo project's four loops (feature 009, research R18): project-owned content, no licence row.

    uv run --with numpy python tools/make-demo-audio.py

Writes app/shared/ui/src/commonMain/composeResources/files/demo/{drums,bass,keys,pad}.wav: sixteen bars of 4/4 at
124 BPM (30.9677 s) at 48 kHz, 16-bit PCM; drums and bass mono, keys and pad stereo. The key is D minor
(i–VI–III–VII: Dm, Bb, F, C, one chord per bar, repeating every four bars). Deterministic: every run writes the same
bytes, so the committed files and the script never drift.
"""
import os
import struct

import numpy as np

RATE = 48000
BPM = 124
BEATS = 16 * 4
BEAT = 60.0 / BPM
LENGTH = BEATS * BEAT  # 30.9677 s
FRAMES = int(round(LENGTH * RATE))
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "shared", "ui", "src", "commonMain", "composeResources", "files", "demo")

# Chords in D minor as MIDI note numbers (root in the bass octave), one per bar, repeating.
PROGRESSION = [
    (50, [62, 65, 69]),  # Dm
    (46, [58, 62, 65]),  # Bb
    (41, [57, 60, 65]),  # F
    (48, [60, 64, 67]),  # C
]


def hz(midi):
    return 440.0 * 2.0 ** ((midi - 69) / 12.0)


def envelope(n, attack, decay, sustain=0.0, release=0.0):
    """A simple linear-attack, exponential-decay envelope of n samples."""
    t = np.arange(n) / RATE
    env = np.where(t < attack, t / max(attack, 1e-9), np.exp(-(t - attack) / max(decay, 1e-9)) * (1 - sustain) + sustain)
    if release > 0:
        tail = int(release * RATE)
        if tail < n:
            env[n - tail:] *= np.linspace(1.0, 0.0, tail)
    return env


def place(buffer, start_sec, sound, gain=1.0):
    start = int(round(start_sec * RATE))
    end = min(len(buffer), start + len(sound))
    if end > start:
        buffer[start:end] += gain * sound[: end - start]


def kick():
    n = int(0.22 * RATE)
    t = np.arange(n) / RATE
    freq = 55.0 + 110.0 * np.exp(-t * 28.0)
    phase = 2.0 * np.pi * np.cumsum(freq) / RATE
    return np.sin(phase) * envelope(n, 0.001, 0.09)


def snare(rng):
    n = int(0.16 * RATE)
    t = np.arange(n) / RATE
    body = np.sin(2.0 * np.pi * 190.0 * t) * envelope(n, 0.001, 0.05)
    noise = rng.standard_normal(n) * envelope(n, 0.001, 0.07)
    return 0.6 * body + 0.5 * noise


def hat(rng, closed=True):
    n = int((0.035 if closed else 0.14) * RATE)
    noise = rng.standard_normal(n)
    # A crude high-pass: first difference twice.
    noise = np.diff(noise, n=2, prepend=[0.0, 0.0])
    return noise * envelope(n, 0.0005, 0.012 if closed else 0.06) * 0.35


def drums():
    rng = np.random.default_rng(9)
    out = np.zeros(FRAMES)
    for bar in range(16):
        for beat in range(4):
            t0 = (bar * 4 + beat) * BEAT
            if beat in (0, 2):
                place(out, t0, kick(), 0.9)
            if beat in (1, 3):
                place(out, t0, snare(rng), 0.55)
            # The last bar of every four adds a pickup kick on the "and" of 4.
            if bar % 4 == 3 and beat == 3:
                place(out, t0 + BEAT / 2, kick(), 0.7)
            place(out, t0, hat(rng, closed=True), 0.7)
            place(out, t0 + BEAT / 2, hat(rng, closed=(bar % 2 == 0 or beat != 3)), 0.5)
    return out


def bass():
    out = np.zeros(FRAMES)
    for bar in range(16):
        root, _ = PROGRESSION[bar % 4]
        for eighth in range(8):
            t0 = (bar * 4) * BEAT + eighth * BEAT / 2
            note = root + (12 if eighth in (3, 7) else 0)
            n = int(BEAT / 2 * 0.95 * RATE)
            t = np.arange(n) / RATE
            f = hz(note)
            # Two detuned saw-ish partial sums, low-passed by keeping few harmonics.
            wave = sum(np.sin(2.0 * np.pi * f * k * t) / k for k in range(1, 6))
            place(out, t0, wave * envelope(n, 0.004, 0.25, sustain=0.3, release=0.02), 0.28)
    return out


def rhodes(freq, seconds):
    n = int(seconds * RATE)
    t = np.arange(n) / RATE
    tone = np.sin(2.0 * np.pi * freq * t) + 0.35 * np.sin(2.0 * np.pi * freq * 2.0 * t + 0.3) + 0.12 * np.sin(2.0 * np.pi * freq * 3.0 * t)
    return tone * envelope(n, 0.003, 0.6, sustain=0.1, release=0.05)


def keys():
    left = np.zeros(FRAMES)
    right = np.zeros(FRAMES)
    for bar in range(16):
        _, chord = PROGRESSION[bar % 4]
        hits = [(0.0, 0.9), (1.5, 0.6), (2.5, 0.55), (3.0, 0.5)] if bar % 2 == 0 else [(0.0, 0.8), (1.5, 0.6), (3.5, 0.55)]
        for offset, gain in hits:
            t0 = (bar * 4 + offset) * BEAT
            for i, note in enumerate(chord):
                sound = rhodes(hz(note), 0.9)
                detuned = rhodes(hz(note) * 1.0015, 0.9)
                pan = (i - 1) * 0.3
                place(left, t0, sound, gain * 0.22 * (1.0 - pan))
                place(right, t0, detuned, gain * 0.22 * (1.0 + pan))
    return np.stack([left, right], axis=1)


def pad():
    t = np.arange(FRAMES) / RATE
    left = np.zeros(FRAMES)
    right = np.zeros(FRAMES)
    for bar in range(16):
        _, chord = PROGRESSION[bar % 4]
        start = int(bar * 4 * BEAT * RATE)
        end = min(FRAMES, int((bar + 1) * 4 * BEAT * RATE) + int(0.4 * RATE))
        n = end - start
        tt = t[:n]
        env = envelope(n, 0.35, 10.0, sustain=0.9, release=0.4)
        for i, note in enumerate(chord + [chord[0] + 12]):
            f = hz(note)
            lfo_l = 1.0 + 0.0025 * np.sin(2.0 * np.pi * 0.17 * tt + i)
            lfo_r = 1.0 + 0.0025 * np.sin(2.0 * np.pi * 0.13 * tt + i + 1.5)
            saw_l = sum(np.sin(2.0 * np.pi * f * lfo_l * k * tt) / k for k in range(1, 5))
            saw_r = sum(np.sin(2.0 * np.pi * f * lfo_r * k * tt) / k for k in range(1, 5))
            left[start:end] += 0.07 * env * saw_l
            right[start:end] += 0.07 * env * saw_r
    # Gentle fades at the loop edges so a looped playback never clicks.
    fade = int(0.02 * RATE)
    left[:fade] *= np.linspace(0, 1, fade)
    right[:fade] *= np.linspace(0, 1, fade)
    left[-fade:] *= np.linspace(1, 0, fade)
    right[-fade:] *= np.linspace(1, 0, fade)
    return np.stack([left, right], axis=1)


def write_wav(path, samples, peak=0.5):
    data = np.asarray(samples, dtype=np.float64)
    if data.ndim == 1:
        data = data[:, None]
    data = data * (peak / max(np.max(np.abs(data)), 1e-9))
    pcm = np.clip(np.round(data * 32767.0), -32768, 32767).astype("<i2")
    channels = pcm.shape[1]
    body = pcm.tobytes()
    header = b"RIFF" + struct.pack("<I", 36 + len(body)) + b"WAVE"
    header += b"fmt " + struct.pack("<IHHIIHH", 16, 1, channels, RATE, RATE * channels * 2, channels * 2, 16)
    header += b"data" + struct.pack("<I", len(body))
    with open(path, "wb") as f:
        f.write(header + body)


def main():
    os.makedirs(OUT, exist_ok=True)
    write_wav(os.path.join(OUT, "drums.wav"), drums(), peak=0.7)
    write_wav(os.path.join(OUT, "bass.wav"), bass(), peak=0.5)
    write_wav(os.path.join(OUT, "keys.wav"), keys(), peak=0.45)
    write_wav(os.path.join(OUT, "pad.wav"), pad(), peak=0.35)
    print("demo audio written to", os.path.normpath(OUT), "frames per loop:", FRAMES)


if __name__ == "__main__":
    main()
