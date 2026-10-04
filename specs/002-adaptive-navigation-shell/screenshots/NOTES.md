# Verification notes: feature 002 (adaptive navigation shell and Home)

Manual checks and screenshot provenance. Dates are 2026-09-24 unless stated. Automated coverage is in the
`uiTest`, `commonTest`, `androidHostTest` and `androidDeviceTest` source sets of `:shared:ui`.

## Devices and simulators

| Name | Platform | Window | Used for |
|---|---|---|---|
| Pixel 6 Pro (serial 19021FDEE001AH) | Android 17, 1440 x 3120 px at 560 dpi (411 x 891 dp portrait) | compact portrait; short landscape; medium and expanded bands through `adb shell wm density` | all Android captures, device lane |
| iPhone 17 simulator | iOS 26.5, 402 x 874 pt | compact portrait; short landscape | `ios-compact-*`, `ios-short-*`, iOS lane |
| iPad Pro 13-inch (M5) simulator | iOS 26.5, 1032 x 1376 pt | expanded | `ios-expanded-*` |

The iOS simulator build carries the developer's local `TEAM_ID` in its bundle identifier
(`audio.tembra.app<TEAM_ID>`, from `Configuration/Config.xcconfig`); launch and screenshot commands must use that
identifier, or the plain `audio.tembra.app` when `TEAM_ID` is empty. A stale feature-001 build under the plain
identifier was removed from both simulators before capturing.

## T048: launch and navigate (US1)

- Android: cold launch opens Home ("TEMBRA AUDIO" overline, title "Home", one sentence). Tapping Tuner, Metronome and
  Profile changed the title each time (`uiautomator dump` read the top bar after every tap); re-tapping Tuner changed
  nothing; the system back key returned to Home, and a second back left the app (launcher resumed). The active capsule
  fades to the new item. Capture: `android-compact-home.png`.
- iOS: cold launch opens Home with the same frame; the bottom bar sits above the home indicator. Destination switching,
  the no-op re-selection and the 300 ms fade are covered on the simulator by `NavigationShellTest` (iOS lane). Capture:
  `ios-compact-home.png`.
- Finding fixed on the way: with six items "Metronome" (59.4 dp at 10 sp) fills its cell and the capsule clip cut its
  first and last glyph on the Pixel; the capsule is now drawn behind the content, 4 dp wider than the label, and nothing
  is clipped. The rail item likewise grows to its label (48 dp minimum) so "Metronome" stays on one line.
- Finding fixed on the way: the Android APKs carried no compose resources at all (AGP's KMP library plugin needs
  `androidResources.enable = true` before the Compose plugin can copy them into assets); feature 001's fonts had been
  falling back silently on Android. Both the app and the device-test APK now contain the six glyphs, the six fonts and
  the licence files.

## T052, T053: the frame adapts (US2)

- Android (Pixel 6 Pro): Tuner selected in portrait (bottom bar); rotating to landscape (891 x 411 dp) switched to the
  rail with the title still "Tuner"; the rail cleared the camera cutout on the start side. `adb shell wm density 380`
  (a 606 dp window, medium band) showed the rail in portrait; `wm density reset` (activity recreated) kept Tuner
  selected, as did the next rotation to landscape and back. Captures: `android-short-home.png` (landscape, rail, no
  panel), `android-medium-home.png` (606 dp, rail, no panel).
- iOS: `ios-medium-home.png` on a temporary iPad mini (A17 Pro) simulator (744 pt wide, rail, no panel; the
  simulator was deleted afterwards). `ios-short-home.png`: iPhone 17 rotated to Landscape Left through the Simulator's
  Device > Orientation menu driven by AppleScript (2026-09-25, after the terminal was granted Accessibility
  permission): rail on the left clearing the Dynamic Island, all six items, no panel. Note that `simctl io screenshot`
  writes the framebuffer in the device's native portrait orientation, so landscape captures are rotated upright with
  `sips -r -90`. Live iPad Split View / Stage Manager resizes were not exercised. The width-to-mode mapping is
  covered on the simulator by `LayoutModeTest` (both directions across 600, 840 and 480 dp) and the shell by
  `NavigationShellTest.layoutSwitchKeepsTheSelection`.
