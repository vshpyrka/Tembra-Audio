package audio.tembra.ui.tracksettings

import audio.tembra.ui.perf.PerfAnchors

/** Every `testTag` of the track-settings form and screens (contracts/timeline-screen.md "Semantics"). */
object TrackSettingsTestTags {
    const val ROOT = "screen.trackSettings"
    const val INLINE = "timeline.settings.inline"
    const val TITLE = "trackSettings.title"
    const val CLOSE = PerfAnchors.TIMELINE_SETTINGS_CLOSE
    const val FORM = "trackSettings.form"
    const val NAME = "trackSettings.name"
    const val LABEL = "trackSettings.label"
    const val LABEL_MENU = "trackSettings.label.menu"
    const val GAIN_SLIDER = "trackSettings.gainSlider"
    const val GAIN = "trackSettings.gain"
    const val MUTE = "trackSettings.mute"
    const val SOLO = "trackSettings.solo"
    const val DELETE = "trackSettings.delete"
    const val EFFECTS = "trackSettings.effects"
    const val EFFECTS_TEXT = "trackSettings.effects.text"
}
