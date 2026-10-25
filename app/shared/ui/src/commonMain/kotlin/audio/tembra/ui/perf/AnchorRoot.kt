package audio.tembra.ui.perf

import androidx.compose.ui.Modifier

/**
 * Applied to the app root and to the root of every window of its own (dialogs, menus, the bottom sheet, which
 * Compose hosts in separate windows): on Android it exposes every `testTag` as a view resource id so UiAutomator
 * (the Macrobenchmark journeys, TalkBack tooling) can find the [PerfAnchors] and the test tags; on iOS the tags
 * already reach XCUITest as accessibility identifiers, so it is the identity.
 */
internal expect fun Modifier.anchorRoot(): Modifier
