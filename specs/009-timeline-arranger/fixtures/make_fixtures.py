#!/usr/bin/env python3
"""Generates the audio fixtures of feature 009 (tasks T009, T017, T021, T022): synthesised tones, our own content.

Writes beside this script (committed, all under 1 MB together):
  tone-48k-stereo.wav    1 s, 48 kHz, 16-bit, stereo: 440 Hz left, 660 Hz right
  tone-44k1-mono-16.wav  1 s, 44.1 kHz, 16-bit, mono, 440 Hz
  tone-8bit-8k.wav       1 s, 8 kHz, 8-bit unsigned, mono, 440 Hz
  tone-float32-192k.wav  0.25 s, 192 kHz, 32-bit float, stereo, 440 Hz
  silence-5s.wav         5 s, 48 kHz, 16-bit, mono, digital silence
  tone-4ch.wav           0.5 s, 48 kHz, 16-bit, four channels (refused by the importer: too many channels)
  tone.flac              tone-48k-stereo.wav encoded losslessly (ffmpeg, or afconvert on macOS)
  tone.mp3               tone-48k-stereo.wav at 128 kbps (ffmpeg, or the lameenc wheel: `uv run --with lameenc python make_fixtures.py`)
  not-audio.txt          a text file (refused by the decoder)
  truncated.wav          tone-48k-stereo.wav cut after 100 bytes (refused by the decoder)

`--long <dir>` additionally writes long-31min.wav (31 min, 48 kHz, 16-bit, mono; refused by the importer as too long,
and the overview builder's partial-header case) into <dir>; it is never committed.

The tones carry a 1 s linear fade in and out only on the long file; the short fixtures are plain so the decoder tests
can compare phase after a seek.
"""
import argparse
import math
import os
import shutil
import struct
import subprocess
import sys
import wave

HERE = os.path.dirname(os.path.abspath(__file__))


def write_wav(path, rate, channels, sampwidth, frames, sample):
    """Writes PCM WAV; `sample(i, ch)` returns -1..1. sampwidth 1 = 8-bit unsigned, 2 = 16-bit, 4 = float32."""
    if sampwidth == 4:
        write_float_wav(path, rate, channels, frames, sample)
        return
    with wave.open(path, "wb") as w:
        w.setnchannels(channels)
        w.setsampwidth(sampwidth)
        w.setframerate(rate)
        out = bytearray()
        for i in range(frames):
            for ch in range(channels):
                v = max(-1.0, min(1.0, sample(i, ch)))
                if sampwidth == 1:
                    out += struct.pack("<B", int(round((v + 1.0) * 127.5)))
                else:
                    out += struct.pack("<h", int(round(v * 32767)))
        w.writeframes(bytes(out))


def write_float_wav(path, rate, channels, frames, sample):
    """WAVE_FORMAT_IEEE_FLOAT (3), 32-bit, written by hand: the wave module only knows PCM."""
    data = bytearray()
    for i in range(frames):
        for ch in range(channels):
            data += struct.pack("<f", max(-1.0, min(1.0, sample(i, ch))))
    block = channels * 4
    header = b"RIFF" + struct.pack("<I", 36 + len(data)) + b"WAVE"
    header += b"fmt " + struct.pack("<IHHIIHH", 16, 3, channels, rate, rate * block, block, 32)
    header += b"data" + struct.pack("<I", len(data))
    with open(path, "wb") as f:
        f.write(header + bytes(data))


def tone(freqs, rate, amplitude=0.5):
    return lambda i, ch: amplitude * math.sin(2.0 * math.pi * freqs[ch % len(freqs)] * i / rate)


def encode_flac(wav, flac):
    if shutil.which("ffmpeg"):
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "flac", flac], check=True)
    elif shutil.which("afconvert"):
        subprocess.run(["afconvert", "-f", "flac", "-d", "flac", wav, flac], check=True)
    else:
        sys.exit("FLAC: install ffmpeg (or run on macOS, which has afconvert)")


def encode_mp3(wav, mp3):
    if shutil.which("ffmpeg"):
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libmp3lame", "-b:a", "128k", mp3], check=True)
        return
    if shutil.which("lame"):
        subprocess.run(["lame", "--quiet", "-b", "128", wav, mp3], check=True)
        return
    try:
        import lameenc  # noqa: WPS433 (optional wheel)
    except ImportError:
        sys.exit("MP3: install ffmpeg or lame, or run `uv run --with lameenc python make_fixtures.py`")
    with wave.open(wav, "rb") as w:
        rate, channels, pcm = w.getframerate(), w.getnchannels(), w.readframes(w.getnframes())
    encoder = lameenc.Encoder()
    encoder.set_bit_rate(128)
    encoder.set_in_sample_rate(rate)
    encoder.set_channels(channels)
    encoder.set_quality(2)
    with open(mp3, "wb") as f:
        f.write(encoder.encode(pcm))
        f.write(encoder.flush())


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--long", metavar="DIR", help="also write long-31min.wav into DIR (not committed)")
    parser.add_argument("--out", default=HERE, help="output directory for the committed fixtures (default: beside the script)")
    args = parser.parse_args()
    out = args.out
    os.makedirs(out, exist_ok=True)

    stereo = os.path.join(out, "tone-48k-stereo.wav")
    write_wav(stereo, 48000, 2, 2, 48000, tone([440.0, 660.0], 48000))
    write_wav(os.path.join(out, "tone-44k1-mono-16.wav"), 44100, 1, 2, 44100, tone([440.0], 44100))
    write_wav(os.path.join(out, "tone-8bit-8k.wav"), 8000, 1, 1, 8000, tone([440.0], 8000))
    write_wav(os.path.join(out, "tone-float32-192k.wav"), 192000, 2, 4, 48000, tone([440.0, 440.0], 192000))
    write_wav(os.path.join(out, "silence-5s.wav"), 48000, 1, 2, 5 * 48000, lambda i, ch: 0.0)
    write_wav(os.path.join(out, "tone-4ch.wav"), 48000, 4, 2, 24000, tone([440.0, 550.0, 660.0, 770.0], 48000))
    encode_flac(stereo, os.path.join(out, "tone.flac"))
    encode_mp3(stereo, os.path.join(out, "tone.mp3"))
    with open(os.path.join(out, "not-audio.txt"), "w") as f:
        f.write("This is not an audio file; the decoder must refuse it.\n")
    with open(stereo, "rb") as src, open(os.path.join(out, "truncated.wav"), "wb") as dst:
        dst.write(src.read(100))

    if args.long:
        os.makedirs(args.long, exist_ok=True)
        rate, seconds = 48000, 31 * 60
        frames = rate * seconds

        def fading(i, ch):
            fade = min(1.0, i / rate, (frames - 1 - i) / rate)
            return 0.5 * fade * math.sin(2.0 * math.pi * 220.0 * i / rate)

        write_wav(os.path.join(args.long, "long-31min.wav"), rate, 1, 2, frames, fading)
    print("fixtures written to", out)


if __name__ == "__main__":
    main()
