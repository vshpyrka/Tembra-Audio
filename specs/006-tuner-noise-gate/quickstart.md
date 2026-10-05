# Quickstart: verify the tuner noise gate

Run from `app/` unless noted. Gradle goes through the `gradle-run` wrapper (Constitution VIII) with
`--warning-mode all`; zero warnings is a gate. Results go into `specs/006-tuner-noise-gate/screenshots/NOTES.md`, one
row per target and orientation (data-model.md §10). A target or a step that cannot be driven is written down with
the reason and the command the owner can run; it is never dropped (FR-024).

Targets (attached 2026-10-05):

| Target | Id |
|---|---|
| Pixel 6 Pro | `19021FDEE001AH` |
| Pixel 9 Pro Fold emulator | `127.0.0.1:5556` |
| iPhone 17 simulator | `iPhone 17` |
| iPad Pro simulator | `iPad Pro 13-inch (M5)` |
| iPhone Duo simulator (iOS 27.1) | `EB59DDD2-6D13-4503-A77E-BDFB69BF8CCD` |

## 1. Automated lanes

```sh
./gradlew engineHostTest                                              # Catch2: level, gate, ABI 4
./gradlew checkAndroidHost                                            # JVM: NoiseGate, preferences, ViewModel, parsers
./gradlew checkIos "-Ptembra.iosSimulator=iPhone 17"                  # shared tests, uiTest, engine end-to-end
./gradlew checkIos "-Ptembra.iosSimulator=iPad Pro 13-inch (M5)"
./gradlew checkIos -Ptembra.iosSimulator=EB59DDD2-6D13-4503-A77E-BDFB69BF8CCD    # iPhone Duo
ANDROID_SERIAL=19021FDEE001AH ./gradlew checkAndroidDevice            # Pixel 6 Pro
ANDROID_SERIAL=127.0.0.1:5556 ./gradlew checkAndroidDevice            # Pixel 9 Pro Fold emulator
```

Expected: all green with zero warnings. `NoiseGateEndToEndTest` passes its sixteen combinations and the live change on
both Android targets and all three simulators (SC-009). `TunerSignalEndToEndTest` passes with eighteen entries. Every assertion the earlier tuner and metronome tests make about readings and screens still holds; only the tests that pin the engine's revision
number, the number of test signals or the shape of the remembered choices were edited (SC-002). On the fold emulator
`MetronomeTimingEndToEndTest` is expected to fail on the emulator's audio clock; confirm it fails the same way on
`main` and record both runs (FR-024).

## 2. Build and install

```sh
ANDROID_SERIAL=19021FDEE001AH ./gradlew :androidApp:installDebug
ANDROID_SERIAL=127.0.0.1:5556 ./gradlew :androidApp:installDebug
```

```sh
cd iosApp
for DEVICE in "iPhone 17" "iPad Pro 13-inch (M5)"; do
  xcodebuild -project iosApp.xcodeproj -scheme iosApp -configuration Debug \
    -destination "platform=iOS Simulator,name=$DEVICE" -derivedDataPath build build
  xcrun simctl install "$DEVICE" "build/Build/Products/Debug-iphonesimulator/Tembra Audio.app"
done
BUNDLE=$(/usr/libexec/PlistBuddy -c "Print :CFBundleIdentifier" "build/Build/Products/Debug-iphonesimulator/Tembra Audio.app/Info.plist")
# iPhone Duo: the same two commands with Xcode 27.1 beta, so the app fills both displays (docs/research/iphone-duo.md)
DEVELOPER_DIR=/Applications/Xcode-27.1-beta.app/Contents/Developer xcodebuild -project iosApp.xcodeproj -scheme iosApp \
  -configuration Debug -destination 'platform=iOS Simulator,name=iPhone Duo' -derivedDataPath build-duo build
```

## 3. The gate with a test signal (no microphone)

