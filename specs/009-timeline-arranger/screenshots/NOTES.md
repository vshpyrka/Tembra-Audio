# Feature 009 screenshots and device checks

One row per capture (spec FR-062) and per quickstart step run on a device. Captures come from
`android screen capture --output <file>` / `adb exec-out screencap -p` and `xcrun simctl io <device> screenshot`
(simulators) or `devicectl` (the iPhone 14). Every Android device, the fold emulator and the Mac's output were muted
for the session; the iPhone 14 cannot be muted remotely (its switch was set by hand).

| File | Target | What it shows | Step | Date |
|---|---|---|---|---|
| `android-1024-demo-stopped.png` | Pixel Tablet at 1024 dp (wm density 400) | Demo project stopped at the start: four tracks, five clips with waveforms, readout 00:00.00 / 00:30.96 | quickstart §3.1 | 2026-10-19 |
| `android-1024-library.png` | Pixel Tablet at 1024 dp (wm density 400) | Projects library: the demo, the active empty project, a seeded project with a description and key, seeded projects without | quickstart §7.2 | 2026-10-19 |
| `android-1024-settings-inline.png` | Pixel Tablet at 1024 dp (wm density 400) | Track settings inline under the header (expanded), one at a time, with the Effects placeholder | quickstart §7.1 | 2026-10-19 |
| `android-1200-demo-stopped.png` | Pixel Tablet at 1200 dp (wm density 341) | Demo project stopped at the start: four tracks, five clips with waveforms, readout 00:00.00 / 00:30.96 | quickstart §3.1 | 2026-10-19 |
| `android-1200-library.png` | Pixel Tablet at 1200 dp (wm density 341) | Projects library: the demo, the active empty project, a seeded project with a description and key, seeded projects without | quickstart §7.2 | 2026-10-19 |
| `android-1200-settings-inline.png` | Pixel Tablet at 1200 dp (wm density 341) | Track settings inline under the header (expanded), one at a time, with the Effects placeholder | quickstart §7.1 | 2026-10-19 |
| `android-360-demo-playing.png` | Pixel 6 Pro at 360 dp (wm density 640) | Demo project playing, playhead mid-song, readout counting | quickstart §3.2 | 2026-10-19 |
| `android-360-demo-stopped.png` | Pixel 6 Pro at 360 dp (wm density 640) | Demo project stopped at the start: four tracks, five clips with waveforms, readout 00:00.00 / 00:30.96 | quickstart §3.1 | 2026-10-19 |
| `android-360-large-font.png` | Pixel 6 Pro at 360 dp (wm density 640) | System font scale 2.0: readout intact, chips intact, three lanes visible | quickstart §9 | 2026-10-19 |
| `android-360-library.png` | Pixel 6 Pro at 360 dp (wm density 640) | Projects library: the demo, the active empty project, a seeded project with a description and key, seeded projects without | quickstart §7.2 | 2026-10-19 |
| `android-360-metronome-sheet.png` | Pixel 6 Pro at 360 dp (wm density 640) | The metronome settings sheet from a long press on the transport toggle | quickstart §6.5 | 2026-10-19 |
| `android-360-settings-screen.png` | Pixel 6 Pro at 360 dp (wm density 640) | Track settings as a screen with the transport bar; Effects shows the placeholder sentence | quickstart §7.1 | 2026-10-19 |
| `android-360-tempo-dialog.png` | Pixel 6 Pro at 360 dp (wm density 640) | The tempo dialog from the ruler pill: dial, steppers, slider, tap tempo | quickstart §6.3 | 2026-10-19 |
| `android-600-demo-stopped.png` | Pixel 6 Pro at 600 dp (wm density 384) | Demo project stopped at the start: four tracks, five clips with waveforms, readout 00:00.00 / 00:30.96 | quickstart §3.1 | 2026-10-19 |
| `android-600-grid-picker.png` | Pixel 6 Pro at 600 dp (wm density 384) | The grid picker open (compact: the overflow menu with the grid sizes; expanded: the grid pill's menu) | quickstart §5.4 | 2026-10-19 |
| `android-600-library.png` | Pixel 6 Pro at 600 dp (wm density 384) | Projects library: the demo, the active empty project, a seeded project with a description and key, seeded projects without | quickstart §7.2 | 2026-10-19 |
| `android-600-settings-screen.png` | Pixel 6 Pro at 600 dp (wm density 384) | Track settings as a screen with the transport bar; Effects shows the placeholder sentence | quickstart §7.1 | 2026-10-19 |
| `android-800-demo-stopped.png` | Pixel Tablet, portrait 800 dp | Demo project stopped at the start: four tracks, five clips with waveforms, readout 00:00.00 / 00:30.96 | quickstart §3.1 | 2026-10-19 |
| `android-800-library.png` | Pixel Tablet, portrait 800 dp | Projects library: the demo, the active empty project, a seeded project with a description and key, seeded projects without | quickstart §7.2 | 2026-10-19 |
| `android-800-settings-screen.png` | Pixel Tablet, portrait 800 dp | Track settings as a screen with the transport bar; Effects shows the placeholder sentence | quickstart §7.1 | 2026-10-19 |
| `android-840-demo-stopped.png` | Pixel 6 Pro at 840 dp (wm density 274) | Demo project stopped at the start: four tracks, five clips with waveforms, readout 00:00.00 / 00:30.96 | quickstart §3.1 | 2026-10-19 |
| `android-840-grid-picker.png` | Pixel 6 Pro at 840 dp (wm density 274) | The grid picker open (compact: the overflow menu with the grid sizes; expanded: the grid pill's menu) | quickstart §5.4 | 2026-10-19 |
| `android-840-library.png` | Pixel 6 Pro at 840 dp (wm density 274) | Projects library: the demo, the active empty project, a seeded project with a description and key, seeded projects without | quickstart §7.2 | 2026-10-19 |
| `android-840-settings-screen.png` | Pixel 6 Pro at 840 dp (wm density 274) | Track settings as a screen with the transport bar; Effects shows the placeholder sentence | quickstart §7.1 | 2026-10-19 |
| `android-compact-card-menu.png` | Pixel 6 Pro, 411 dp portrait (Android 17) | The demo card's menu: Rename, Description, Key, Duplicate, Delete, Restore demo project | quickstart §7.2 | 2026-10-19 |
| `android-compact-clip-selected.png` | Pixel 6 Pro, 411 dp portrait (Android 17) | First clip selected: the 2 dp selection outline | quickstart §4.7 | 2026-10-19 |
| `android-compact-demo-playing.png` | Pixel 6 Pro, 411 dp portrait (Android 17) | Demo project playing, playhead mid-song, readout counting | quickstart §3.2 | 2026-10-19 |
| `android-compact-demo-stopped.png` | Pixel 6 Pro, 411 dp portrait (Android 17) | Demo project stopped at the start: four tracks, five clips with waveforms, readout 00:00.00 / 00:30.96 | quickstart §3.1 | 2026-10-19 |
| `android-compact-empty-project.png` | Pixel 6 Pro, 411 dp portrait (Android 17) | A new, empty project: the invitation to add a track or audio | quickstart §4.1 | 2026-10-19 |
| `android-compact-grid-picker.png` | Pixel 6 Pro, 411 dp portrait (Android 17) | The grid picker open (compact: the overflow menu with the grid sizes; expanded: the grid pill's menu) | quickstart §5.4 | 2026-10-19 |
| `android-compact-import-refusal.png` | Pixel 6 Pro, 411 dp portrait (Android 17) | A four-channel WAV refused with the reason in a snackbar | quickstart §4.5 | 2026-10-19 |
| `android-compact-landscape.png` | Pixel 6 Pro, 411 dp portrait (Android 17) | Phone landscape: the timeline with the rail and the one-line readout | quickstart §9 | 2026-10-19 |
| `android-compact-large-font.png` | Pixel 6 Pro, 411 dp portrait (Android 17) | System font scale 2.0: readout intact, chips intact, three lanes visible | quickstart §9 | 2026-10-19 |
| `android-compact-library.png` | Pixel 6 Pro, 411 dp portrait (Android 17) | Projects library: the demo, the active empty project, a seeded project with a description and key, seeded projects without | quickstart §7.2 | 2026-10-19 |
| `android-compact-metronome-sheet.png` | Pixel 6 Pro, 411 dp portrait (Android 17) | The metronome settings sheet from a long press on the transport toggle | quickstart §6.5 | 2026-10-19 |
| `android-compact-search-none.png` | Pixel 6 Pro, 411 dp portrait (Android 17) | Search without a match: the no-match message and '0 of m projects' | quickstart §7.2 | 2026-10-19 |
| `android-compact-search-results.png` | Pixel 6 Pro, 411 dp portrait (Android 17) | Search 'Seed' matching the seeded projects, the count reads 'n of m projects' | quickstart §7.2 | 2026-10-19 |
| `android-compact-settings-screen.png` | Pixel 6 Pro, 411 dp portrait (Android 17) | Track settings as a screen with the transport bar; Effects shows the placeholder sentence | quickstart §7.1 | 2026-10-19 |
| `android-compact-split-result.png` | Pixel 6 Pro, 411 dp portrait (Android 17) | The first clip split at the playhead: two clips, the right one selected | quickstart §4.7 | 2026-10-19 |
| `android-compact-stop-and-leave.png` | Pixel 6 Pro, 411 dp portrait (Android 17) | The stop-and-leave dialog when choosing Tuner while playing | quickstart §3.6 | 2026-10-19 |
| `android-compact-tempo-dialog.png` | Pixel 6 Pro, 411 dp portrait (Android 17) | The tempo dialog from the ruler pill: dial, steppers, slider, tap tempo | quickstart §6.3 | 2026-10-19 |
| `android-compact-unavailable-clip.png` | Pixel 6 Pro, 411 dp portrait (Android 17) | The duplicated demo with its audio removed: clips drawn as unavailable outlines | spec FR-030 | 2026-10-19 |
| `android-compact-zoom-closest.png` | Pixel 6 Pro, 411 dp portrait (Android 17) | Closest zoom level: ruler labels in beats and sixteenths, time in hundredths | quickstart §5.1 | 2026-10-19 |
| `android-compact-zoom-widest.png` | Pixel 6 Pro, 411 dp portrait (Android 17) | Widest zoom level: the whole project in bars | quickstart §5.1 | 2026-10-19 |
| `android-expanded-card-menu.png` | Pixel Tablet, landscape 1280 dp (Android 16) | The demo card's menu: Rename, Description, Key, Duplicate, Delete, Restore demo project | quickstart §7.2 | 2026-10-19 |
| `android-expanded-clip-selected.png` | Pixel Tablet, landscape 1280 dp (Android 16) | First clip selected: the 2 dp selection outline | quickstart §4.7 | 2026-10-19 |
| `android-expanded-demo-playing.png` | Pixel Tablet, landscape 1280 dp (Android 16) | Demo project playing, playhead mid-song, readout counting | quickstart §3.2 | 2026-10-19 |
| `android-expanded-demo-stopped.png` | Pixel Tablet, landscape 1280 dp (Android 16) | Demo project stopped at the start: four tracks, five clips with waveforms, readout 00:00.00 / 00:30.96 | quickstart §3.1 | 2026-10-19 |
| `android-expanded-grid-picker.png` | Pixel Tablet, landscape 1280 dp (Android 16) | The grid picker open (compact: the overflow menu with the grid sizes; expanded: the grid pill's menu) | quickstart §5.4 | 2026-10-19 |
| `android-expanded-metronome-sheet.png` | Pixel Tablet, landscape 1280 dp (Android 16) | The metronome settings sheet from a long press on the transport toggle | quickstart §6.5 | 2026-10-19 |
| `android-expanded-settings-inline.png` | Pixel Tablet, landscape 1280 dp (Android 16) | Track settings inline under the header (expanded), one at a time, with the Effects placeholder | quickstart §7.1 | 2026-10-19 |
| `android-expanded-split-result.png` | Pixel Tablet, landscape 1280 dp (Android 16) | The first clip split at the playhead: two clips, the right one selected | quickstart §4.7 | 2026-10-19 |
| `android-expanded-stop-and-leave.png` | Pixel Tablet, landscape 1280 dp (Android 16) | The stop-and-leave dialog when choosing Tuner while playing | quickstart §3.6 | 2026-10-19 |
| `android-expanded-tempo-dialog.png` | Pixel Tablet, landscape 1280 dp (Android 16) | The tempo dialog from the ruler pill: dial, steppers, slider, tap tempo | quickstart §6.3 | 2026-10-19 |
| `android-expanded-zoom-closest.png` | Pixel Tablet, landscape 1280 dp (Android 16) | Closest zoom level: ruler labels in beats and sixteenths, time in hundredths | quickstart §5.1 | 2026-10-19 |
| `android-expanded-zoom-widest.png` | Pixel Tablet, landscape 1280 dp (Android 16) | Widest zoom level: the whole project in bars | quickstart §5.1 | 2026-10-19 |
| `android-fold-open-demo-playing.png` | Pixel 9 Pro Fold emulator, open (inner display, Android 16) | Demo project playing, playhead mid-song, readout counting | quickstart §3.2 | 2026-10-19 |
| `android-fold-open-demo-stopped.png` | Pixel 9 Pro Fold emulator, open (inner display, Android 16) | Demo project stopped at the start: four tracks, five clips with waveforms, readout 00:00.00 / 00:30.96 | quickstart §3.1 | 2026-10-19 |
| `android-fold-open-large-font.png` | Pixel 9 Pro Fold emulator, open (inner display, Android 16) | System font scale 2.0: readout intact, chips intact, three lanes visible | quickstart §9 | 2026-10-19 |
| `android-fold-open-library.png` | Pixel 9 Pro Fold emulator, open (inner display, Android 16) | Projects library: the demo, the active empty project, a seeded project with a description and key, seeded projects without | quickstart §7.2 | 2026-10-19 |
| `android-fold-open-metronome-sheet.png` | Pixel 9 Pro Fold emulator, open (inner display, Android 16) | The metronome settings sheet from a long press on the transport toggle | quickstart §6.5 | 2026-10-19 |
| `android-fold-open-settings-screen.png` | Pixel 9 Pro Fold emulator, open (inner display, Android 16) | Track settings as a screen with the transport bar; Effects shows the placeholder sentence | quickstart §7.1 | 2026-10-19 |
| `android-fold-open-tempo-dialog.png` | Pixel 9 Pro Fold emulator, open (inner display, Android 16) | The tempo dialog from the ruler pill: dial, steppers, slider, tap tempo | quickstart §6.3 | 2026-10-19 |
| `android-fold-tabletop-demo-stopped.png` | Pixel 9 Pro Fold emulator, half-opened | Demo project stopped at the start: four tracks, five clips with waveforms, readout 00:00.00 / 00:30.96 | quickstart §3.1 | 2026-10-19 |
| `android-fold-tabletop-metronome-sheet.png` | Pixel 9 Pro Fold emulator, half-opened | The metronome settings sheet from a long press on the transport toggle | quickstart §6.5 | 2026-10-19 |
| `android-oppo-demo-playing.png` | OPPO CPH2239 (ColorOS) | Demo project playing, playhead mid-song, readout counting | quickstart §3.2 | 2026-10-19 |
| `android-oppo-demo-stopped.png` | OPPO CPH2239 (ColorOS) | Demo project stopped at the start: four tracks, five clips with waveforms, readout 00:00.00 / 00:30.96 | quickstart §3.1 | 2026-10-19 |
| `android-oppo-library.png` | OPPO CPH2239 (ColorOS) | Projects library: the demo, the active empty project, a seeded project with a description and key, seeded projects without | quickstart §7.2 | 2026-10-19 |
| `android-oppo-settings-screen.png` | OPPO CPH2239 (ColorOS) | Track settings as a screen with the transport bar; Effects shows the placeholder sentence | quickstart §7.1 | 2026-10-19 |
| `android-pixel7-demo-playing.png` | Pixel 7 Pro, 411 dp (Android 16) | Demo project playing, playhead mid-song, readout counting | quickstart §3.2 | 2026-10-19 |
| `android-pixel7-demo-stopped.png` | Pixel 7 Pro, 411 dp (Android 16) | Demo project stopped at the start: four tracks, five clips with waveforms, readout 00:00.00 / 00:30.96 | quickstart §3.1 | 2026-10-19 |
| `android-pixel7-library.png` | Pixel 7 Pro, 411 dp (Android 16) | Projects library: the demo, the active empty project, a seeded project with a description and key, seeded projects without | quickstart §7.2 | 2026-10-19 |
| `android-redmi-demo-playing.png` | Redmi Note 5 (Android 9, API 28) | Demo project playing, playhead mid-song, readout counting | quickstart §3.2 | 2026-10-19 |
| `android-redmi-demo-stopped.png` | Redmi Note 5 (Android 9, API 28) | Demo project stopped at the start: four tracks, five clips with waveforms, readout 00:00.00 / 00:30.96 | quickstart §3.1 | 2026-10-19 |
| `android-redmi-library.png` | Redmi Note 5 (Android 9, API 28) | Projects library: the demo, the active empty project, a seeded project with a description and key, seeded projects without | quickstart §7.2 | 2026-10-19 |
| `android-redmi-settings-screen.png` | Redmi Note 5 (Android 9, API 28) | Track settings as a screen with the transport bar; Effects shows the placeholder sentence | quickstart §7.1 | 2026-10-19 |
| `ios-compact-card-menu.png` | iPhone 17 simulator, iOS 26.5 | The demo card's menu: Rename, Description, Key, Duplicate, Delete, Restore demo project | quickstart §7.2 | 2026-10-19 |
| `ios-compact-clip-selected.png` | iPhone 17 simulator, iOS 26.5 | First clip selected: the 2 dp selection outline | quickstart §4.7 | 2026-10-19 |
| `ios-compact-demo-playing.png` | iPhone 17 simulator, iOS 26.5 | Demo project playing, playhead mid-song, readout counting | quickstart §3.2 | 2026-10-19 |
| `ios-compact-demo-stopped.png` | iPhone 17 simulator, iOS 26.5 | Demo project stopped at the start: four tracks, five clips with waveforms, readout 00:00.00 / 00:30.96 | quickstart §3.1 | 2026-10-19 |
| `ios-compact-empty-project.png` | iPhone 17 simulator, iOS 26.5 | A new, empty project: the invitation to add a track or audio | quickstart §4.1 | 2026-10-19 |
| `ios-compact-grid-picker.png` | iPhone 17 simulator, iOS 26.5 | The grid picker open (compact: the overflow menu with the grid sizes; expanded: the grid pill's menu) | quickstart §5.4 | 2026-10-19 |
| `ios-compact-landscape.png` | iPhone 17 simulator, iOS 26.5 | Phone landscape: the timeline with the rail and the one-line readout | quickstart §9 | 2026-10-19 |
| `ios-compact-library.png` | iPhone 17 simulator, iOS 26.5 | Projects library: the demo, the active empty project, a seeded project with a description and key, seeded projects without | quickstart §7.2 | 2026-10-19 |
| `ios-compact-metronome-sheet.png` | iPhone 17 simulator, iOS 26.5 | The metronome settings sheet from a long press on the transport toggle | quickstart §6.5 | 2026-10-19 |
| `ios-compact-portability.png` | iPhone 17 simulator, iOS 26.5 | The demo project copied from the Pixel 6 Pro (folder copy into Documents/projects) opened on iOS: the identical arrangement | spec SC-009 | 2026-10-19 |
| `ios-compact-search-none.png` | iPhone 17 simulator, iOS 26.5 | Search without a match: the no-match message and '0 of m projects' | quickstart §7.2 | 2026-10-19 |
| `ios-compact-search-results.png` | iPhone 17 simulator, iOS 26.5 | Search 'Seed' matching the seeded projects, the count reads 'n of m projects' | quickstart §7.2 | 2026-10-19 |
| `ios-compact-settings-screen.png` | iPhone 17 simulator, iOS 26.5 | Track settings as a screen with the transport bar; Effects shows the placeholder sentence | quickstart §7.1 | 2026-10-19 |
| `ios-compact-split-result.png` | iPhone 17 simulator, iOS 26.5 | The first clip split at the playhead: two clips, the right one selected | quickstart §4.7 | 2026-10-19 |
| `ios-compact-stop-and-leave.png` | iPhone 17 simulator, iOS 26.5 | The stop-and-leave dialog when choosing Tuner while playing | quickstart §3.6 | 2026-10-19 |
| `ios-compact-tempo-dialog.png` | iPhone 17 simulator, iOS 26.5 | The tempo dialog from the ruler pill: dial, steppers, slider, tap tempo | quickstart §6.3 | 2026-10-19 |
| `ios-compact-zoom-closest.png` | iPhone 17 simulator, iOS 26.5 | Closest zoom level: ruler labels in beats and sixteenths, time in hundredths | quickstart §5.1 | 2026-10-19 |
| `ios-compact-zoom-widest.png` | iPhone 17 simulator, iOS 26.5 | Widest zoom level: the whole project in bars | quickstart §5.1 | 2026-10-19 |
| `ios-duo-demo-playing.png` | iPhone Duo simulator, iOS 27.1 (closed, end rail) | Demo project playing, playhead mid-song, readout counting | quickstart §3.2 | 2026-10-19 |
| `ios-duo-demo-stopped.png` | iPhone Duo simulator, iOS 27.1 (closed, end rail) | Demo project stopped at the start: four tracks, five clips with waveforms, readout 00:00.00 / 00:30.96 | quickstart §3.1 | 2026-10-19 |
| `ios-duo-library.png` | iPhone Duo simulator, iOS 27.1 (closed, end rail) | Projects library: the demo, the active empty project, a seeded project with a description and key, seeded projects without | quickstart §7.2 | 2026-10-19 |
| `ios-duo-settings-screen.png` | iPhone Duo simulator, iOS 27.1 (closed, end rail) | Track settings as a screen with the transport bar; Effects shows the placeholder sentence | quickstart §7.1 | 2026-10-19 |
| `ios-duo-tempo-dialog.png` | iPhone Duo simulator, iOS 27.1 (closed, end rail) | The tempo dialog from the ruler pill: dial, steppers, slider, tap tempo | quickstart §6.3 | 2026-10-19 |
| `ios-expanded-card-menu.png` | iPad Pro 13-inch (M5) simulator, iOS 26.5 | The demo card's menu: Rename, Description, Key, Duplicate, Delete, Restore demo project | quickstart §7.2 | 2026-10-19 |
| `ios-expanded-clip-selected.png` | iPad Pro 13-inch (M5) simulator, iOS 26.5 | First clip selected: the 2 dp selection outline | quickstart §4.7 | 2026-10-19 |
| `ios-expanded-demo-playing.png` | iPad Pro 13-inch (M5) simulator, iOS 26.5 | Demo project playing, playhead mid-song, readout counting | quickstart §3.2 | 2026-10-19 |
| `ios-expanded-demo-stopped.png` | iPad Pro 13-inch (M5) simulator, iOS 26.5 | Demo project stopped at the start: four tracks, five clips with waveforms, readout 00:00.00 / 00:30.96 | quickstart §3.1 | 2026-10-19 |
| `ios-expanded-empty-project.png` | iPad Pro 13-inch (M5) simulator, iOS 26.5 | A new, empty project: the invitation to add a track or audio | quickstart §4.1 | 2026-10-19 |
| `ios-expanded-grid-picker.png` | iPad Pro 13-inch (M5) simulator, iOS 26.5 | The grid picker open (compact: the overflow menu with the grid sizes; expanded: the grid pill's menu) | quickstart §5.4 | 2026-10-19 |
| `ios-expanded-landscape.png` | iPad Pro 13-inch (M5) simulator, iOS 26.5 | Phone landscape: the timeline with the rail and the one-line readout | quickstart §9 | 2026-10-19 |
| `ios-expanded-library.png` | iPad Pro 13-inch (M5) simulator, iOS 26.5 | Projects library: the demo, the active empty project, a seeded project with a description and key, seeded projects without | quickstart §7.2 | 2026-10-19 |
| `ios-expanded-metronome-sheet.png` | iPad Pro 13-inch (M5) simulator, iOS 26.5 | The metronome settings sheet from a long press on the transport toggle | quickstart §6.5 | 2026-10-19 |
| `ios-expanded-search-none.png` | iPad Pro 13-inch (M5) simulator, iOS 26.5 | Search without a match: the no-match message and '0 of m projects' | quickstart §7.2 | 2026-10-19 |
| `ios-expanded-search-results.png` | iPad Pro 13-inch (M5) simulator, iOS 26.5 | Search 'Seed' matching the seeded projects, the count reads 'n of m projects' | quickstart §7.2 | 2026-10-19 |
| `ios-expanded-settings-inline.png` | iPad Pro 13-inch (M5) simulator, iOS 26.5 | Track settings inline under the header (expanded), one at a time, with the Effects placeholder | quickstart §7.1 | 2026-10-19 |
| `ios-expanded-split-result.png` | iPad Pro 13-inch (M5) simulator, iOS 26.5 | The first clip split at the playhead: two clips, the right one selected | quickstart §4.7 | 2026-10-19 |
| `ios-expanded-stop-and-leave.png` | iPad Pro 13-inch (M5) simulator, iOS 26.5 | The stop-and-leave dialog when choosing Tuner while playing | quickstart §3.6 | 2026-10-19 |
| `ios-expanded-tempo-dialog.png` | iPad Pro 13-inch (M5) simulator, iOS 26.5 | The tempo dialog from the ruler pill: dial, steppers, slider, tap tempo | quickstart §6.3 | 2026-10-19 |
| `ios-expanded-zoom-closest.png` | iPad Pro 13-inch (M5) simulator, iOS 26.5 | Closest zoom level: ruler labels in beats and sixteenths, time in hundredths | quickstart §5.1 | 2026-10-19 |
| `ios-expanded-zoom-widest.png` | iPad Pro 13-inch (M5) simulator, iOS 26.5 | Widest zoom level: the whole project in bars | quickstart §5.1 | 2026-10-19 |
| `ios-iphone14-demo-stopped.png` | iPhone 14, iOS 27.0.1 (physical) | Demo project stopped at the start: four tracks, five clips with waveforms, readout 00:00.00 / 00:30.96 | quickstart §3.1 | 2026-10-19 |

## Targets (attached 2026-10-19)

| Target | Id | OS | Role |
|---|---|---|---|
| Pixel 6 Pro (USB) | `19021FDEE001AH` | Android 17 | device lane, the compact matrix, the bands by `wm density`, the manual checks, the perf session |
| Pixel 7 Pro (USB) | `33241FDH3000NZ` | Android 16 | smoke captures |
| Pixel Tablet (USB) | `3621105H803UFX` | Android 16 | the expanded matrix, the 1024 / 1200 / 800 dp bands |
| OPPO CPH2239 (USB) | `O7PBYLZP4HMBW8S4` | ColorOS | smoke captures (after the owner replaced its cable; before that every adb call hung) |
| Redmi Note 5 (USB) | `e9446ac2` | Android 9 | smoke captures (API 28: no notification permission, no Perfetto) |
| Pixel 9 Pro Fold emulator | `emulator-5554` | Android 16 | open and half-opened postures (inner display `-d 4619827259835644672`) |
| iPhone 17 simulator (replaced on the Mac by an iPhone 18 Pro, `1374495F-788F-42B4-B036-5CFF2EA195EE`, on 2026-10-25) | `4CD6879C-8FBA-4CEF-B4EC-31968D0144B0` | iOS 26.5 | the compact iOS matrix, the iOS lane, portability; the final iOS lanes ran on the iPhone 18 Pro |
| iPad Pro 13-inch (M5) simulator | `C9A67FF1-62FC-4BE6-89F9-685DC39FBD46` (re-created as `035F4A11-FE89-4AA8-BCD0-405790F30B71` on 2026-10-25) | iOS 26.5 | the expanded iOS matrix, the iOS lane |
| iPhone Duo simulator (re-created as `8AF0F092-16BB-4286-8F8F-E0AB208CBA8A` on the iOS 27.1 release runtime on 2026-10-26, when the Xcode 27.1 beta left the Mac) | `EB59DDD2-6D13-4503-A77E-BDFB69BF8CCD` | iOS 27.1 | closed posture, end rail; the iOS lane on 2026-10-26 |
| iPhone 14 (USB) | `00008110-001049660E3A401E` | iOS 27.0.1 | launch captures, the perf session |

Every Android target, the emulator and the Mac's output were muted (`media volume --stream 3 --set 0`, Mac output
muted); the iPhone 14 cannot be muted remotely (its switch was set by the owner).

## How the captures were driven

Android: `scratchpad/capture_android.py` drives the app through its accessibility ids (every Compose `testTag`
reaches UiAutomator as a resource id, now also inside dialogs, menus and sheets) and hardware key events (the
keyboard map of spec FR-057: space, Home, arrows, S, +/−, Ctrl+Z), and saves `adb exec-out screencap`. iOS:
`TembraPerfTests/ScreenshotMatrix` (XCUITest) drives the same ids on a simulator and writes PNGs through the shared
file system (`TEST_RUNNER_TEMBRA_CAPTURE_DIR`), because the Mac session was locked for most of the run and System
Events could not type into a simulator. The widths below 411 dp and above 840 dp come from `wm density` on the
Pixel 6 Pro (1440 px: 640 → 360 dp, 384 → 600 dp, 274 → 840 dp) and the Pixel Tablet (2560 px landscape: 400 →
1024 dp, 341 → 1200 dp); the layout bands are measured on the content width, so an 841 dp window with the 80 dp rail
is still the medium band (settings as a screen) and inline settings begin at a 920 dp window.

## Gates (all through the `gradle-run` wrapper, `--warning-mode all`, zero warnings)

| Lane | Result | Log |
|---|---|---|
| `engineHostTest` (Catch2, macOS) | 204 tests green, incl. the new counter-reset test | 0059 |
| `checkAndroidHost` | green (five test tasks) | 0061 |
| `checkIos -Ptembra.iosSimulator="iPhone 17"` | 800 tests green | 0062 |
| `:shared:engine:connectedAndroidDeviceTest` (Pixel 6 Pro, alone) | 95 green | 0060 |
| `checkAndroidDevice :androidApp:connectedDebugAndroidTest` (Pixel 6 Pro) | 797 UI tests + 17 app tests; one flake (`MetronomeScreenTest.theControlsAnnounceWhatTheyAre`, "No compose hierarchies") green on rerun | 0063, 0064 |
| targeted device reruns after each product fix | green (0065 dialogs/menus, 0067 narrow bar, 0068 readout fit + chip, 0074 two-pass fit after the review) | |
| `checkIos` after the review fixes | 805 tests green, zero warnings (the order-recording keyboard tests run only there) | 0075 |
| final pass (T134) | see the "Final pass" section below | |

An Opus sub-agent reviewed the uncommitted diff read-only (2026-10-19) and found: the readout and the master slider
both carried `weight(1f)` so the readout's fit budget could exceed its real slot (fixed: the readout is unweighted and
its budget is the bar's width minus the slider's floor and the five targets; a `TimeReadoutTest` measures the fit, a
411 dp / 2.0× bar test checks the budget); the tab-order tests wrapped around and could not fail (fixed: one pass of
Tabs records the first step each target is focused and the order is asserted); the demo-card tag test used the id
"demo" and so never exercised the demo branch (fixed: id "d1"); an unspecified font size would have crashed the fit
(guarded); the debug seeder's failure path (caught); a stale import; a redundant counter reset in the renderer.

What the device lane taught (fixed in this feature): one process-global native engine per test process (the
transport renderer's health counters now reset per session); `org.gradle.parallel` ran three instrumentation tasks on
one device at once (an `androidDeviceLock` build service serialises them); the streamer thread now runs at audio
priority and the real-time test shows a foreground activity; keyboard tests skip on Android by the lane's
declaration (`supportsKeyboardInputMode`); a device's IME resets a controlled `TextField` whose state is not updated;
the Pixel's density is 3.5 so touch offsets go through `LocalDensity`.

## Final pass (T134, 2026-10-25, zero warnings)

Run conditions, at the owner's request: every target silent (the music stream at 0 on each physical Android device, the
ringer silent on the Pixels, the tablet and the emulator, the Mac's output muted for the emulator and the simulators; the
OPPO keeps its ringer in vibrate, its shell cannot change it), the screens at the lowest brightness (`screen_brightness 0`
with manual mode on the Pixels, the tablet, the Redmi and the fold emulator; the OPPO refuses settings writes from the
shell and stays at its own 13/255 with auto brightness; the iPhone 14 is dimmed by the perf runner itself,
`UIScreen.main.brightness = 0` in `PerfJourneyTestCase`; simulators have no brightness), and a black window behind every
device test. The white window the owner saw was the Compose test host, `androidx.activity.ComponentActivity`, which the
`ui-test-manifest` artifact declares with `Theme.Material.Light.NoActionBar`, plus androidx.test's bootstrap and empty
activities (`WhiteBackgroundTheme`) that flash between tests; the shared UI device-test manifest now replaces those
themes with `Theme.Tembra.DeviceTest` (black), and the engine's `ForegroundActivity` is declared with
`Theme.Black.NoTitleBar`. Verified by polling the Pixel 6 Pro's focused window every 0.4 s with a capture at each
change: during the 20:16 lane `EmptyActivity` was white at every test close; during the 20:35 lane the same windows are
black (`evidence/device-test-window-before.png`, `evidence/device-test-window.png`). The black window also exposed two
tuner meter probes that had passed only because white is nearer the needle's pink than the dark theme: the needle
smoother steps at most 100 ms per frame, so the test's one long clock jump left the needle short of ±20 cents; the
test now settles by frames on the theme's own background. iOS Compose tests draw into an off-screen Skia surface, so no
window exists there.

| Lane | Result | Log |
|---|---|---|
| `engineHostTest` | 204 Catch2 tests green on 2026-10-19 (0080); the final run found the engine unchanged since and Gradle reused that result | 0111 |
| `checkAndroidHost` (every module) | green, 407 shared UI host tests of which 41 session tests (11 on the hand-over) | 0129 |
| `checkIos -Ptembra.iosSimulator="iPhone 18 Pro"` | green, 818 tests, fresh run at 22:15 | 0127 |
| `checkIos -Ptembra.iosSimulator="iPad Pro 13-inch (M5)"` | green | 0128 |
| `checkAndroidDevice :androidApp:connectedDebugAndroidTest` (Pixel 6 Pro, final code) | green: 198 core, 118 data, 95 engine, 815 UI and 17 app tests, 721 s, black test windows | 0126 |
| the same on the Pixel 7 Pro (`--continue`) | green, 737 s; a first run lost `theStreamProbeSeesAnOpenOutput` once (the probe's 1 s poll) | 0125 (0114) |
| the same on the Pixel Tablet | green, 724 s | 0115 |
| the same on the OPPO CPH2239 (Android 11, 360 dp) | engine: `MetronomeTimingEndToEndTest` start latency 180 ms against the 100 ms budget (the device); UI: two tests assumed a wide window (defect 6), fixed and rerun alone, green | 0116, 0130 |
| the same on the Redmi Note 5 (Android 9, 393 dp) | engine and app green; UI: the inline-row test (defect 6), fixed and rerun alone, green | 0121, 0131 |
| the same on the Pixel 9 Pro Fold emulator (`--continue`) | engine: the metronome's fitted period 498.98 ms against the 1 ms tolerance on one run (the emulator's audio clock), inside it on the next; UI: 814 of 815 green, the readout room test off by a sixth of a dp (defect 8), fixed and rerun alone, green | 0113, 0122, 0132 |
| `uv run pytest` (app/perf) | 350 passed, 1 skipped | |
| one public composable and one type per file under `ui/timeline`, `ui/transport`, `ui/tracksettings`, `ui/projects` | clean (scripted grep) | |

The Pixel 6 Pro, iOS and host lanes ran on the final code; the Pixel 7 Pro, tablet, OPPO, Redmi and emulator lanes ran
on the code of 20:50 to 21:30 (before the single-open hand-over rewrite, which is host-tested, and before the three
test fixes, which were rerun alone on the devices that found them).

### Final pass, continued (2026-10-26: the final code on every target)

The five lanes that had run on the 20:50 code ran again on the final code (`d7f0dc4`; the docs commit `605c92f` changes no
source), with the iPhone Duo's iOS lane beside them, as two chains at once: the main checkout for the iPhone Duo, the Pixel 7 Pro, the Pixel Tablet and the fold
emulator, a detached worktree at the same commit for the OPPO and the Redmi. The run conditions above were applied again
first (brightness 0 and manual mode, the music stream at 0, ringers silent, the Mac muted, black test windows). Log numbers
are per `gradle-run` workflow: A is the main-checkout chain (`0b408650`), B the OPPO/Redmi chain (`a109e992`), R the
OPPO and Redmi reruns (`febe1976`), P the Pixel 7 Pro rerun (`5a17c72b`).

One event shaped the pass: at 19:22:08 the USB hub that carries the Pixel 7 Pro, the Pixel Tablet, the OPPO and the Redmi
dropped all four for about a second (adb's own log: `usb_read failed with status e00002ed`, every transport "connection
terminated", re-enumerated at 19:22:09 and dropped once more at 19:22:12; the OPPO and the Redmi had each dropped once at
18:54, the Pixel 6 Pro, on its own port, at 19:01). The two UI runs in flight at that moment, the Pixel 7 Pro's and the
OPPO's, lost their `am instrument` shell and the runner's UiAutomation with it, so each ended early ("Test run failed to
complete") with AGP's XML blaming whichever test was running with an empty message. Both UI modules were rerun whole
afterwards; the tablet and Redmi lanes started after the drop and the emulator is not on USB.

| Lane | Result | Log |
|---|---|---|
| the Android device lane on the OPPO CPH2239 (`--continue`) | core 198, data 118, app 17 green; engine: `MetronomeTimingEndToEndTest` start latency 113 ms against the 100 ms budget (the device; 180 ms the day before); UI: the run ended at 377 of 815 when `LaidOutWindowSizeTest.containerSizeFollowsAnInPlaceRotation`, the one test that uses the instrumentation's `UiAutomation`, found it no longer connected (`IllegalStateException: UiAutomation not connected`: the 19:22:08 USB drop had taken the instrumentation shell and its UiAutomation with it; the runner stopped and the XML blames the test before it with an empty message). The UI module alone afterwards: 815 green in 624 s, zero warnings | B 0002, R 0001 |
| the same on the Redmi Note 5 (`--continue`) | core 198, data 118, app 17 green; engine: start latency 100.8 ms against the 100 ms budget on this run (green the day before and on the class alone afterwards: the device sits at the budget); UI: 815 with one failure, `TembraMenuTest.hoverTintsTheRowAndFocusDrawsTheRing` (the hovered row's colour had not moved by the time the probe read it), green on the class alone; no Play Protect prompt this time | B 0003, 0005, R 0002 |
| `checkIos -Ptembra.iosSimulator=8AF0F092-…` (the iPhone Duo) | every test task up-to-date in 2 s: Gradle kept the 14:04 run of the same simulator on the same sources, 1,225 green (core 198, data 118, engine 91, UI 818), zero warnings | A 0001 |
| the same on the Pixel 7 Pro (`--continue`) | core 198, data 118, engine 95, app 17 green; UI: the run ended at 403 of 815 in the 19:22:08 USB drop (the XML blames `ProjectOverviewChartTest.barsCarryTheSpansTints` with an empty message; the class alone: 3 green in 14 s). The UI module alone afterwards: 815 green in 618 s, zero warnings | A 0002, 0005, P 0001 |
| the same on the Pixel Tablet (`--continue`) | green in 723 s: core 198, data 118, engine 95, UI 815, app 17; zero warnings | A 0003 |
| the same on the Pixel 9 Pro Fold emulator (`--continue`) | core 198, data 118, engine 95 (the fitted period inside its tolerance this time), app 17 green; UI: 815 with 8 failures in 7 classes, every one the window-capture helper giving up (`ComposeTimeoutException … 2000 ms` six times, `PixelCopy failed with result 2` twice) while the lane took 72 min with both chains building on the Mac at once; the seven classes alone: 43 green in 67 s | A 0004, 0006 |

## Design iteration D1 (2026-10-26/27: the owner's review of the release build)

After the release build ran on every target, the owner reviewed the Timeline and asked for six changes (spec Clarifications,
session 2026-10-26; tasks T137–T149). What changed, commit by commit on `009-timeline-arranger`:

| Finding | Decision | Commit |
|---|---|---|
| The tempo and 4/4 pills at the ruler's left end covered the first bars on a phone | The tempo pill lives in the toolbar at every width (phones: between redo and the overflow button); the time signature is chosen in the metronome sheet; the ruler shows bars only | `d775aae` (T141) |
| The master volume slider took much of a phone's transport bar | Below 600 dp a speaker button whose popup holds the 200 dp slider and the percentage; the inline slider with its percentage from 600 dp | `0c2e84b` (T138) |
| "Grid Bar" changed nothing visible | One "Snap: <value>" entry opening a sheet with the five sizes; the snap lines are drawn over the lanes at the picked resolution (coarsened to keep 4 dp between lines); scrub and split snap too | `1b9daeb` (T139), `d775aae` |
| Zoom sat in the overflow menu | Two always-visible controls for a side-by-side comparison, chosen per device from "Zoom control" in the overflow: "− level +" buttons at the ruler's right end (default) or an overview strip above the transport bar | `b13368b` (T140), `d775aae` |
| The playhead chip's text was barely visible | `onPrimaryContainer` text on the lilac chip (6.7:1 measured on the Pixel 7 Pro, 1.1:1 before) and a 1 dp outline | `1b9daeb` |
| No way to record into a track | Out of scope (FR-064): recording becomes the next feature, before the mixer (T145) | — |

Also in the pass: the metronome sheet forgot its settings on a project switch (found by the zoom agent, `915c7c8`, T147); the
popup gained a `transport.masterPopup` perf section and the `timeline` journey opens the popup and drags the strip on both
platforms (`e300b7b`, T146); an Expanded ruler/lane misalignment of 4 dp was fixed on the way (T141).

Built as three parallel agents in detached worktrees on disjoint files (T138 on the Pixel 6 Pro, T139 on the Pixel 7 Pro,
T140 on the Pixel Tablet, each with its own `gradle-run` workflow), then one integrator in the main checkout (full
`:shared:ui` module on the Pixel 6 Pro, `checkIos` on the iPhone 18 Pro) and one follow-up agent (JVM lanes, perf APKs,
the XCTest target built). The zoom buttons overlay the ruler's right 136 dp rather than narrowing the lanes, so the last
time labels and, during playback, the position chip pass under them before the view pages.

### Lanes on the final D1 code (`e300b7b`, every target, `--warning-mode all`, zero warnings)

Two chains at once again: A from the main checkout (host lanes, the three simulators, the Pixel 6 Pro, the fold emulator;
workflow `75e47afe`), B from a detached worktree at the same commit (Pixel 7 Pro, Pixel Tablet, OPPO, Redmi; `fcb9d15b`),
E the reruns of the rewritten tests from the main checkout (`79828d95`). The run conditions above were applied again.

Two of the new tests were written for the author's device rather than for every window: `OverviewStripTest` composed a
400 dp strip (coerced into the 360 dp OPPO and the 393 dp Redmi, which shifted every position) and the 411 dp transport
bar test measured positions from the window's edge rather than the bar's (the required width centres the bar past the
edge of a narrower window). The strip test now uses a 320 dp strip with its positions written in beats, the bar test
measures from the bar, and the 44 dp and 132 dp assertions allow one pixel (the fold emulator's 2.44 and the Pixel 7
Pro's 2.625 densities land 0.1–0.2 dp off). Test-only changes; the app was not touched.

| Lane | Result | Log |
|---|---|---|
| `checkAndroidHost engineHostTest` | up to date with the follow-up agent's run on the same inputs: core 199, engine 60, data 108, ui 412, androidApp 12 green; C++ 204 green | A 0001 |
| `checkIos` on the iPhone 18 Pro | core 199, data 118, engine 91, UI 858 green | A 0002 |
| `checkIos` on the iPad Pro 13-inch (M5) | the same, green | A 0003 |
| `checkIos` on the iPhone Duo | the same, green | A 0004 |
| the Android device lane on the Pixel 6 Pro (`--continue`) | core 199, data 118, engine 95, UI 855, app 17 green, 834 s | A 0005 |
| the same on the Pixel 9 Pro Fold emulator (`--continue`) | core 199, data 118, engine 95, app 17 green; UI 855 with 2 failures, both the new tests measuring a 44 dp target and a 132 dp group at the emulator's 2.44 density (43.9 dp and 132.1 dp); green alone with the one-pixel room (18 tests) (the emulator had to be restarted; it ran headless and without audio for this lane) | A 0006, 0007; E 0001 |
| the same on the Pixel 7 Pro (`--continue`) | core 199, data 118, engine 95, app 17 green; UI 855 with the same 2 rounding failures as the emulator (132.19 dp and 44.19 dp at density 2.625); the three classes green alone: 26 tests | B 0001, 0002; E 0004 |
| the same on the Pixel Tablet (`--continue`) | green in 841 s: core 199, data 118, engine 95, UI 855, app 17 | B 0003 |
| the same on the OPPO CPH2239 (`--continue`) | core 199, data 118, engine 95, app 17 green; UI 855 with 7 failures, six in `OverviewStripTest` and the 411 dp bar test (the window assumptions above); the three rewritten classes green alone on it: 26 tests, 78 s | B 0004, 0005; E 0002 |
| the same on the Redmi Note 5 (`--continue`) | core 199, data 118, engine 95, app 17 green; UI 855 with 4 of the same failures; the three classes green alone: 26 tests; no Play Protect prompt, no latency failure this time | B 0006, 0007; E 0003 |

### D1 captures (2026-10-27, the debug build at `e300b7b`'s app code)

Re-captured where D1 changed the screen, same drivers as before (the accessibility ids on Android, `ScreenshotMatrix` on
the simulators, now with the `overview-strip`, `overflow-menu`, `snap-sheet` and `master-popup` states and the zoom states
through the ruler's buttons). The Pixel Tablet and the iPad stood in portrait this time (800 dp and 1024 dp windows), so
their `*-expanded-*` rows below are portrait where the earlier ones were landscape; the tablet's 800 dp window still shows
the full toolbar. The compact grid-picker captures are gone with the picker (`android-compact-grid-picker.png`,
`ios-compact-grid-picker.png`); the 600/840/expanded grid-picker files predate D1 and show the pill before it read "Snap".

| Capture | Target | Shows |
|---|---|---|
| `android-compact-demo-stopped.png`, `android-compact-demo-playing.png` | Pixel 6 Pro, 360 dp | the toolbar with "124 BPM", bar 1 at the left edge, "− 1× +" at the ruler's right end, the speaker button; playing at 00:02.54 with the chip at 2.2 |
| `android-compact-overview-strip.png` | Pixel 6 Pro | the overview strip above the transport bar, no zoom buttons |
| `android-compact-overflow-menu.png` | Pixel 6 Pro | Add audio…, Add audio as new track, Snap: 1/16, Zoom control: Buttons (checked), Zoom control: Overview strip, Metronome settings |
| `android-compact-snap-sheet.png`, `android-compact-snap-bar.png` | Pixel 6 Pro | the "Snap to grid" sheet; the lanes after choosing Bar at 2.8× (bar lines only, where 1/16 shows beats) |
| `android-compact-master-popup.png` | Pixel 6 Pro | the speaker button lit, the popup slider at 80% |
| `android-compact-metronome-sheet.png`, `android-compact-tempo-dialog.png` | Pixel 6 Pro | "124 BPM from the project" with the 4/4 signature pill; the tempo dialog from the toolbar pill |
| `android-compact-zoom-widest.png`, `android-compact-zoom-closest.png` | Pixel 6 Pro | 1× after tapping the level; 128× with the ruler at 1.1.1 / 1.1.2 / 1.1.3 |
| `android-expanded-demo-stopped.png`, `-overview-strip.png`, `-overflow-menu.png`, `-snap-sheet.png`, `-metronome-sheet.png`, `-tempo-dialog.png` | Pixel Tablet, portrait 800 dp | the full toolbar (Snap pill, tempo pill, metronome button, inline slider with "80%"), the strip, the two-entry overflow, the Snap pill's menu, the sheet with the signature pill, the dialog |
| `android-fold-open-demo-stopped.png`, `android-fold-open-overview-strip.png` | fold emulator, open | the same two states on the inner display |
| `ios-compact-*` (the nine states of the phone) | iPhone 18 Pro | the same states as the Android phone |
| `ios-expanded-*` (six states) | iPad Pro 13-inch, portrait | the same states as the tablet |
| `ios-duo-demo-stopped.png`, `ios-duo-overview-strip.png`, `ios-duo-master-popup.png` | iPhone Duo, closed, end rail | buttons, strip, popup |

Seen while capturing, not fixed: on iOS, Undo lights up after the tempo dialog's Done with no change or after the long press
on the metronome toggle (the tempo stays 124; a relaunch clears it) — T150.

### Captures after the zoom verdict (2026-10-27)

The owner kept the overview strip (T144, T151; `d249c15`): the "− level +" buttons are gone, the strip shows by default on
every device, inset from the window's sides (16 dp, or the system's back-gesture zone where wider), and the overflow's
"Overview strip" row hides or shows it per device. Every D1 capture that showed the ruler's right end or the overflow menu
was redone on the debug build of `8e89c7f`'s app code, same names, same targets, same windows (the Pixel 6 Pro at
`wm density 640`, 360 dp, reset afterwards; the Pixel Tablet and the iPad in portrait; the fold emulator open; the Duo
closed with the end rail).

How they were driven:

- Android: the debug build installed through the `gradle-run` wrapper from a detached worktree at `d152c28` (workflow
  `ca07c074`, `:androidApp:installDebug --warning-mode all` per serial, up to date, zero warnings), then the app driven by
  its accessibility ids (`scratchpad/cap/ui.py`: UiAutomator dumps, taps, key events) and the strip's handles dragged
  with `adb shell input motionevent DOWN/MOVE/UP` (an `input swipe` stopped 12 px short of its end point: the strip applies
  the last move before the lift, not the lift). The fold's PNGs come from its inner display (`screencap -d
  4619827259835644672`).
- iOS: `ScreenshotMatrix` with a new `strip-hidden` state (`d152c28`: it opens the overflow, taps
  `timeline.toolbar.overflow.overviewStrip`, waits until `timeline.overview` is gone, shoots, then shows the strip again),
  built for testing in the worktree and run per simulator with the state list. The `zoom-closest` and `zoom-widest` states
  of `d249c15` drag the window's right handle onto the window's left edge, then to the strip's right end (twice, since the
  drawn window is never narrower than 24 pt); both land on the limits as written: closest shows the ruler at 1.1.1–1.1.4,
  the scale of D1's 128×, and widest the fit view. `xcodebuild test-without-building` hung after the passing suite on the
  iPhone 18 Pro and the Duo (not on the iPad). It was interrupted by hand the first time and then by a watchdog 60 s after
  the suite reported; every PNG had been written by then.
- Each `strip-hidden` capture hides the strip and then shows it again. Every Android target was left on the Timeline with
  the strip shown (relaunched at the end, so Undo is clear). The simulators' own apps were never touched, because XCUITest
  runs on clones.

What changed in the set: the six `*-overview-strip.png` files (`android-compact-`, `android-expanded-`,
`android-fold-open-`, `ios-compact-`, `ios-expanded-`, `ios-duo-`) are deleted, because the strip is in every capture now.
A `*-strip-hidden.png` is new for each of the six form factors. The zoom and snap-bar captures now come from the strip's
handle. The other screenshots were left as they were: the pre-D1 width series, the OPPO, Redmi and Pixel 7 Pro ones, and
the states outside the D1 set (`ios-*-demo-playing`, `android-expanded-demo-playing`, `*-expanded-zoom-*`, the settings,
library, search and clip states), which predate D1.

The strip's inset, measured on the PNGs at the widest zoom (where the drawn window's outline covers the whole strip):

| Target | Window | Left | Right |
|---|---|---|---|
| Pixel 6 Pro, 360 dp | 1440 px at 4.0 | 30 dp (the 120 px back-gesture zone) | 30 dp |
| Pixel Tablet, portrait 800 dp | 1600 px at 2.0 | 30 dp from the rail's edge (the 60 px gesture zone) | 30 dp |
| fold emulator, open | 2076 px at 2.4375 | 30 dp from the rail's edge | 30.8 dp |
| iPhone 18 Pro | 1206 px at 3× | 16 pt | 16 pt |
| iPad Pro 13-inch (M5), portrait | 2064 px at 2× | 20 pt from the lanes' left edge (the strip lies under the lanes, as in D1) | 20 pt |
| iPhone Duo, closed | 1398 px at 3× | 20 pt | 84.3 pt short of the end rail (D1's strip reached the rail) |

| Capture | Target | Shows |
|---|---|---|
| `android-compact-demo-stopped.png` | Pixel 6 Pro, 360 dp | "124 BPM" in the toolbar, bars 1–25 across the ruler with nothing over its right end, the strip above the transport bar 30 dp in from both sides, the speaker button |
| `android-compact-demo-playing.png` | Pixel 6 Pro | playing at 00:02.50 with the chip at 2.2 |
| `android-compact-overflow-menu.png` | Pixel 6 Pro | Add audio…, Add audio as new track, Snap: 1/16, Overview strip (checked), Metronome settings |
| `android-compact-strip-hidden.png` | Pixel 6 Pro | the same Timeline without the strip: the lanes reach the transport bar |
| `android-compact-snap-sheet.png` | Pixel 6 Pro | "Snap to grid": Bar, 1/4, 1/8, 1/16 (checked), Off |
| `android-compact-snap-bar.png` | Pixel 6 Pro | Bar at 2.80× (the window dragged to 429 of the strip's 1200 px): bars 1–11 in the ruler, bar lines only; snap set back to 1/16 afterwards |
| `android-compact-master-popup.png` | Pixel 6 Pro | the speaker button lit, the popup slider at 80 % (the popup covers the strip's left part) |
| `android-compact-metronome-sheet.png`, `android-compact-tempo-dialog.png` | Pixel 6 Pro | "124 BPM from the project" with the 4/4 pill (from the overflow's Metronome settings); the tempo dialog from the toolbar pill |
| `android-compact-zoom-widest.png` | Pixel 6 Pro | the right handle dragged past the strip's right end: the fit view, the window over the whole strip |
| `android-compact-zoom-closest.png` | Pixel 6 Pro | the right handle dragged onto the window's left edge: the ruler at 1.1.1 / 1.1.2 / 1.1.3 / 1.1.4, the window drawn at its 24 dp minimum |
| `android-expanded-demo-stopped.png`, `-overflow-menu.png`, `-strip-hidden.png`, `-snap-sheet.png`, `-metronome-sheet.png`, `-tempo-dialog.png` | Pixel Tablet, portrait 800 dp | the full toolbar (Snap pill, tempo pill, metronome button) and the inline slider at 80 % with the strip; the overflow holding only "Overview strip" (checked); no strip; the Snap pill's menu; the sheet from the toolbar's metronome button; the dialog |
| `android-fold-open-demo-stopped.png`, `android-fold-open-strip-hidden.png` | fold emulator, open | the strip, then none |
| `ios-compact-demo-stopped.png`, `-overflow-menu.png`, `-strip-hidden.png`, `-snap-sheet.png`, `-master-popup.png`, `-tempo-dialog.png`, `-metronome-sheet.png`, `-zoom-closest.png`, `-zoom-widest.png` | iPhone 18 Pro | the Android phone's states (the D1 iOS set has no `demo-playing` or `snap-bar`); the strip 16 pt in |
| `ios-expanded-demo-stopped.png`, `-overflow-menu.png`, `-strip-hidden.png`, `-snap-sheet.png`, `-tempo-dialog.png`, `-metronome-sheet.png` | iPad Pro 13-inch, portrait | the tablet's states; the strip under the lanes column |
| `ios-duo-demo-stopped.png`, `ios-duo-strip-hidden.png`, `ios-duo-master-popup.png` | iPhone Duo, closed, end rail | the strip (ending 84 pt before the rail), none, the popup |

Seen while capturing, not fixed:

- Beside a navigation rail the strip is inset twice. `OverviewStripDefaults.sideInsets()` pads by the window's gesture
  zones (`WindowInsets.systemGestures`), but the shell pads its content for `safeDrawing` only and consumes nothing. So the
  strip insets itself by a window-edge zone that the rail already covers: 84 pt on the Duo's end-rail side (the end column
  iOS reserves, which `EndRailFrame` fills with the rail), and 30 dp from the rail on the Pixel Tablet and the fold
  (where the back zone lies under the rail; 16 dp would do).
- T150 is not iOS-only: on the Pixel Tablet, Undo lights up after the tempo dialog's Done with no change (relaunched,
  then Done alone; the two strip toggles leave Undo off). It shows in the iOS metronome-sheet captures, which the matrix
  takes after the tempo dialog.
- At 360 dp the metronome sheet's "Standard" pill sits under the gesture handle at the bottom edge (it did in D1 too).

### The zoom-control comparison install (T143, 2026-10-27 02:00)

The D1 release build (`fa0fb41`'s profiles, signed with the debug key, compiled from the profile everywhere but the Redmi)
is installed and running on all ten targets, with the zoom control set per device so the owner can compare the two:

| Zoom control | Targets | How it was set |
|---|---|---|
| Buttons (the default) | Pixel 6 Pro, Pixel Tablet, iPhone 18 Pro, iPad Pro 13-inch, iPhone 14 | nothing to set |
| Overview strip | Pixel 7 Pro, OPPO CPH2239, Redmi Note 5, fold emulator, iPhone Duo | Android: the overflow menu driven by accessibility ids (`scratchpad/set_zoom_control.py`); the Duo: `timeline.zoomControl=overviewStrip` written into the app's `Library/Application Support/datastore/tembra.preferences_pb` (the DataStore map encoded by hand, 41 bytes; `xcodebuild test` runs on simulator clones, so a UI test cannot set the real simulator's preference) |

Either control can be flipped on any device from the toolbar's overflow menu ("Zoom control"). The owner keeps one; T144
removes the other.

## Defects the matrix found (fixed, with a device test each)

1. The playhead overlay's full-size semantics node hid every lane, header and clip from TalkBack and UiAutomator
   (Android drops accessibility nodes a later sibling covers entirely). The accessible playhead is now an 8 dp node on
   the line; the Android perf journey and FR-058 depend on this.
2. Dialogs, menus and the bottom sheet exposed no accessibility ids (each is its own window without
   `testTagsAsResourceId`); `anchorRoot()` is now applied at their roots.
3. The transport bar overflowed at 360–411 dp: the one-line readout ran under the step-forward button and the
   metronome toggle. Below 520 dp the bar tightens its gaps, shortens the slider to 56 dp and stacks the readout; a
   system font scale that would still not fit shrinks the digits to the room (FR-059). Instrument chips grew with the
   font (descenders were cut at 2.0).
4. The demo card's children carried the project's id while the card carried `projects.card.demo`; the children
   now hang off the card's tag.
5. Found by the first trial round (the metronome journey took 6.1 s against 1.8 s before and its start never showed
   "Stop"): with a project open and stopped, the session kept the engine's transport, so the standalone metronome and
   the tuner were refused the audio device (`TEMBRA_ERR_STATE`) when chosen from the Timeline. The shell now tells the
   session which destination it is about to show (`PlaybackGuard.transportHostVisible`, `Destination.hostsTransport`):
   the session closes the transport synchronously before a non-hosting destination appears and takes it back, with
   the tempo, master, arrangement, click and the stopped position re-pushed, when the Timeline returns; play right
   after returning waits for that. On the Pixel the metronome and the tuner now start from a stopped Timeline and
   playback resumes on return. An Opus read-only review of the first version found the holes a quick user would hit,
   all fixed and covered by six session tests over a fake engine that can hold an open or answer "busy": a leave that
   lands while the device is still reopening now closes it again as soon as the open returns (the open runs
   non-cancellable); the session records which destination is on screen even before a project is open, so a tap on
   Tuner during the start-up open leaves the device to the Tuner; play after a failed reattach binds the transport
   fully (tempo, master, arrangement, click, position) instead of opening a bare one; click settings changed on the
   Metronome destination no longer reach the engine while it belongs to that screen; the retries last up to five
   seconds (the other screen lets go after its navigation fade); closing a project closes the transport before its
   clip sources (the engine refuses to close a source its arrangement still references). A second Opus review of that
   version found the races between two opens: the Android open suspends on the I/O dispatcher, a cancelled attach keeps
   its open running, and a second open while one is in flight is refused by the engine and drops the audio focus with
   it. Now one transport mutex serialises every open and close except the synchronous hand-over, `transportOpen` only
   turns true inside it, install re-checks the host after its open and takes the device back if the host returned
   while the sources opened, a start job runs once and re-checks the state after every suspension (a pause while play
   waits for the transport starts nothing and releases the playback host), a pause from `Starting` keeps the stored
   position, and a scrub cannot overtake the bind's own seek. The fake engine now refuses a second open like the real
   one. 41 session tests, 11 of them on the hand-over.

6. **Two UI tests assumed a wide window** (found by the OPPO CPH2239, 360 dp wide, on 2026-10-25). `Modifier.width(411.dp)`
   is coerced into the device window, so the reference-phone transport bar test measured a 360 dp bar and its readout
   budget came out at 59.5 dp; the inline track-settings test composed the expanded layout into a phone window, where
   the open form pushed the second header below the fold and its tap landed outside the screen. The bar test now uses
   `requiredWidth`, the inline-row test scrolls the second header into view before tapping; the two rerun alone on the
   OPPO and the Redmi (393 dp) in the final pass.
7. **The measurement build's health-write dot had a 28 × 48 dp accessibility node** (the D2 stress rerun, Pixel 6 Pro,
   2026-10-25): Compose grows every clickable's touch bounds to the view configuration's 48 dp minimum and Android
   accessibility reports those bounds, so the 8 dp dot's node, clipped at the screen edge, had its centre on the
   Timeline's overflow button; UiAutomator's tap there opened the menu and the next anchor vanished behind the pop-up
   window. Reproduced by hand with `uiautomator dump`; Material's `LocalMinimumInteractiveComponentSize` was the wrong
   knob (D3 failed the same way, the node still 28 × 48 dp), a `ViewConfiguration` with a zero minimum touch target
   around the dot is the right one (a developer control, not a user's), verified by the D4 round.

8. **The readout room test ignored pixel rounding** (found by the Pixel 9 Pro Fold emulator, density 390, on 2026-10-25):
   a 70 dp room is laid out as 171 px there, which reads back as 70.15 dp, so "the readout fits its room" failed by a
   sixth of a dp while the readout filled the room exactly. The two room assertions in `TimeReadoutTest` now allow one
   pixel; rerun alone on the emulator in the final pass.

## Manual and scripted checks on the Pixel 6 Pro (2026-10-19)

| Check | Result |
|---|---|
| SC-008 force-quit loop (`scratchpad/forcequit.py`): add a track, force-quit 2 s later, relaunch, twenty times | 20 of 20 cycles kept every edit (the saved document counted 1…20 tracks) |
| T088 notification while playing | "Playing" / "Demo project", a foreground service, after the notification permission was granted |
| T088 screen off while playing | 20 s with the screen off: readout 00:03 → 00:28, still playing afterwards |
| T135 another app takes the audio (YouTube started by intent) | paused at 00:15 and did not resume on return |
| T135 headphones unplugged | cannot be proven from the shell: the receiver is registered `RECEIVER_NOT_EXPORTED` (correct), so only the system's own broadcast reaches it; owner's physical unplug stays open |
| T135 incoming call, Bluetooth, ten minutes screen off, the Duo poses | open for the owner (see "Left to the owner") |
| SC-010 library with 174 projects (seed 170) | scroll: 636 frames, 10 janky (1.6 %), p50 5 ms, p90 8 ms, p99 21 ms. Open and search-narrowing could not be timed through UiAutomator (its dump alone takes 2.4 s on that screen); the perf trial round's `timeline.library.open` section is the measurement |
| Import refusal | a four-channel WAV from Downloads refused: "1 file could not be added: four-channel.wav (more than two channels)" |
| Unavailable audio | the duplicated demo with its audio files removed opens with outline clips and the names |
| SC-009 portability (Pixel → iPhone 17 simulator) | the demo's folder (`project.json`, `view.json`, four audio copies; no device path in the document) copied into the simulator's `Documents/projects/`; the next launch indexed it as a user project (124 BPM, 30.97 s, 4 tracks, 17 MB) and opening it shows the identical arrangement (`ios-compact-portability.png`) |

## Known nits (not blocking)

- On the expanded layout the playhead line is drawn across the inline track-settings row as well as the lanes.
- The play/pause button's accessible name ("Play"/"Pause") sits on a child of the clickable node in the Android
  accessibility tree (feature 004's `PlayPauseButton`); TalkBack still reads it from the child.
- The Redmi Note 5 (API 28) renders every screen but its UiAutomator tree does not list `screen.projects`, so its
  library capture was taken by hand after a scripted tap.
- The expanded transport bar packs its controls at the start of the bar (spec FR-004 fixes only the order).

## Left to the owner (needs a hand or an ear at the device)

- Quickstart §3 timing evidence (SC-002/SC-003): a recording of the output on a wired route to measure play within
  100 ms and the playhead within 30 ms; the lanes and the perf round measure the sections, not the audible output.
- Quickstart §4.7 split audio (T106) and §6.2 the ten-minute click recordings at 40, 124 and 240 BPM in 4/4 and 7/8
  (T111, SC-004): offline analysis of a recording; the Catch2 renderer tests prove the sample-identical split and the
  anchored click within one frame, the device recording is the owner's confirmation.
- Quickstart §8 on the Pixel 6 Pro and the iPhone 14: an incoming call, headphones unplugged, a Bluetooth output,
  ten minutes with the screen off (20 s was scripted), and the dialog on the iPhone (no automation reaches a physical
  iPhone's screen).
- TalkBack and VoiceOver walkthroughs by ear: the accessibility trees were inspected instead (every track, clip, the
  playhead and the readout read with their descriptions after the overlay fix).
- The SC-013 first-use walkthrough with a musician who has not used the app.
