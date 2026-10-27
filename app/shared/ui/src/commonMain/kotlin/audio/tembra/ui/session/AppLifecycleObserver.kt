package audio.tembra.ui.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Saves the pending edits the moment the app leaves the foreground (spec FR-046): an `ON_STOP` of the host's lifecycle
 * flushes the autosave at once. Playback itself is never touched by lifecycle events (research R6).
 */
@Composable
fun AppLifecycleObserver(session: ProjectSession) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, session) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) session.flushSave()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
