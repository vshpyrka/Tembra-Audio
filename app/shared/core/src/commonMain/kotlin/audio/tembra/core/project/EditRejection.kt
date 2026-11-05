package audio.tembra.core.project

/** Every reason [ProjectEditor] refuses an edit, each a small data holder the UI can name in a message. */
sealed interface EditRejection {
    data class UnknownTrack(val id: TrackId) : EditRejection

    data class UnknownClip(val id: ClipId) : EditRejection

    data class UnknownAsset(val id: AudioAssetId) : EditRejection

    /** The trimmed project name is empty or longer than 60 characters. */
    data object ProjectNameOutOfRange : EditRejection

    /** The trimmed description is longer than 120 characters. */
    data object DescriptionTooLong : EditRejection

    /** The trimmed track name is longer than 40 characters. */
    data object TrackNameTooLong : EditRejection

    /** Move up on the first track or move down on the last. */
    data object TrackAtEnd : EditRejection

    /** The split position is not strictly inside the clip. */
    data object SplitOutsideClip : EditRejection

    /** The split position lies within a hundredth of a second of a clip edge. */
    data object SplitTooCloseToEdge : EditRejection

    /** A cross-track move whose span another clip already occupies. */
    data object SpanOccupied : EditRejection

    /** The arrangement already holds the engine's maximum of tracks. */
    data object TrackLimitReached : EditRejection

    /** The arrangement already holds the engine's maximum of clips. */
    data object ClipLimitReached : EditRejection

    /** The take is shorter than a hundredth of a second at its asset's rate (feature 010, spec FR-011). */
    data object TakeTooShort : EditRejection

    /** A pan outside −100..100 (feature 012, spec FR-006); the controls clamp before they edit, so only a bad caller sees it. */
    data object PanOutOfRange : EditRejection
}
