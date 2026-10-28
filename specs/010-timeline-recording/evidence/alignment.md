# Take alignment and monitoring delay (feature 010, quickstart §8; T046, T089, T090)

The engine aligns every take by the input plus the output latency of the device in use
(`tembra_capture_snapshot.alignment_frames`); the loopback check measures what that leaves on the
file. The script is `app/tools/measure-take-alignment.py` (take mode: click onsets against the
beat at `--bpm`; `--monitor`: the delay between the first two transients of a direct-plus-monitored
recording).

## The engine's reported alignment (device gate T046)

The `NativeAudioEngineRecordingTest` device test logs the snapshot after the arm
(`adb logcat -d -s TembraRecordingTest`).

| Device | Route | Sample rate | `alignmentFrames` | ms | Input latency | Output latency | Date |
|---|---|---|---|---|---|---|---|
| Pixel 6 Pro (19021FDEE001AH) | built-in mic → speaker (muted stream) | _pending_ | _pending_ | _pending_ | _pending_ | _pending_ | 2026-10-28 |

## Loopback measurement (T090, the one sounding step)

Not yet run: it needs a loopback cable (or the acoustic variant at the lowest audible volume) and
is announced before it starts. Fill one row per device from the script's output.

| Device | Route | Clicks | Mean offset (ms) | Max abs offset (ms) | Within 10 ms | Counter `takeAlignmentMs` | Monitoring delay (ms) | Within 30 ms |
|---|---|---|---|---|---|---|---|---|
| Pixel 6 Pro | | | | | | | | |
| iPhone 14 | | | | | | | | |
| Pixel 7 Pro (no pass/fail) | | | | | | | | |
| Pixel Tablet (no pass/fail) | | | | | | | | |
| OPPO CPH2239 (no pass/fail) | | | | | | | | |
| Redmi Note 5 (no pass/fail) | | | | | | | | |
| Fold emulator (no pass/fail) | | | | | | | | |
