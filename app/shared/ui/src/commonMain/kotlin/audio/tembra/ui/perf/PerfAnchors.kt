package audio.tembra.ui.perf

/**
 * The journey anchors (feature 007, spec FR-017): the `testTag` values the benchmark journeys reach on both
 * platforms, UiAutomator through `testTagsAsResourceId` on the Android root ([anchorRoot]) and XCUITest through
 * Compose Multiplatform's accessibility-identifier mapping. The values are the screens' existing stable tags, so
 * the UI tests and the journeys share one name per control; a journey that needs a new control adds its tag here
 * (constitution: every feature instruments itself).
 */
object PerfAnchors {
    /** The start destination's root, where every journey begins: Timeline since Home was removed (2026-10-17). */
    const val START_ROOT = "screen.timeline"
    const val NAV_TUNER = "nav.item.tuner"
    const val NAV_METRONOME = "nav.item.metronome"
    const val TUNER_ROOT = "screen.tuner"
    const val TUNER_MODE_AUTO_LISTEN = "tuner.mode.autoListen"
    const val TUNER_MODE_BY_EAR = "tuner.mode.byEar"
    const val TUNER_NOTE_READOUT = "tuner.meter"
    const val METRONOME_ROOT = "screen.metronome"
    const val METRONOME_START_STOP = "metronome.play"
    const val METRONOME_TEMPO_UP = "metronome.dial.up"
    const val METRONOME_TEMPO_DOWN = "metronome.dial.down"
    const val METRONOME_TEMPO_READOUT = "metronome.tempoReadout"
    const val DEVTOOLS_WRITE_HEALTH = "devtools.writeHealth"

    /** Every anchor, for the parity test and the journey drivers' documentation. */
    val all: List<String> = listOf(
        START_ROOT, NAV_TUNER, NAV_METRONOME,
        TUNER_ROOT, TUNER_MODE_AUTO_LISTEN, TUNER_MODE_BY_EAR, TUNER_NOTE_READOUT,
        METRONOME_ROOT, METRONOME_START_STOP, METRONOME_TEMPO_UP, METRONOME_TEMPO_DOWN, METRONOME_TEMPO_READOUT,
        DEVTOOLS_WRITE_HEALTH,
    )
}
