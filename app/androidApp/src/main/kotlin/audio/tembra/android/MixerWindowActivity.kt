package audio.tembra.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import audio.tembra.ui.mixer.MixerWindow
import audio.tembra.ui.platform.ProvideLaidOutWindowSize
import audio.tembra.ui.platform.enableEdgeToEdgeOverDarkContent

/**
 * The mixer in a window of its own (feature 012, spec FR-021a, research R10): a second host over the application graph's
 * session, in its own task (the manifest's `singleTask` and `taskAffinity`), opened by [AndroidWindowCapabilities]. It
 * finishes when the session has no project any more (the project's close closes both windows; the window's screen
 * reports it once the empty state has lasted a moment, so a project switch passes).
 */
class MixerWindowActivity : ComponentActivity() {
    private val appGraph: AndroidAppGraph get() = (application as TembraApplication).appGraph

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdgeOverDarkContent()
        super.onCreate(savedInstanceState)
        val graph = appGraph
        graph.androidWindowCapabilities.windowCreated(this)
        setContent {
            ProvideLaidOutWindowSize { MixerWindow(graph, onProjectGone = ::finish) }
        }
    }

    override fun onDestroy() {
        appGraph.androidWindowCapabilities.windowDestroyed(this)
        super.onDestroy()
    }
}
