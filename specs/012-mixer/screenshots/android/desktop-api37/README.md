# The mixer in its own window (feature 012, T105; quickstart §12)

`Tembra_Desktop_API37` (Android 17 "Desktop (Preview)", `emulator-5582`, user 10, booted with `-no-audio`), the branch's
debug build, 2026-11-08. Driven over adb by Compose test tag and content description (`uiautomator dump` lists the
focused window only, so each window was focused with a tap before its controls were read); the windows were placed with
`am task resize`.

| File | What it shows |
|---|---|
| `window-open.png` | the sheet's "Open in a window" tapped: the sheet closed and the console (four strips and the master) opened in a second free-form window of the app, at its 720 × 480 dp default over the timeline window |
| `window-beside.png` | the mixer window moved beside the timeline window |
| `window-fader.png` | the Drums fader dragged in the mixer window: the timeline window's header reads the same −13.9 dB |
| `window-undo.png` | Undo pressed in the timeline window: both windows back at +0.0 dB (the drag was one step) |
| `window-x-front.png` | the timeline window focused, `X` pressed: the mixer window came to the front (`topResumedActivity` moved from `MainActivity` to `MixerWindowActivity`) |
| `window-closed.png` | the timeline window closed from its caption button: the mixer window and its task went with it (the app's task count read 0) |

Findings: the window's strips show the VU dial and knob when the window is tall enough and drop them in the same order
as the sheet; a project switch in the timeline keeps the window on the new project (the session never has no project,
so "the project's close" is the timeline window's close); Back on the timeline window's root moves the task to the
background as Android 12+ does and closes nothing, the caption's close button does.
