package audio.tembra.android

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import audio.tembra.ui.App
import audio.tembra.ui.devtools.DeveloperViewRequest
import audio.tembra.ui.metronome.MetronomeDebugRequest
import audio.tembra.ui.projects.ProjectsSeedRequest
import audio.tembra.ui.platform.ProvideLaidOutWindowSize
import audio.tembra.ui.platform.dispatchEscapeAsBack
import audio.tembra.ui.platform.enableEdgeToEdgeOverDarkContent
import audio.tembra.ui.tuner.TunerNoiseGateRequest
import audio.tembra.ui.tuner.TunerTestSignalRequest

class MainActivity : ComponentActivity() {
    private lateinit var appGraph: AndroidAppGraph

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdgeOverDarkContent()
        super.onCreate(savedInstanceState)
        appGraph = (application as TembraApplication).appGraph
        // The microphone prompt is launched through this activity (feature 003, research R6); registered before setContent.
        val host = appGraph.microphonePermissionHost
        val launcher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> host.onResult(granted) }
        host.attach(this, launcher)
        // Audio files (the timeline's imports, the metronome's custom click sound) are picked through this activity's
        // document pickers (feature 009, contracts/project-storage.md): one document, or several in picker order.
        val pickerHost = appGraph.audioFilePickerHost
        pickerHost.attach(
            one = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pickerHost.onResult(uri) },
            many = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> pickerHost.onResult(uris) },
        )
        // The notifications prompt for the metronome's "Running" notification (Android 13+, contracts/background-playback.md).
        val notificationHost = appGraph.notificationPermissionHost
        notificationHost.attach(this, registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> notificationHost.onResult(granted) })
        // Debug builds accept the "tembra.developer.view" extra (feature 002, spec FR-022), the "tembra.tuner.testSignal"
        // extra (feature 003, spec FR-032), the "tembra.tuner.noiseGate" extra (feature 006, spec FR-023) and the
        // "tembra.metronome.syncopation" extra (feature 004, spec FR-037); the shrinker removes these branches from
        // release.
        val request = if (BuildConfig.DEBUG) {
            DeveloperViewRequest.parse(intent.getStringExtra("tembra.developer.view"))
        } else {
            DeveloperViewRequest(null)
        }
        val testSignal = if (BuildConfig.DEBUG) TunerTestSignalRequest.parse(intent.getStringExtra("tembra.tuner.testSignal")) else null
        val noiseGate = if (BuildConfig.DEBUG) TunerNoiseGateRequest.parse(intent.getStringExtra("tembra.tuner.noiseGate")) else null
        val metronomeDebug = if (BuildConfig.DEBUG) {
            MetronomeDebugRequest.parse(intent.getStringExtra("tembra.metronome.syncopation"))
        } else {
            null
        }
        // "tembra.projects.seed" fills the library for the SC-010 timing (feature 009, T121).
        val projectsSeed = if (BuildConfig.DEBUG) ProjectsSeedRequest.parse(intent.getStringExtra("tembra.projects.seed")) else null
        setContent {
            // This activity rotates in place (configChanges), which leaves Compose's window size one rotation behind on some
            // devices (API 28 and 30 seen); the wrapper hands every adaptive layout the laid-out window instead.
            ProvideLaidOutWindowSize {
                App(appGraph = appGraph, developerView = request, tunerTestSignal = testSignal, tunerNoiseGate = noiseGate, metronomeDebug = metronomeDebug, projectsSeed = projectsSeed)
            }
        }
    }

    // A hardware keyboard's Escape goes back like the back key (desktop windowing), before Compose can consume it.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean = dispatchEscapeAsBack(event) || super.dispatchKeyEvent(event)

    override fun onDestroy() {
        appGraph.microphonePermissionHost.detach()
        appGraph.audioFilePickerHost.detach()
        appGraph.notificationPermissionHost.detach()
        super.onDestroy()
    }
}
