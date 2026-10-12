package audio.tembra.ui.perf

import androidx.compose.ui.Modifier

/**
 * Applied once to the app root: on Android it exposes every `testTag` as a view resource id so UiAutomator (the
 * Macrobenchmark journeys) can find the [PerfAnchors]; on iOS the tags already reach XCUITest as accessibility
 * identifiers, so it is the identity.
 */
internal expect fun Modifier.anchorRoot(): Modifier
