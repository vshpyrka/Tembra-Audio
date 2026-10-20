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
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import audio.tembra.ui.theme.TembraTheme

/**
 * All Navigation 3 wiring in one place (research R2): the derived back stack, the entry decorators (saveable state and
 * an entry-scoped ViewModel store for later destinations), the scene strategies (the supporting pane when the layout
 * allows it, then the single-pane fallback) and the fade of spec FR-008, which replaces the platform defaults so both
 * platforms behave the same and snaps under reduce motion. A scene change that keeps the same top entry (a layout
 * switch) is instant: the frame changes form in one step and the content is not re-animated (spec edge cases).
 */
@Composable
internal fun TembraNavDisplay(
    backStack: List<Destination>,
    layoutMode: LayoutMode,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = TembraTheme.motion
    val instant = ContentTransform(EnterTransition.None, ExitTransition.None)
    val fade = if (motion.reduceMotion) {
        instant
    } else {
        fadeIn(tween(motion.effectiveDuration, easing = motion.standardEasing)) togetherWith
            fadeOut(tween(motion.effectiveDuration, easing = motion.standardEasing))
    }
    // A screen that prepares itself over a few frames fades itself in once it is ready (FadesItselfInKey), with no
    // offscreen layer; the navigation fades out only the screen it leaves.
    val fadeOutOnly = if (motion.reduceMotion) {
        instant
    } else {
        EnterTransition.None togetherWith fadeOut(tween(motion.effectiveDuration, easing = motion.standardEasing))
    }
    val transition: AnimatedContentTransitionScope<Scene<DestinationKey>>.() -> ContentTransform = {
        val target = targetState.entries.lastOrNull()
        when {
            initialState.entries.lastOrNull()?.contentKey == target?.contentKey -> instant
            target?.metadata?.get(FadesItselfInKey) == true -> fadeOutOnly
            else -> fade
        }
    }
    NavDisplay(
        backStack = backStack.map(::DestinationKey),
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
                metadata = { key ->
                    val pane = if (key.destination.hasSupportingPane) supportingPane(SupportingPane.Library) else emptyMap()
                    pane + if (key.destination in PreparesItself) fadesItselfIn() else emptyMap()
                },
            ) { key ->
                DestinationContent(key.destination)
            }
        },
    )
}

/** The destinations whose screens prepare themselves before they show (PreparedLayout) and fade themselves in. */
private val PreparesItself = setOf(Destination.Metronome)
