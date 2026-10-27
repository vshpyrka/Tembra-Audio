# iPhone Duo: simulator setup, fold APIs and design guidance

Research brief, 2026-10-04. Sources: the iOS 27.1 SDK headers in Xcode 27.1 beta (27A9269), Apple's
[HIG: Designing for iPhone Duo](https://developer.apple.com/design/human-interface-guidelines/designing-for-iphone-duo)
(new 2026-09-09), [Preparing your app for iPhone Duo](https://developer.apple.com/documentation/technologyoverviews/preparing-your-app-for-iphone-duo),
the API reference pages linked below, and running Tembra Audio on the iPhone Duo simulator.

## 1. Running the simulator

- The Duo simulator needs **Xcode 27.1** (Apple silicon, macOS 26.6 or later) and an **iOS 27.1 simulator runtime**.
  The Xcode 27.1 release (27A9275) has been the default `/Applications/Xcode.app` since 2026-10-21 and runs the Duo
  on its own (2026-10-26, macOS 27.0.1: Debug and Release builds, Device Hub poses, `checkIos`); the 27.1 beta
  (27A9269) the simulator was first set up with was removed that day. Since then the Duo runs on the **iOS 27.1
  release runtime, build 24A94425** (7.85 GB), which replaced the 27.1 beta runtime 24A94401. Both are delivered
  only through Xcode's own downloader (`downloadMethod: mobileAsset`; the beta carries
  `minXcodeVersion = maxXcodeVersion = 27.1.0` in Apple's [runtime index](https://devimages-cdn.apple.com/downloads/xcode/simulators/index2.dvtdownloadableindex));
  there is no dmg on the developer site.
- **Xcode 27.2 beta 2 cannot run it**: its iOS 27.2 runtime lists the Duo (`iPhone19,4`) under
  `unsupportedDeviceTypes`, so `simctl create` fails with "Incompatible device", and it cannot fetch the 27.1 runtime
  (hangs at "Finding content...").
- Set up with Xcode 27.1 selected: `xcodebuild -downloadPlatform iOS -buildVersion 24A94425` (without
  `-buildVersion`, on 2026-10-03, it also started iOS 27.0; it stages the ~8 GB on the internal disk and installs
  the runtime even with `-exportPath <dir>`, which also leaves a copy there), then
  `xcrun simctl create "iPhone Duo" com.apple.CoreSimulator.SimDeviceType.iPhone-Duo com.apple.CoreSimulator.SimRuntime.iOS-27-1`.
  While two builds of iOS 27.1 are installed, pass the wanted runtime's `.simruntime` path (`runtimeBundlePath` in
  `xcrun simctl runtime list -j`) instead of the identifier; `xcrun simctl getenv <udid>
  SIMULATOR_RUNTIME_BUILD_VERSION` on the booted device confirms the build.
  An Xcode that brings a newer CoreSimulator service installs it on first launch: quit Device Hub and
  `launchctl remove com.apple.CoreSimulator.CoreSimulatorService` once. After a macOS update the first Device Hub
  launch asks for an administrator password to install Xcode's components (`xcodebuild -checkFirstLaunchStatus`
  exits non-zero until then).
- Simulator.app is replaced by **Device Hub** (`Xcode.app/Contents/Applications/DeviceHub.app`). The
  Closed, Book and Open poses and rotation are buttons in its bottom toolbar; `simctl` has no pose command.
- Two displays: outer 466 × 678 pt (1398 × 2034 px), inner 669 × 951 pt landscape-native (2853 × 2007 px).
  `xcrun simctl io <udid> enumerate` lists them; `screenshot --display=<uuid>` captures one (the inner one is black
  while closed).
- `./gradlew checkIos -Ptembra.iosSimulator=<udid>` runs the iOS lane on the Duo (660 tests passed on 2026-10-03
  under Xcode 26.6, 1,225 on 2026-10-26 under Xcode 27.1).

## 2. What the app does on it

- **Build with Xcode 27.1.** Built with Xcode 26.6 (iOS 26.5 SDK) the app runs in a portrait compatibility window
  with black bars on the open inner display; Apple's guide confirms that apps built with Xcode 26 and earlier do not
  extend under the status bar and camera. Built with the 27.1 SDK it fills both displays and the adaptive layout
  picks the rail and the supporting pane on the inner display (the pane showed only beside Home; since Home was
  removed on 2026-10-17 the inner display shows the rail alone).
