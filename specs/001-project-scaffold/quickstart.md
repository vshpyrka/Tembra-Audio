# Quickstart: Project Scaffold

**Feature**: `specs/001-project-scaffold/spec.md` | **Date**: 2026-09-23

This guide validates the finished scaffold end to end. Commands run from the repository root
unless stated. Expected outcomes are the acceptance scenarios of the spec; the implementation
must make every step below true as written.

## 1. Prerequisites (one-time, macOS)

| Need | Check | Install if missing |
|---|---|---|
| JDK 21 | `java -version` shows 21 | Temurin 21 (present on the reference machine) |
| Xcode 26.x + command line tools | `xcodebuild -version`; `xcode-select -p` | App Store; `sudo xcode-select -s /Applications/Xcode.app` |
| iOS simulator runtime | `xcrun simctl runtime list` lists an iOS runtime | `xcodebuild -downloadPlatform iOS` (about 8 GB, needs roughly 10 GB free) |
| Android SDK platform 36 + build tools | `android sdk list` shows `platforms/android-36`, `build-tools/36.0.0` | `android sdk install "platforms;android-36" "build-tools;36.0.0"` |
| Android NDK and CMake | `ls $HOME/Library/Android/sdk/ndk/29.0.14206865 $HOME/Library/Android/sdk/cmake/3.31.6` | `android sdk install "ndk;29.0.14206865" "cmake;3.31.6"` |
| `android` CLI | `android --version` | already installed; `android update` recommended |
| A device to run on | Android: a phone with USB debugging, or an emulator; iOS: the simulator (a physical iPhone also works for running and screenshots, see §7) | Emulator (optional): `android sdk install emulator "system-images;android-35;google_apis;arm64-v8a"` then `android emulator create` |

Gradle itself is not installed system-wide; the committed wrapper (`app/gradlew`) downloads
Gradle 9.7.0 on first use. Point Gradle at the SDK once:

```bash
echo "sdk.dir=$HOME/Library/Android/sdk" > app/local.properties
```

## 2. Build and launch on Android (User Story 1)

```bash
cd app
./gradlew :androidApp:installDebug          # builds the C++ engine for arm64-v8a/x86_64, all shared modules, the APK
adb shell am start -n audio.tembra.app/audio.tembra.android.MainActivity   # launches on the connected device
```

**Expected**: the app opens to the placeholder screen: "Tembra Audio", a button with
"Pressed 0 times", a caption
`Tembra Engine 0.1.0 (oboe 1.11.0; miniaudio 0.11.25; dr_wav 0.14.5; signalsmith-stretch 1.1.0)`,
and the colour and type preview below. First build from a clean cache is expected to take
several minutes (Kotlin/Native and NDK toolchains download); the spec budget is 15 minutes to
the running screen on both platforms.

## 3. Build and launch on iOS (User Story 1)

```bash
cd app/iosApp
xcodebuild -project iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 17' -derivedDataPath build build
xcrun simctl boot "iPhone 17" || true
xcrun simctl install booted build/Build/Products/Debug-iphonesimulator/Tembra\ Audio.app
xcrun simctl launch booted audio.tembra.app
```

Or open `app/iosApp/iosApp.xcodeproj` in Xcode and press Run. The Xcode build phase runs
`./gradlew :shared:ui:embedAndSignAppleFrameworkForXcode`, which builds the iOS static engine
library through CMake, the cinterop bindings and the `TembraShared` framework.

**Expected**: the same placeholder screen; the engine caption lacks the `oboe` entry (Oboe is
Android-only).

## 4. Run the automated tests (User Story 2)

```bash
cd app
./gradlew checkAndroidHost     # JVM: SemanticVersion, descriptor parser, fake engine, ViewModel, theme.json parity
./gradlew checkIos             # iOS simulator: everything in commonTest + Compose UI tests + native engine link
./gradlew checkAndroidDevice   # connected phone or emulator: Compose UI tests + native engine link + AppDirectories
./gradlew engineHostTest       # macOS host: CMake configure/build + Catch2 tests for the C ABI
```

**Expected**: each command ends with `BUILD SUCCESSFUL`, prints the executed test count, and
finishes in under 10 minutes on a warm cache. `checkIos` boots the default simulator if none is
running. Then prove the lanes can fail: change an expected value in
`app/shared/core/src/commonTest/.../SemanticVersionTest.kt`, rerun `checkAndroidHost` and
`checkIos`; both must report exactly one failing test with its name, then revert.

