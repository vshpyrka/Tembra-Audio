package audio.tembra.ui.perf

import androidx.compose.runtime.Composable

/**
 * Tells the platform the screen is fully drawn once [ready] turns true (spec FR-017): on Android it is
 * `androidx.activity.compose.ReportDrawnWhen`, which ends Macrobenchmark's `timeToFullDisplayMs`; on iOS the launch
 * metric's "responsive" point is OS-defined, so it is a no-op. Call it from a wiring composable with the semantic
 * first-content state (`TunerUiState.isFirstContentReady`), never with a derived rendering detail.
 */
@Composable
expect fun ReportFirstContentDrawn(ready: Boolean)
