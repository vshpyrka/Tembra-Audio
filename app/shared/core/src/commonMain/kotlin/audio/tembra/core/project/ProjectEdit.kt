package audio.tembra.core.project

/**
 * Every undoable change to a project (spec FR-035; data-model.md section 1): a small data holder that
 * [ProjectEditor.apply] turns into the next immutable snapshot. Edits that create entities carry their fresh ids, so
 * applying an edit is pure and replayable. Not edits: the metronome toggle (saved, not undoable), grid, zoom, scroll
 * and selection.
 */
sealed interface ProjectEdit
