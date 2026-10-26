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