- No foldable emulator or desktop-mode window was available; the density override is the substitute for a live
  width change on Android.
- Finding fixed on the way: six rail items at the design's 8 dp vertical padding and 6 dp gap need 374 dp, more than
  a phone in landscape offers below the top bar (about 311 dp on the Pixel); the rail now uses 4 dp vertical metrics
  (48 dp items, all six visible) and scrolls in windows that are shorter still.

## T068: the Library panel (US3)

- iOS: `ios-expanded-home.png` on the iPad Pro 13-inch (M5) simulator in landscape (1376 x 1032 pt): rail on the
  left, Home in the middle, the 320 dp "Library" panel on the right with only its empty-state sentence, no control.
  Compared with `design/stitch/screens/timeline-arranger/tablet/screenshot.jpg`: same placement, surface tone and
  corner radius for the panel; the rail matches `design/stitch/screens/export-mastering/tablet/screenshot.jpg` in
  width, item shape and active treatment.
- `ios-expanded-tuner.png`: Tuner selected on the iPad simulator with the Mac keyboard forwarded as a hardware
  keyboard (three Tab presses moved focus to Tuner, Return activated it); the panel disappeared and the content
  spanned the width. Synthetic clicks at window coordinates missed because the Simulator draws the device inside a
  bezel; the keyboard route needs no geometry.
- Android: `android-expanded-home.png` and `android-expanded-tuner.png` on the Pixel at density 380 in landscape
  (1313 x 606 dp): rail, Home and the Library panel; tapping Tuner removed the panel and the content spanned the
  width. The Pixel dropped off USB for part of the session (2026-09-25) and the Android captures were taken once it
  was back.

## T075: pointer, keyboard and screen reader (US4)

- Automated on the iOS simulator: every item has role tab, its label as description and a 44 dp target
  (`everyItemIsAnAccessibleTab`); Enter on a focused item selects it (`keyboardActivatesAnItem`, in the bar, the rail
  and through the shell); the focused item draws the primary ring (`focusedItemShowsAnIndicator`); hover raises the
  label to onSurface in the bar and fills the rail item with surfaceContainerHigh (`hoverRaisesTheLabelColour`,
  `hoverFillsTheItem`).
- The same cases ran green on the Pixel in the device lane (77 tests), including the hover and focus-ring captures
  through `performMouseInput` and `requestFocus`.
- Hardware keyboard on iOS (iPad Pro simulator, Mac keyboard forwarded): Tab moved focus along the rail items and
  Return selected the focused one (Tuner), as the spec's US4 scenario 2 describes.
- Not done: a real mouse hover on the Pixel (`adb shell input mouse motionevent MOVE` injects no hover event, so
  `android-expanded-home-hover.png` could not be captured without a physical mouse or desktop mode), keyboard
  traversal on Android, and TalkBack / VoiceOver announcements (no way to read spoken output over adb or simctl).
  The semantics those readers consume (role tab, label description, selected state) are asserted by the automated
  tests on both platforms.

## T079: glyph parity (US5)

Bottom bars cropped from `android-compact-home.png` and `ios-compact-home.png` and compared at the same width: the
six glyphs (home, graphic_eq, music_note, av_timer, folder, person) have identical shapes and stroke weights on both
platforms; only anti-aliasing differs. Both come from the same normalised vector files. The layered glow behind the
active capsule showed faint banding at four layers; it is now drawn with eight.

## T080: SC-008 dry run (US5)

