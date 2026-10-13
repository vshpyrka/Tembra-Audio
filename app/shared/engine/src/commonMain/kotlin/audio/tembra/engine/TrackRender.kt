package audio.tembra.engine

/** One track as the engine mixes it (`tembra_track_state`): the gain target and whether the solo/mute table makes it audible. */
data class TrackRender(val gainLinear: Float, val audible: Boolean)
