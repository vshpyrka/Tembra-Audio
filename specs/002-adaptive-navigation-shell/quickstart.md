# Quickstart: Adaptive Navigation Shell and Home

**Feature**: `002-adaptive-navigation-shell` | **Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

Everything runs from `app/` with the committed Gradle wrapper. Prerequisites are unchanged from
feature 001 (`app/README.md`): JDK 21, Xcode 26 with an iOS simulator runtime, Android SDK
platform 37 with NDK 29 and CMake 3.31.6, the `android` CLI, and `local.properties` pointing at
the SDK.

## 1. Build and launch (User Story 1)

```bash
cd app
./gradlew :androidApp:installDebug
adb shell am start -n audio.tembra.app/audio.tembra.android.MainActivity
```

```bash
cd app/iosApp
xcodebuild -project iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 17' -derivedDataPath build build
xcrun simctl boot "iPhone 17"
xcrun simctl install "iPhone 17" "build/Build/Products/Debug-iphonesimulator/Tembra Audio.app"
xcrun simctl launch "iPhone 17" audio.tembra.app
```

The simulator bundle identifier is `audio.tembra.app` followed by the `TEAM_ID` from
`Configuration/Config.xcconfig` (empty in the repository, so plain `audio.tembra.app`; with a local team it is
`audio.tembra.app<TEAM_ID>`). Read it from the built app before launching:
`/usr/libexec/PlistBuddy -c "Print :CFBundleIdentifier" "build/Build/Products/Debug-iphonesimulator/Tembra Audio.app/Info.plist"`.

Expected on both: the app opens on **Home** (top bar overline "TEMBRA AUDIO", title "Home",
one sentence of placeholder text). On a phone the six items Home, Timeline, Tuner, Metronome,
Projects, Profile sit in the bottom bar with Home marked active. Tap each item: the title
changes, the marker moves with a short animation, and tapping the active item again does
nothing. On Android, press back from any other destination: Home returns; back again leaves
the app.

## 2. Automated tests (FR-020)

```bash
cd app
./gradlew checkAndroidHost     # JVM: ShellViewModelTest, LayoutModeTest, DestinationTest, IconAssetsTest, ThemeJsonParityTest
./gradlew checkIos             # iOS simulator: the same commonTest classes + NavigationShellTest, TopBarTest, placeholder and dev-view UI tests
./gradlew checkAndroidDevice   # connected Android device: the uiTest classes + engine link tests
./gradlew engineHostTest       # unchanged C++ lane; must still pass
```

Every lane ends green with zero warnings (`--warning-mode all`). Test classes and what each
proves are listed in [contracts/navigation-shell.md](contracts/navigation-shell.md) and
[contracts/design-system-additions.md](contracts/design-system-additions.md).

## 3. The three layouts (User Story 2)

Windows are judged by width and height, not device. Use one device per band:

| Band | Layout | Android | iOS |
|---|---|---|---|
| under 600 dp | bottom bar | any phone, portrait | iPhone 17 |
| 600 dp to 839 dp | rail, no panel | a foldable or tablet AVD in portrait (for example Pixel Fold unfolded in portrait, or Pixel Tablet portrait at 800 dp) | iPad mini or iPad (10th gen) in portrait |
| 840 dp and wider, at least 480 dp tall | rail + Library panel on Home | Pixel Tablet landscape, or a desktop-mode / freeform window | iPad Pro 13-inch landscape |
| 840 dp and wider, under 480 dp tall | rail, panel hidden | phone in landscape | iPhone 17 in landscape |

On a phone, the medium and expanded bands can be produced without an emulator by lowering the display density:
`adb shell wm density 380` turns a 1440 px Pixel into a 606 dp window (rail in portrait, rail plus panel in
landscape); `adb shell wm density reset` restores it. Create and boot emulators with the `android` CLI
(`android emulator create`, `android emulator start`) or Android Studio's device manager; rotate with
`adb shell settings put system accelerometer_rotation 0 && adb shell settings put system user_rotation 1`
(restore with `user_rotation 0`). On the iOS simulator rotate with Device > Rotate (⌘←/⌘→).

Live-resize check (SC-004): on a foldable emulator, select Tuner, fold and unfold (or on a
desktop-mode window drag its edge across 600 dp and 840 dp). Tuner stays selected and the
navigation control changes form at each threshold. On an iPad, select Tuner, then open Split
View or Stage Manager and shrink the window below 840 dp: the panel disappears on Home, the rail
stays, Tuner stays selected.

## 4. Home and the Library panel (User Story 3)

On any expanded window open Home: a 320 dp panel titled "Library" appears on the right with the
empty-state text and nothing else; it cannot be collapsed. Switch to Tuner: the panel is gone
and the content spans the width. Rotate a phone to landscape on Home: rail, no panel.

## 5. Pointer, keyboard and screen reader (User Story 4)

