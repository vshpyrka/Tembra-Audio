package audio.tembra.ui.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Saves the pending edits the moment the app leaves the foreground (spec FR-046): an `ON_STOP` of the host's lifecycle
 * flushes the autosave at once. Playback itself is never touched by lifecycle events (research R6). `ON_RESUME`
 * re-reads the microphone permission (feature 010, FR-020) so a grant made in the settings is noticed.
 */
@Composable
fun AppLifecycleObserver(session: ProjectSession) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, session) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> session.flushSave()
                Lifecycle.Event.ON_RESUME -> session.refreshPermission()
                Lifecycle.Event.ON_CREATE, Lifecycle.Event.ON_START, Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_DESTROY, Lifecycle.Event.ON_ANY -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
