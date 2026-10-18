package audio.tembra.ui.session

import audio.tembra.engine.ArrangementSnapshot

/** What [ArrangementMapper.map] produced. */
sealed interface ArrangementResult {
    data class Mapped(val snapshot: ArrangementSnapshot) : ArrangementResult

    /** More tracks or clips than the engine's fixed arrangement holds; the session reports it and keeps the last snapshot. */
    data object TooLarge : ArrangementResult
}