```sh
# Android: test signal and gate for this launch (contracts/tuner-screen.md §6)
adb -s <serial> shell am start -n audio.tembra.app/audio.tembra.android.MainActivity \
  --es tembra.tuner.testSignal e2-55db --es tembra.tuner.noiseGate -50
# iOS
xcrun simctl launch "<device>" "$BUNDLE" --tembra-tuner-test-signal=e2-55db --tembra-tuner-noise-gate=-50
```

| Launch | Expected |
|---|---|
| `e2-in-tune`, gate `off` | E2, "In tune" lit; handle at "Off"; fill in the active look |
| `e2-55db`, gate `-50` | "Listening"; fill in the muted look, ending short of the handle |
| `e2-45db`, gate `-50` | E2; fill in the active look, past the handle |
| `e2-45db`, gate `-50`, then drag the handle past the fill and back | The note goes as the handle passes the end of the fill and returns as it comes back; the "Test signal" pill never disappears (FR-003, User Story 3.3) |
| any, then By Ear | No noise gate control; back in Auto Listen the handle is where it was |

## 4. Orientation, posture and "is it on the screen"

For every target, in each orientation and posture of data-model.md §10, run the capture tool. It makes the three
captures of contracts/tuner-screen.md §8 and, on Android, checks from the layout dump that the control lies inside
the display and touches neither the gauge nor a selector.

```sh
tools/capture-noise-gate.sh --android 19021FDEE001AH --orientation upright
tools/capture-noise-gate.sh --android 19021FDEE001AH --orientation sideways
tools/capture-noise-gate.sh --ios "iPhone 17" --orientation upright      # rotate the simulator first for sideways
# The fold emulator has two displays: name the inner one, and label a posture (set it first, see the table)
tools/capture-noise-gate.sh --android 127.0.0.1:5556 --display 4619827259835644672 --name pixel-9-pro-fold \
  --orientation sideways --label tabletop
# The iPhone Duo likewise: the display's UUID comes from `xcrun simctl io <id> enumerate` and changes with a reboot
tools/capture-noise-gate.sh --ios EB59DDD2-6D13-4503-A77E-BDFB69BF8CCD --name iphone-duo --display <uuid> \
  --orientation upright --label closed-upright
```

The mode is remembered: a target left in By Ear shows no control and the tool says so. Choose Auto Listen on it once.
The steps that go through the Simulator or Device Hub windows (turning, poses, the keyboard) need the Mac's screen
unlocked; it locks after ten minutes without its owner.

How each target is turned:

| Target | Command |
|---|---|
| Pixel 6 Pro, fold emulator | `adb shell settings put system accelerometer_rotation 0`, then `adb shell settings put system user_rotation 1` (sideways) or `0` (upright); the tool does this |
| Fold emulator postures | `adb shell cmd device_state state 1` (half-opened), `state 2` (opened), `state reset`; tabletop is half-opened with the fold horizontal, book with it vertical; capture the inner display with `screencap -p -d <id>` (`--display <id>`) |
| iPhone 17, iPad Pro simulators | `osascript`: raise the simulator's window, then Device ▸ Orientation ▸ "Landscape Left" / "Portrait" through System Events |
| iPhone Duo simulator | Device Hub's rotation and pose buttons (Closed, Book, Open) |

While listening behind a gate (`e2-45db`, gate `-50`), turn each target upright → sideways → upright, and on the fold
emulator flat → tabletop → book → flat ten times (SC-012). Expected each time: the note stays on the gauge with no
gap, the handle stays at the same place, and the control is whole: inside the screen, not cut by an edge, a bar or
the fold, and not over or under the gauge, the selectors or the pill (FR-016, FR-024, SC-008). Review every capture
against that list and against the tempo slider's look (FR-020).

Also check once per platform: the largest font size (the settings column or card scrolls, the labels are whole), and
a pointer and a keyboard on the tablet or desktop-mode window (hover halo, focus ring, arrows move one step).

## 5. Live sound through the microphone (FR-025, SC-010)

