# Quickstart: verify tabletop posture

Run from `app/` unless noted. Gradle goes through the `gradle-run` wrapper (Constitution VIII).

## 1. Automated lanes

```sh
./gradlew checkAndroidHost --warning-mode all            # FoldSplitGeometry, TabletopFold, layout selection
./gradlew checkIos "-Ptembra.iosSimulator=iPhone 17"     # same + tabletop UI tests on the simulator
./gradlew checkIos "-Ptembra.iosSimulator=iPad Pro 13-inch (M5)"
ANDROID_SERIAL=<serial> ./gradlew checkAndroidDevice     # each attached Android target in turn
```

Expected: all green, zero warnings, existing tuner/metronome layout tests unchanged.

## 2. Real posture on the Pixel 9 Pro Fold emulator

```sh
export ANDROID_SERIAL=127.0.0.1:5556
./gradlew :androidApp:installDebug
adb shell am start -n audio.tembra.app/audio.tembra.android.MainActivity
adb shell cmd device_state state 1        # HALF_OPENED (tabletop when the display is rotated so the fold is horizontal)
adb shell cmd device_state state 2        # OPENED (flat)
adb shell cmd device_state state reset
adb shell dumpsys SurfaceFlinger --display-id   # pick the inner display id for screencap -d
adb exec-out screencap -p -d <id> > tabletop.png
```

Check for each of Tuner Auto Listen (with the test signal), Tuner By Ear, Metronome (running):
1. Half-opened: watched content wholly above the fold, every setting below (contract `tabletop-layout.md`).
2. Toggle flat ↔ half-opened ten times while the metronome runs / the tuner listens: no stop, no lost selection
   (SC-002); the layout follows within a second (SC-003).
3. Largest font (`adb shell settings put system font_scale 2.0`): each region scrolls, nothing crosses the fold
   (SC-005); restore with `1.0`.
4. Book posture (display rotated so the fold is vertical) and the closed cover screen: existing layouts.

## 3. Regression captures

Capture the Tuner and Metronome on the Pixel 6 Pro, the iPhone 17 and the iPad Pro simulators and compare with the
feature 003/004 captures: no change (SC-004).

## 4. Still for a person

A physical foldable on a real table, listening to the click through ten fold changes.
