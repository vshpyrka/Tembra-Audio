package audio.tembra.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import audio.tembra.ui.App
import audio.tembra.ui.devtools.DeveloperViewRequest
import audio.tembra.ui.metronome.MetronomeDebugRequest
import audio.tembra.ui.tuner.TunerNoiseGateRequest
import audio.tembra.ui.tuner.TunerTestSignalRequest

class MainActivity : ComponentActivity() {
    private lateinit var appGraph: AndroidAppGraph

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        appGraph = (application as TembraApplication).appGraph
        // The microphone prompt is launched through this activity (feature 003, research R6); registered before setContent.
        val host = appGraph.microphonePermissionHost
        val launcher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> host.onResult(granted) }
        host.attach(this, launcher)
        // The custom click sound is picked through this activity's document picker (feature 004, contracts/click-sounds.md).
        val pickerHost = appGraph.soundFilePickerHost
        pickerHost.attach(registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pickerHost.onResult(uri) })
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
        setContent {
            App(appGraph = appGraph, developerView = request, tunerTestSignal = testSignal, tunerNoiseGate = noiseGate, metronomeDebug = metronomeDebug)
        }
    }

    override fun onDestroy() {
        appGraph.microphonePermissionHost.detach()
        appGraph.soundFilePickerHost.detach()
        super.onDestroy()
    }
}
