package audio.tembra.ui.tracksettings

import audio.tembra.core.project.GainDb
import audio.tembra.core.project.InstrumentLabel
import audio.tembra.core.project.TrackId

/**
 * The unsaved values of an open track-settings form (spec FR-042): handed through `ProjectSession.settingsDraft`
 * between the nested screen and the inline row when the window crosses 840 dp, so nothing typed is lost.
 */
data class TrackSettingsDraft(
    val trackId: TrackId,
    val name: String,
    val label: InstrumentLabel?,
    val gainDb: GainDb,
)