- **The outer display keeps a system column at the end edge** (status bar and camera). The content cleared it
  through `WindowInsets.safeDrawing`, but the bottom bar padded only `navigationBars`, so its items ran under the
  column; fixed 2026-10-03 (`TembraNavigationBar` now also pads the horizontal safe-drawing insets).
- **The compact metronome does not fit** the 678 pt outer display: the Stacked form falls back to scrolling and the
  dial card is cut at the viewport with the slider out of view. See the Pocket experiment in
  `specs/004-metronome/contracts/metronome-screen.md`.
- **The tabletop and book splits of feature 005 never trigger on iOS**: Compose Multiplatform's
  `currentWindowAdaptiveInfo().windowPosture` returns a constant empty posture outside Android
  (`WindowAdaptiveInfo.nonAndroid.kt`, adaptive 1.2.0).

## 3. Fold APIs (iOS 27.1)

| Need | UIKit | SwiftUI |
|---|---|---|
| Hinge state | [`UIHingeInteraction`](https://developer.apple.com/documentation/uikit/uihingeinteraction) added to a view; its handler gets [`UIHinge`](https://developer.apple.com/documentation/uikit/uihinge) `status` (`closed`, `partiallyOpen`, `fullyOpen`) and `angle` (radians), initially and on every change | [`onHingeChange`](https://developer.apple.com/documentation/swiftui/view/onhingechange(isenabled:_:)) with `DeviceHinge.status` and `.angle` |
| Where the fold is | [`UIView.reservedRegions(kind: .division)`](https://developer.apple.com/documentation/uikit/uiview/reservedregion): `frame` (with `margins`) in the view's coordinates, `isActive` | `GeometryProxy.reservedRegions(kind: .division)` |
| Camera areas | the same call with `.occlusion` | the same |
| A two-pane container that follows the fold | `UIArrangementViewController` (split or overlay) | `ArrangementView` |

- The fold region is active while the device is partially open and inactive when it is fully open (Apple's guide).
  The reference page says the query returns regions "regardless of whether they are currently active", which
  disagrees with the `.includeInactive` option, so check `isActive`.
- Nothing announces a region change: read the regions during layout (`layoutSubviews`), and UIKit lays out again
  when a region read there changes. The regions lag the hinge status slightly.
- The header says to prefer `status` over `angle` and not to rely on the angle's update rate; the angle is for
  interactions and effects, not layout.
- **There is no posture value.** The HIG describes poses informally ("partially folded like a book, placed down on
  a surface, or standing on its edges") and says supporting them "doesn't mean designing a custom layout for each
  one: instead, use size classes". A tabletop or book split has to be derived: partially open with a horizontal
  division region is tabletop, a vertical one is book (the rule Jetpack WindowManager uses on Android).
- Kotlin/Native 2.4.20's UIKit bindings do not contain these APIs (built against an older SDK), so they have to be
  read in Swift (`iosApp`, behind `if #available(iOS 27.1, *)`) and handed to Kotlin, where they can fill the
  existing `TabletopFold` and `VerticalFold`.

## 4. Design guidance that affects Tembra

- **Bars go on the side.** On the outer display "the system places toolbars and tab bars on the side to maximize the
  vertical space for content", and keeps them there on the inner display in landscape; the HIG asks apps to follow
  that and not override it. A bottom bar spends the vertical space Apple is saving; a side rail on the outer display
  matches the platform. UIKit reports the side with the `verticalBarEdge` trait (`toolbarVerticalEdge` in SwiftUI).
- **No display-specific layouts.** "Avoid fixed widths and display-specific dependencies"; base layout on the
  window's bounds and size classes; "avoid extreme layout changes as people fold the device".
- **Keep content off the fold.** System sheets, alerts and menus move clear of it automatically; custom content uses
  the reserved regions; grids should use an even number of columns.
- **Same functionality in every pose**: controls may overflow but must stay reachable.

## 5. Open follow-ups

1. A Swift bridge for the hinge status and the division region, so the feature 005 splits work on the Duo.
2. A side rail instead of the bottom bar on the closed outer display (HIG vertical bars). Tried 2026-10-04 on
   branch `experiment/duo-side-rail` (`LayoutMode.EndRail`) and merged to main: the rail fills the end-edge system
   column with its items at the bottom, under the clock and camera, and the content gains the bar's height. Since
   2026-10-17 the rail is Material's collapsed `WideNavigationRail` with icon-only items: at least as wide as the end
   inset, and Material's 96 dp where the inset is narrower.
3. If the Pocket metronome is kept, select it from a short compact window rather than the device model.