Using only `app/README.md` ("Where does X go?"): a seventh destination needs (1) an icon: run
`tools/fetch-material-symbols.sh <glyph>` and add a `TembraIcon` value; (2) no new chrome component: add a
`Destination` enum value and the branch in `DestinationContent`, and `TembraNavigationBar` / `TembraNavigationRail`
pick it up from the registry; (3) label style `labelSmall` (title `headlineSmall` in the top bar). Answered in about
two minutes.

## T093 to T095: developer view, reduce motion, large fonts

- iOS developer view: `xcrun simctl launch "iPhone 17" audio.tembra.app<TEAM_ID> --tembra-developer-view=token-preview`
  opened the full-screen palette and type preview; launching without the argument opened Home. Android debug build
  with `--es tembra.developer.view token-preview` opened the preview ("Colour roles" in the accessibility tree);
  without the extra it opened Home. The release build (`assembleRelease`, signed with the debug keystore for the
  check) launched with the same extra opened Home: the `BuildConfig.DEBUG` branch is compiled out. The debug build
  was reinstalled afterwards.
- Reduce motion: `xcrun simctl ui` offers no reduce-motion switch (only appearance, increase_contrast, content_size),
  and Settings cannot be driven without the Accessibility permission; the behaviour is covered on the simulator by
  `ReduceMotionTest` and `NavigationShellTest.reduceMotionSwitchesInstantly` (green on the simulator and on the
  Pixel). Android: with `animator_duration_scale 0` the switches Home to Tuner and back worked and the marker jumped;
  instantness itself is what the device test measures on the animation clock. The scale was restored to 1.0.
- Large fonts: `ios-compact-home-largefont.png` at `content_size accessibility-extra-extra-extra-large`: the bar grew,
  the six items still fit, labels wrap onto a second line (single words break mid-word, which is the unclipped
  alternative to truncation) and nothing is clipped; content size restored to `medium`. Android:
  `android-compact-home-largefont.png` at `font_scale 2.0` on the Pixel, same behaviour; restored to 1.0.

## Screenshot matrix (T096)

Present: `android-compact-home`, `android-medium-home`, `android-short-home`, `android-expanded-home`,
`android-expanded-tuner`, `android-compact-home-largefont`, `ios-compact-home`, `ios-medium-home`, `ios-short-home`,
`ios-expanded-home`, `ios-expanded-tuner`, `ios-compact-home-largefont`. Missing: `android-expanded-home-hover`
(no mouse on the Pixel and adb cannot inject a hover); the hover state is covered by the hover tests on both
platforms.

## Physical iPhone (2026-09-25)

The Debug build signed with the developer's personal team was installed on "Victor's iPhone" (iPhone 14, iOS
26.5.2) with `xcrun devicectl device install app` and launched with `devicectl device process launch`
(bundle `audio.tembra.app<TEAM_ID>`); the process was still running ten seconds later. There is no CLI screenshot
for a physical iPhone, so the on-device look was not captured.

## iPhone Duo end-edge rail (experiment), 2026-10-04

**Device**: iPhone Duo simulator (iOS 27.1 beta runtime 24A94401), Debug build made with Xcode 27.1 beta, closed
pose, outer display 466 × 678 pt. Background: `docs/research/iphone-duo.md`; layout mode `EndRail` in data-model §3.

| File | What it shows |
|---|---|
| `ios-duo-closed-end-rail-home.png` | Held upright: the rail fills the end-edge system column under the clock and Wi-Fi, items at the bottom; the content runs the full height with no bottom bar |
| `ios-duo-closed-end-rail-tuner.png` | Tuner (By Ear) beside the end rail; the string grid scrolls inside its card as designed |
| `ios-duo-closed-landscape-rail.png` | Rotated to landscape (678 pt wide, medium): the ordinary start rail, the system column hidden, the camera in the bottom end corner over empty content |

Observed: "Metronome" fills its selected rail pill edge to edge; the rail's secondaryContainer highlight differs
from the bottom bar's primaryContainer one, now visible on the same device. checkIos on the Duo (660) and
checkAndroidHost (380) passed with the change.
