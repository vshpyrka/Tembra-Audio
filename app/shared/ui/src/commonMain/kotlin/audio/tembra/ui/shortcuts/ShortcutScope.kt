package audio.tembra.ui.shortcuts

/**
 * Where a shortcut works (feature 011, data-model.md "Keyboard shortcuts"): [Everywhere] on every destination, the
 * others only while their destination shows; [Mixer] (feature 012) on a focused strip of the mixer sheet, which lives
 * inside the Timeline. The order is the shortcut sheet's: everywhere first, then the destinations in navigation order,
 * the mixer after its Timeline.
 */
enum class ShortcutScope { Everywhere, Timeline, Mixer, Tuner, Metronome, Projects }
