package audio.tembra.core.project

/** One labelled mark of the fader's scale (feature 012): where along the throw (0 at the bottom, 1 at the top) and what it reads. */
data class FaderTick(val throwPosition: Float, val label: String)
