# The keyboard and pointer pass on the Android 14 desktop emulator (feature 012, T092)

`Tembra_Desktop_API34` (`emulator-5580`, 3840 × 2160 at 320 dpi, no touchscreen, booted with `-no-audio`), the branch's
debug build with the two focus fixes of 2026-11-09 in a 1600 × 950 dp free-form window; the demo project for the key
steps, the 24-track stress project for the row. Driven over adb (`input keyevent`, `input mouse tap`, `uiautomator dump`);
the findings and the result table are in `../../NOTES.md` § T092.

| File | What it shows |
|---|---|
| `01-x-open.png` | X pressed on the timeline: the sheet open at its half height, the focus ring on the handle |
| `02a-fader-after-up-up.png` | Tab to the Drums fader, Up twice: the header's readout at +1.0 dB |
| `02b-after-M.png` | M on the focused strip: Drums muted (the strip's M and the header's M lit) |
| `02c-after-O.png` | O on the focused strip: Drums soloed |
| `02-fader-focused.png` | after Home, M and O again: the strip back at 0 dB, unmuted, unsoloed, the fader focused |
| `03-full-form-after-enter.png` | Enter on the handle: the sheet at its full height, the dials and knobs shown |
| `03a-knob-after-right-right.png` | Tab, Tab to the Drums knob, Right twice: the indicator off centre, the focus tooltip "Drums, pan" |
| `03b-knob-after-page-up.png` | Page Up on the knob: ten percent further right |
| `03-full-form.png` | after Home on the knob: centred again |
| `05-after-back.png` | Back with the handle focused: the focus cleared, the sheet still open (the second Back closes it) |
| `04-hover-tooltip.png`, `05-after-drag.png`, `06-secondary-click.png`, `07-master.png` | the pointer steps the emulator could not deliver (the tablet's events stop at the kernel): the sheet unchanged |
| `08-row-24-strips-hover.png`, `09-row-scrolled-by-wheel.png` | the 24-track stress project's sheet: nineteen strips and the master at 1600 dp; the wheel did not reach the row either |
