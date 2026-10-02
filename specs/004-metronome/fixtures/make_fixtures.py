"""Writes the quickstart §5 fixture WAVs (specs/004-metronome/quickstart.md) with the standard library only."""
import math, struct, wave, os, sys

out = sys.argv[1]
os.makedirs(out, exist_ok=True)

def click(rate, ms, freq=1800.0, tau_ms=None, amp=0.8):
    n = int(rate * ms / 1000)
    tau = (tau_ms or ms / 4) / 1000
    return [amp * math.sin(2 * math.pi * freq * i / rate) * math.exp(-(i / rate) / tau) for i in range(n)]

def pcm_int(path, rate, channels, bits, mono):
    frames = bytearray()
    full = (1 << (bits - 1)) - 1
    for x in mono:
        v = max(-1.0, min(1.0, x))
        for _ in range(channels):
            if bits == 8:
                frames += struct.pack('<B', int(round(v * 127)) + 128)
            elif bits == 16:
                frames += struct.pack('<h', int(round(v * full)))
            elif bits == 24:
                frames += int(round(v * full)).to_bytes(3, 'little', signed=True)
    with wave.open(path, 'wb') as w:
        w.setnchannels(channels); w.setsampwidth(bits // 8); w.setframerate(rate); w.writeframes(bytes(frames))

def pcm_float(path, rate, mono):
    data = b''.join(struct.pack('<f', x) for x in mono)
    fmt = struct.pack('<HHIIHH', 3, 1, rate, rate * 4, 4, 32)   # WAVE_FORMAT_IEEE_FLOAT, mono
    body = b'WAVE' + b'fmt ' + struct.pack('<I', len(fmt)) + fmt + b'fact' + struct.pack('<II', 4, len(mono)) + b'data' + struct.pack('<I', len(data)) + data
    with open(path, 'wb') as f:
        f.write(b'RIFF' + struct.pack('<I', len(body)) + body)

r = 48000
pcm_int(f'{out}/silence-then-click.wav', r, 1, 16, [0.0] * int(r * 0.1) + click(r, 20, tau_ms=5))
pcm_int(f'{out}/long-tail.wav', 22050, 1, 16, click(22050, 3000, freq=440.0, tau_ms=900, amp=0.7))
r96 = 96000
pcm_int(f'{out}/stereo-24bit-96k.wav', r96, 2, 24, [0.0] * int(r96 * 0.05) + click(r96, 120, freq=2500.0, tau_ms=25))
pcm_float(f'{out}/float32.wav', r, click(r, 80, freq=1200.0, tau_ms=15))
pcm_int(f'{out}/8bit-8k.wav', 8000, 1, 8, click(8000, 100, freq=900.0, tau_ms=20))
pcm_int(f'{out}/silent.wav', r, 1, 16, [0.0] * int(r * 0.5))
with open(f'{out}/notes.wav', 'w') as f:
    f.write('Rehearsal notes: bars 12-16 slower, count in two bars.\n')
for name in sorted(os.listdir(out)):
    print(name, os.path.getsize(f'{out}/{name}'))
