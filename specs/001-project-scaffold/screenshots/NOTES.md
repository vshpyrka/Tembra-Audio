# Screenshots: feature 001

The four captures of quickstart §7 (the app's launch screen: Pixel 6 Pro portrait and landscape, iPhone 17 and iPad
Pro 13-inch (M5) portrait) were retaken on 2026-10-16 with that day's build, when the owner asked for every recorded
screenshot to be updated after the navigation shell lost its top bar. The launch screen is now feature 002's Home
inside the shell (bottom bar on the phones, rail and Library panel on the iPad and the Pixel held sideways), with no
top bar. The originals show the scaffold's own screen (title, counter button, engine line, colour roles), which
feature 002 replaced; they stay in git: `git show 42d8e92:specs/001-project-scaffold/screenshots/<file>`. The colour
roles survive as the debug-only developer view (`tembra.developer.view` / `--tembra-developer-view=token-preview`).

## Recaptured 2026-10-17: icon-only navigation, no Home (Android)

Home was removed on 2026-10-17 and the navigation items became icons only (Material 3 Expressive bottom bar with the
active item on a lavender pill on compact windows, a collapsed 96 dp rail with the active item on a circle on wide
ones). The app now opens on Timeline, so the two Android launch captures were retaken under the same names and show
Timeline. Target: Pixel 6 Pro `19021FDEE001AH` (Android 17), the debug APK built that morning from
`worktree-nav-icons-only` (`:androidApp:assembleDebug`, no `tembra.perf` property), installed with `adb install -r -t`
(the app had been uninstalled from the phone, so this was a fresh install with RECORD_AUDIO and POST_NOTIFICATIONS
granted by `pm grant`), captured with `adb exec-out screencap -p`. Rotation locked with `wm user-rotation lock 0|1`;
the phone's settings were left as found (rotation lock 0, native density, font scale 1.0).

### Android, Pixel 6 Pro (2 retaken, 0 left)

| File | How | Differs beyond the navigation |
|---|---|---|
| `android-compact.png` | retaken: portrait, cold launch (`am force-stop`, `am start`) | the launch screen is now Timeline ("Timeline", "The timeline arranger arrives with the transport and timeline feature.") instead of Home ("This is your starting point in Tembra Audio."); that follows from the removal of Home. Bottom bar: five icons, Timeline on the lavender pill, no labels |
| `android-wide.png` | retaken: launch, then `wm user-rotation lock 1` (891 × 411 dp) | Timeline instead of Home, as above; collapsed rail with five icons and Timeline on the magenta circle. No other difference |

## Recaptured 2026-10-17: Home removed, icon-only navigation (iOS)

Both iOS captures were retaken under the same file names, on the same simulators, in the same state: a plain launch,
which opens Timeline now that Home is gone. The navigation shows icons only: a bar with the active item on a lavender
pill on the iPhone, and a collapsed 96 pt rail with the active item on a circle on the iPad. The builds were Debug, made
from the worktree `nav-icons-only` with the Xcode 27.1 SDK. `ios-wide.png` shows the rail, so it was taken a second
time from the build with 41fffa2 (rail items centred in the rail). Screenshots: `xcrun simctl io <udid> screenshot`.

| File | How | Differs beyond the navigation |
|---|---|---|
| `ios-compact.png` | retaken: iPhone 17 (iOS 26.5), portrait, plain launch: Timeline (title and one sentence) | no |
| `ios-wide.png` | retaken (build with 41fffa2): iPad Pro 13-inch (M5) (iOS 26.5), portrait (1032 pt, rail with the items centred), plain launch: Timeline | no. The Library panel at the foot is gone with Home; that is part of the navigation change. |