## 5. Verify the design system and the placeholder behaviour (User Story 3)

1. On either platform, compare the swatches in the preview with `design/stitch/theme.json`:
   the first row shows the Material roles (`primary #cebdff`, `primaryContainer #a78bfa`,
   `background #131316`, ...), the second block the DAW colours (`playhead #C084FC`,
   `winePad #5B2A3D`, ...). Every label must match its rendered colour and the file.
2. Tap the button three times: the counter reads "Pressed 3 times" in tabular figures.
3. Rotate the device or resize the window (Android desktop mode / freeform, iPad split view):
   nothing overlaps or clips; on a wide window the content sits in a centred 720 dp column and
   the preview becomes a grid.
4. With a mouse or trackpad (Android desktop mode, emulator, iPad with trackpad): hovering the
   button shows a hover overlay and the tooltip "Adds one to the counter".
5. With a keyboard: Tab to the button (focus ring visible), press Space; the counter increases.

The automated parts of this story run in `checkIos` and `checkAndroidDevice`
(`PlaceholderScreenTest`, `DesignTokenPreviewTest`) and `checkAndroidHost`
(`ThemeJsonParityTest`).

## 6. Confirm the native engine link (User Story 4)

```bash
cd app
./gradlew :shared:engine:iosSimulatorArm64Test --tests 'audio.tembra.engine.NativeAudioEngineTest'
./gradlew :shared:engine:connectedAndroidDeviceTest   # exact task name confirmed at implementation and aliased by checkAndroidDevice
```

**Expected**: both pass; the caption on screen matches the string asserted by the tests. No
audio permission prompt ever appears, and the app does not appear in the OS microphone
indicator.

## 7. Capture the verification screenshots (FR-010)

```bash
mkdir -p specs/001-project-scaffold/screenshots
# Android (device or emulator), phone portrait then a wide window (landscape tablet, or a
# desktop-mode / freeform window at least 840 dp wide):
android screen capture --output specs/001-project-scaffold/screenshots/android-compact.png
android screen capture --output specs/001-project-scaffold/screenshots/android-wide.png
# iOS: iPhone then iPad simulator
xcrun simctl io booted screenshot specs/001-project-scaffold/screenshots/ios-compact.png
xcrun simctl boot "iPad Pro 13-inch (M4)" && xcrun simctl launch booted audio.tembra.app
xcrun simctl io booted screenshot specs/001-project-scaffold/screenshots/ios-wide.png
```

Add `--device <serial>` when more than one Android device is attached. Review the four images against
`design/stitch/screens/*/screenshot.jpg` and `theme.json`, then tick the screenshot item in the
feature checklist. A physical iPhone can substitute for the simulator screenshots (Xcode →
Devices and Simulators → Take Screenshot) but not for the `checkIos` test lane.

## 8. Understand the structure (User Story 5)

Read `app/README.md`. It must answer, without further searching: where a new screen goes
(`:shared:ui`, feature package), a new domain rule (`:shared:core`), a new persistence concern
(`:shared:data`), a new engine command (`app/engine/include/tembra_engine.h` → `:shared:engine`
facade); and list the four commands from §4, the two launch commands from §2–§3 and the
screenshot procedure from §7. CLAUDE.md must record the same build and test commands.

## 9. Licence and hygiene checks (FR-009, SC-007)

```bash
cd app
./gradlew clean build -x check --console=plain --warning-mode all 2>&1 | grep -c -E 'kotlin:compiler:warning|^w: '   # expected: 0 from project code (see README § Known toolchain notes)
cat THIRD_PARTY_LICENSES.md                                              # every row Apache-2.0, MIT, MIT-0/public domain, OFL-1.1, or BSL-1.0 (test-only)
```

## Fallback

If Kotlin 2.4.20 with Compose Multiplatform 1.12.1 fails to resolve or compile on this machine,
switch the catalog to the proven `kotlinconf-app` set (Kotlin 2.4.10, Compose Multiplatform
1.11.1, AGP 9.1.0, Gradle 9.6.1, lifecycle 2.11.0-beta01) and record the reason in
`research.md` R2.