- Hover: on a device with a mouse (Android desktop mode, or an iPad with a trackpad), move the
  pointer over a navigation item: the label brightens, and on the rail the item gets a subtle fill.
- Keyboard: with a hardware keyboard, press Tab until a navigation item is focused (visible
  focus ring), use Tab / arrow keys to move along the items, press Enter or Space: the destination
  changes.
- Screen reader: enable TalkBack (`adb shell settings put secure enabled_accessibility_services com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService`)
  or VoiceOver on the simulator (Accessibility Inspector also works): each item announces its
  label and "selected" for the active one. Every item measures at least 44 dp (the UI tests
  assert `assertHeightIsAtLeast(44.dp)` and `assertWidthIsAtLeast(44.dp)`).

## 6. Icons and fonts in the shared design system (User Story 5)

- Compare the six glyphs on both platforms at the same size: they are the same vector files.
- Confirm `app/THIRD_PARTY_LICENSES.md` lists "Material Symbols (google/material-design-icons)"
  with the commit and Apache-2.0, and still lists Space Grotesk and Inter.
- Grep the shell for literal visual values: `grep -rnE '\b[0-9]+\.(dp|sp)\b|Color\(0x' app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/{navigation,designsystem}` returns nothing (all values come from `TembraTheme`).
- Re-fetching or adding a glyph: `app/tools/fetch-material-symbols.sh <glyph-name>` downloads,
  normalises and drops the file into `composeResources/drawable/` (contracts/design-system-additions.md).

## 7. Reduce motion (FR-008)

Android: Settings > Accessibility > Remove animations (or `adb shell settings put global animator_duration_scale 0`).
iOS simulator: Settings > Accessibility > Motion > Reduce Motion. Switch destinations: the
change is instant and the marker jumps. Restore afterwards (`animator_duration_scale 1`).

## 8. Developer-only token preview (FR-022)

Debug builds only; never reachable from the UI.

```bash
# Android (debug build): the extra is ignored by release builds
adb shell am start -n audio.tembra.app/audio.tembra.android.MainActivity --es tembra.developer.view token-preview
# iOS simulator (Debug configuration)
xcrun simctl launch "iPhone 17" audio.tembra.app --tembra-developer-view=token-preview
```

Expected: the full-screen palette and type preview from feature 001. Launching without the
argument, or a release build with it, opens Home. Note: the composable stays in the iOS
framework binary; only the entry point is compiled out (plan, Complexity Tracking).

## 9. Screenshots (FR-020, SC-003)

Stored under `specs/002-adaptive-navigation-shell/screenshots/` as
`<platform>-<compact|medium|expanded|short>-<destination>.png`, with the devices, OS versions and outcomes of the
manual checks in `screenshots/NOTES.md`. Rotating a simulator or tapping in it from the command line goes through
the Simulator app's menus and keyboard via AppleScript (System Events), which requires Accessibility permission for
the terminal (System Settings > Privacy & Security > Accessibility). `simctl io screenshot` always writes the
device's native portrait framebuffer, so rotate landscape captures upright with `sips -r -90`. The Mac keyboard is
forwarded to the focused simulator as a hardware keyboard: Tab moves focus along the navigation items and Return
selects, which is the easiest way to switch destinations without touch.

```bash
android screen capture --output specs/002-adaptive-navigation-shell/screenshots/android-compact-home.png
xcrun simctl io "iPhone 17" screenshot specs/002-adaptive-navigation-shell/screenshots/ios-compact-home.png
xcrun simctl io "iPad mini (A17 Pro)" screenshot specs/002-adaptive-navigation-shell/screenshots/ios-medium-home.png
xcrun simctl io "iPad Pro 13-inch (M5)" screenshot specs/002-adaptive-navigation-shell/screenshots/ios-expanded-home.png
```

Capture Home at all three bands on both platforms, plus Tuner at expanded width (no panel) and
one large-font capture (`adb shell settings put system font_scale 2.0`; `xcrun simctl ui "iPhone 17" content_size accessibility-extra-extra-extra-large`; restore to `1.0` / `medium`).
Review against `design/stitch/screens/instrument-tuner/phone/screenshot.jpg` (bar) and
`design/stitch/screens/export-mastering/tablet/screenshot.jpg` (rail): placement, sizes, active
treatment; no clipping, overlap or off-screen elements.

## 10. Hygiene (SC-007)

```bash
cd app && ./gradlew checkAndroidHost checkIos --warning-mode all   # zero warnings
grep -rn "TODO" shared/ui/src | grep -v "#"                          # none without an issue reference
```

## Fallback

If the rail or panel does not appear where expected, first verify the band with the
developer overlay of the platform (Android: Developer options > "Show layout bounds" and the
window width reported by `adb shell wm size` divided by density; iOS: Xcode's view debugger),
then check `LayoutModeTest` and `NavigationShellTest` in `checkAndroidHost` / `checkIos`.
