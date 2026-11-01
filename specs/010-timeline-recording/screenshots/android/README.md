# Android screenshots (feature 010, T080)

## Fold emulator (Pixel_9_Pro_Fold AVD, Android 16, release build, 2026-11-01)

Release build (debug key, baseline profile compiled), demo project, one added track (Track 1, row 5) armed. Open = inner display 2076x2152 (`screencap -d 4619827259835644672`), closed = outer display 1080x2424 (`screencap -d 4619827551948147201`); `adb emu fold` / `adb emu unfold`. Microphone and notification permissions granted by `pm grant` beforehand (the runtime dialogs otherwise cover the screen).

| File (fold-emulator/) | Posture | State |
|---|---|---|
| open-armed.png | open (inner) | Track 1 armed, transport idle |
| open-countin.png | open | Record pressed, count-in |
| open-recording.png | open | recording, playhead running |
| open-take.png | open | recording stopped, take on Track 1 (below the visible rows; overview strip shows it) |
| closed-armed.png | closed (outer) | armed, playhead at the start |
| closed-recording.png | closed | recording (Take 3 growing, record button filled) |
| closed-take.png | closed | finished takes (Take 1, Take 2) on Track 1 |

## Phone and tablet widths (T080, 2026-11-01)

Release build (debug key) on the Redmi Note 5 (Android 9, 360 and 432 dp through `wm density`) and the Pixel Tablet (600, 840, 1024 dp), demo project, microphone granted; the room is the input and the music stream is silent, so `armedLoud` and `armedClipped` could not be produced. Caveats: the project persisted between width runs, so the armed track carries a take from an earlier width and the Redmi list holds extra "Track n" rows (the Redmi adds a track per tap more than once; the list did not scroll the new track into view, it was scrolled by hand); the list was not undone to four tracks (the app was restarted per width instead); `recording` and the dialog/sheet/monitor shots on the Redmi were taken while the engine state lagged on the slow display; `barRecord432` is the armed rest frame at 432 dp (the bar's Record is `transport.record`). On the tablet the notification permission prompt appeared at the first Record and was answered "Allow".

| Device | Width (dp) | State | File |
|---|---|---|---|
| Redmi Note 5 (e9446ac2) | 360 | armedRest | `redmi-note-5/360dp-armedRest.png` |
| Redmi Note 5 (e9446ac2) | 360 | countIn | `redmi-note-5/360dp-countIn.png` |
| Redmi Note 5 (e9446ac2) | 360 | monitorSuspended | `redmi-note-5/360dp-monitorSuspended.png` |
| Redmi Note 5 (e9446ac2) | 360 | recording | `redmi-note-5/360dp-recording.png` |
| Redmi Note 5 (e9446ac2) | 360 | sheetCountIn | `redmi-note-5/360dp-sheetCountIn.png` |
| Redmi Note 5 (e9446ac2) | 360 | stopRecordingDialog | `redmi-note-5/360dp-stopRecordingDialog.png` |
| Redmi Note 5 (e9446ac2) | 360 | takeDone | `redmi-note-5/360dp-takeDone.png` |
| Redmi Note 5 (e9446ac2) | 432 | armedRest | `redmi-note-5/432dp-armedRest.png` |
| Redmi Note 5 (e9446ac2) | 432 | barRecord432 | `redmi-note-5/432dp-barRecord432.png` |
| Redmi Note 5 (e9446ac2) | 432 | countIn | `redmi-note-5/432dp-countIn.png` |
| Redmi Note 5 (e9446ac2) | 432 | monitorSuspended | `redmi-note-5/432dp-monitorSuspended.png` |
| Redmi Note 5 (e9446ac2) | 432 | recording | `redmi-note-5/432dp-recording.png` |
| Redmi Note 5 (e9446ac2) | 432 | sheetCountIn | `redmi-note-5/432dp-sheetCountIn.png` |
| Redmi Note 5 (e9446ac2) | 432 | stopRecordingDialog | `redmi-note-5/432dp-stopRecordingDialog.png` |
| Redmi Note 5 (e9446ac2) | 432 | takeDone | `redmi-note-5/432dp-takeDone.png` |
| Pixel Tablet (3621105H803UFX) | 600 | armedRest | `pixel-tablet/600dp-armedRest.png` |
| Pixel Tablet (3621105H803UFX) | 600 | countIn | `pixel-tablet/600dp-countIn.png` |
| Pixel Tablet (3621105H803UFX) | 600 | monitorSuspended | `pixel-tablet/600dp-monitorSuspended.png` |
| Pixel Tablet (3621105H803UFX) | 600 | recording | `pixel-tablet/600dp-recording.png` |
| Pixel Tablet (3621105H803UFX) | 600 | sheetCountIn | `pixel-tablet/600dp-sheetCountIn.png` |
| Pixel Tablet (3621105H803UFX) | 600 | stopRecordingDialog | `pixel-tablet/600dp-stopRecordingDialog.png` |
| Pixel Tablet (3621105H803UFX) | 600 | takeDone | `pixel-tablet/600dp-takeDone.png` |
| Pixel Tablet (3621105H803UFX) | 840 | armedRest | `pixel-tablet/840dp-armedRest.png` |
| Pixel Tablet (3621105H803UFX) | 840 | countIn | `pixel-tablet/840dp-countIn.png` |
| Pixel Tablet (3621105H803UFX) | 840 | monitorSuspended | `pixel-tablet/840dp-monitorSuspended.png` |
| Pixel Tablet (3621105H803UFX) | 840 | recording | `pixel-tablet/840dp-recording.png` |
| Pixel Tablet (3621105H803UFX) | 840 | sheetCountIn | `pixel-tablet/840dp-sheetCountIn.png` |
| Pixel Tablet (3621105H803UFX) | 840 | stopRecordingDialog | `pixel-tablet/840dp-stopRecordingDialog.png` |
| Pixel Tablet (3621105H803UFX) | 840 | takeDone | `pixel-tablet/840dp-takeDone.png` |
| Pixel Tablet (3621105H803UFX) | 1024 | armedRest | `pixel-tablet/1024dp-armedRest.png` |
| Pixel Tablet (3621105H803UFX) | 1024 | countIn | `pixel-tablet/1024dp-countIn.png` |
| Pixel Tablet (3621105H803UFX) | 1024 | monitorSuspended | `pixel-tablet/1024dp-monitorSuspended.png` |
| Pixel Tablet (3621105H803UFX) | 1024 | recording | `pixel-tablet/1024dp-recording.png` |
| Pixel Tablet (3621105H803UFX) | 1024 | sheetCountIn | `pixel-tablet/1024dp-sheetCountIn.png` |
| Pixel Tablet (3621105H803UFX) | 1024 | stopRecordingDialog | `pixel-tablet/1024dp-stopRecordingDialog.png` |
| Pixel Tablet (3621105H803UFX) | 1024 | takeDone | `pixel-tablet/1024dp-takeDone.png` |
