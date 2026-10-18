package audio.tembra.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import audio.tembra.core.project.TrackId
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.tracksettings.TrackSettingsKey
import audio.tembra.ui.tracksettings.TrackSettingsScreen

/**
 * All Navigation 3 wiring in one place (research R2): the derived back stack, the entry decorators (saveable state and
 * an entry-scoped ViewModel store for later destinations), the scene strategies (the supporting pane when the layout
 * allows it, then the single-pane fallback) and the fade of spec FR-008, which replaces the platform defaults so both
 * platforms behave the same and snaps under reduce motion. A scene change that keeps the same top entry (a layout
 * switch) is instant: the frame changes form in one step and the content is not re-animated (spec edge cases).
 * Feature 009 adds the second key, [TrackSettingsKey], the track settings entry above the Timeline.
 */
@Composable
internal fun TembraNavDisplay(
    backStack: List<NavKey>,
    layoutMode: LayoutMode,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenTrackSettings: (TrackId) -> Unit = {},
) {
    val motion = TembraTheme.motion
    val instant = ContentTransform(EnterTransition.None, ExitTransition.None)
    val fade = if (motion.reduceMotion) {
        instant
    } else {
        fadeIn(tween(motion.effectiveDuration, easing = motion.standardEasing)) togetherWith
            fadeOut(tween(motion.effectiveDuration, easing = motion.standardEasing))
    }
    val transition: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
        if (initialState.entries.lastOrNull()?.contentKey == targetState.entries.lastOrNull()?.contentKey) instant else fade
    }
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = onBack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        sceneStrategies = listOf(
            SupportingPaneSceneStrategy(enabled = layoutMode == LayoutMode.RailWithPanel),
            SinglePaneSceneStrategy(),
        ),
        transitionSpec = transition,
        popTransitionSpec = transition,
        predictivePopTransitionSpec = { transition() },
        entryProvider = entryProvider {
            entry<DestinationKey>(
                metadata = { key -> if (key.destination.hasSupportingPane) supportingPane(SupportingPane.Library) else emptyMap() },
            ) { key ->
                DestinationContent(key.destination, onOpenTrackSettings)
            }
            entry<TrackSettingsKey> { key ->
                TrackSettingsScreen(trackId = key.trackId, onBack = onBack)
            }
        },
    )
}
