# Android screenshots (feature 012, T089)

Taken 2026-11-08 with the branch's debug build (`:androidApp:installDebug`, the owner's go for the real devices) through
`uiautomator` by Compose test tag over adb (the scratchpad script `capture_android.py`: the demo project opened from
Projects, the sheet from the toolbar's Mixer button or the compact bar's overflow row, every width through `wm density`,
the status bar in demo mode). The run conditions were applied first (music stream muted, do-not-disturb, brightness floor):
nothing sounded. `mixer-clipped` is live playback with every strip's fader at +6 dB and the master at 100 % (undone after);
`mixer-armed` is a fifth track added and armed, the row scrolled to its strip (the input meter reads the room); the
`font2` files are `font_scale 2.0`.

## Pixel 6 Pro (`19021FDEE001AH`, Android 17, 1440 × 3120 px)

| Width (`wm density`) | Files (`pixel-6-pro/`) | Notes |
|---|---|---|
| 360 dp (640) | `360dp-mixer-half`, `360dp-mixer-full`, `360dp-mixer-clipped`, `360dp-mixer-armed`, `360dp-track-settings-pan`, `360dp-mixer-landscape` | three strips and the master at the half height; the full sheet is `FullNoVu` (the lanes box is under 480 dp on a 780 dp screen): knobs, no dials |
| 360 dp, font 2.0 | `360dp-font2-mixer-half`, `360dp-font2-mixer-full`, `360dp-font2-track-settings-pan` | the labels grow, the names ellipsise ("Dru…"); the master's "Master" clipped to "Mast" in this capture and now ellipsises (fixed after the capture, `MasterStrip`) |
| 600 dp (384) | `600dp-mixer-half`, `600dp-mixer-full`, `600dp-mixer-clipped`, `600dp-mixer-armed`, `600dp-track-settings-pan` | the medium layout: the toolbar carries the Mixer button inline |
| 600 dp, font 2.0 | `600dp-font2-mixer-half`, `600dp-font2-mixer-full` | |
| 840 dp (274) | `840dp-mixer-half`, `840dp-mixer-full`, `840dp-mixer-clipped`, `840dp-mixer-armed`, `840dp-track-settings-pan` | the expanded layout with the pinned header column; the half sheet leaves two lanes visible |
| 840 dp, font 2.0 | `840dp-font2-mixer-half`, `840dp-font2-mixer-full` | |

## Pixel Tablet (`3621105H803UFX`, Android 17, 2560 × 1600 px landscape)

| Width (`wm density`) | Files (`pixel-tablet/`) | Notes |
|---|---|---|
| 1024 dp (400) | `1024dp-mixer-half`, `1024dp-mixer-full`, `1024dp-mixer-clipped`, `1024dp-mixer-armed`, `1024dp-track-settings-pan` | the handle carries "Open in a window" (the tablet reports free-form window management); the full sheet is `FullNoVu` on the 640 dp-tall screen |
| 1200 dp (341) | `1200dp-mixer-half`, `1200dp-mixer-full`, `1200dp-mixer-armed` | |

## Pixel 9 Pro Fold emulator (`emulator-5584`, API 36, booted with `-no-audio`)

| Posture | Files (`pixel-9-pro-fold/`) | Notes |
|---|---|---|
| open (inner display 2076 × 2152 px, about 850 × 880 dp) | `open-mixer-half`, `open-mixer-full`, `open-mixer-clipped`, `open-mixer-armed`, `open-track-settings-pan`, `open-mixer-landscape` | the expanded layout with the rail; the half sheet is the compact form, the full sheet the full form with dials and knobs on the 880 dp-tall display; the emulator's `screencap` prepends a "multiple displays" warning to the PNG stream, stripped from the PNG signature on |
| closed (outer display 1080 × 2424 px, about 443 × 995 dp) | `closed-mixer-half`, `closed-mixer-full`, `closed-mixer-clipped`, `closed-mixer-armed`, `closed-track-settings-pan` | the compact layout; folding (`adb emu fold`) locks the emulator's screen, dismissed with the swipe keyguard before the run; `closed-mixer-landscape` is kept as evidence of an emulator quirk: in the closed posture the landscape window renders in the left 890 px of the 2424 px display (no app bug on a real device is claimed; the Pixel 6 Pro's landscape capture is the reference) |

## Not in this set

- The desktop window (`Tembra_Desktop_API37`): the second-window captures belong to T105 (`desktop-api37/`).
