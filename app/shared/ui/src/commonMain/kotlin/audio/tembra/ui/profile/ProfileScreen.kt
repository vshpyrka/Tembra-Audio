package audio.tembra.ui.profile

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import audio.tembra.ui.designsystem.PlaceholderDestination
import audio.tembra.ui.navigation.Destination

/**
 * The Profile destination; a placeholder until its feature lands (spec FR-013). Its root takes keyboard focus when it
 * appears, like every destination's (feature 011, spec FR-024).
 */
@Composable
fun ProfileScreen(modifier: Modifier = Modifier) {
    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(rootFocus) { rootFocus.requestFocus() }
    PlaceholderDestination(Destination.Profile, modifier.focusRequester(rootFocus).focusable())
}
