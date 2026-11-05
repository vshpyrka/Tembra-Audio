package audio.tembra.ui.timeline

import audio.tembra.ui.mixer.MixerHeight

/**
 * The mixer sheet's state the Timeline ViewModel owns for the session (feature 012, data-model.md §4, §7; spec FR-022):
 * open or closed and the height asked for. Kept across a layout rebuild and a project switch, not across launches;
 * landscape maps the height to the full one when the state is read.
 */
internal data class MixerViewState(
    val open: Boolean = false,
    val height: MixerHeight = MixerHeight.Half,
)
