package audio.tembra.ui.tuner

/** Which string is sounding, whether it loops and what plays for it; at most one (data-model.md §6). */
data class AuditionState(val stringIndex: Int, val loop: Boolean, val sound: ReferenceSound)
