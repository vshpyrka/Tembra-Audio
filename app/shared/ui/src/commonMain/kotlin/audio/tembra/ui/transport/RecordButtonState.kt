package audio.tembra.ui.transport

/**
 * The transport's Record control (feature 010, spec FR-007): [Hidden] in bars narrower than 432 dp (Record then sits
 * in the armed track's compact header), [Idle] (the outline), [CountIn] (pulsing once per beat; a steady state under
 * reduce motion) and [Recording] (lit).
 */
enum class RecordButtonState {
    Hidden,
    Idle,
    CountIn,
    Recording,
}