On the Pixel 6 Pro and on each simulator (a simulator hears the workstation's microphone), launch with a gate and no
test signal:

```sh
adb -s 19021FDEE001AH shell am start -n audio.tembra.app/audio.tembra.android.MainActivity --es tembra.tuner.noiseGate off
xcrun simctl launch "iPhone 17" "$BUNDLE" --tembra-tuner-noise-gate=off
```

The tone for the loudspeaker (196 Hz with four harmonics, forty seconds), and how to play it:

```sh
python3 - <<'TONE'
import math, os, struct, wave
rate = 48000
path = os.path.join(os.environ.get("TMPDIR", "/tmp"), "tembra-g3.wav")
with wave.open(path, "wb") as out:
    out.setnchannels(1); out.setsampwidth(2); out.setframerate(rate)
    for n in range(rate * 40):
        t = n / rate
        s = sum(a * math.sin(2 * math.pi * 196 * k * t) for k, a in ((1, 0.25), (2, 1.0), (3, 0.5), (4, 0.3), (5, 0.2)))
        out.writeframes(struct.pack("<h", int(12000 * s / 2.25)))
print(path)
TONE
afplay -v 0.15 "$TMPDIR/tembra-g3.wav"   # a quiet background; -v 1 with the output at half volume is the stand-in of §6
```

1. Play a quiet pitched background from a loudspeaker (a steady tone or a voice at low volume). With the gate off
   the gauge names it.
2. Relaunch with a gate level above it (find it with §6). The gauge rests for at least 95% of thirty seconds.
3. Play a louder tone at an instrument's level over it. It is read within a quarter of a second, and the gauge rests
   again when it stops.
4. While it listens through the microphone, move the handle several times. "Mic active" never disappears and the
   platform's microphone indicator stays on (FR-003, SC-005).
5. Watch for the gauge flickering on a steady sound near the gate, and for the background being named after a note
   because it held the gate open. Write down either; they are the evidence about the 3 dB margin.

Try the same on the fold emulator. If its microphone gets nothing from the workstation, write that down and note
that §3 stands in there.

## 6. The scale checkpoint (research R8)

Before the feature is accepted, on the Pixel 6 Pro and through the iPhone 17 simulator, launch with the gate off and
the real microphone and read three steady sounds off the track. The track is the scale: −80 dB at "Off", −20 dB at
"High"; the fill ends at the sound's level. Take three captures a second apart for each sound, then confirm the
reading by relaunching with the gate one step under it (the fill is active) and one step over it (the fill is
muted).

| Sound | How |
|---|---|
| Q, the quietest room | Nothing playing |
| B, a pitched background | A steady tone or a voice from a loudspeaker across the room, at low volume |
| I, a stand-in for an instrument | A 196 Hz harmonic tone from the workstation's loudspeaker at half output volume, 30 cm from the target |

Keep the scale if on both targets Q ≤ −60 dB and −50 dB ≤ I ≤ −20 dB. If only I is out of range, the loudspeaker is
not proof: ask the owner for one pluck of a real guitar at arm's length and use that number. If Q fails, or the real
instrument does, stop, write the numbers into the notes and propose new ends through `/speckit-clarify`.

## 7. Remembered across launches

Set the handle, close the app (`adb shell am force-stop audio.tembra.app`, `xcrun simctl terminate`), open it with no
arguments: the handle is where it was and the gate is in force from the first sound (SC-007). On a fresh install the
handle is at "Off".

## 8. After acceptance (FR-027)

Add the slider to `design/stitch/DESIGN.md`, upload the new Auto Listen screens to the Stitch project from the
captures of §4, and re-pull the export (`design/stitch/README.md`).

## 9. Still for a person

- A real guitar and a real cello in a room with a television on (SC-011).
- VoiceOver and TalkBack reading and adjusting the control on a physical device.
- The owner's iPhone 14 (skipped for now) and a physical foldable.
