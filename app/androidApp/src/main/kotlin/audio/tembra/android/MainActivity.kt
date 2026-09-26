package audio.tembra.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import audio.tembra.ui.App
import audio.tembra.ui.devtools.DeveloperViewRequest
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
        // Debug builds accept the "tembra.developer.view" extra (feature 002, spec FR-022) and the "tembra.tuner.testSignal"
        // extra (feature 003, spec FR-032); the shrinker removes this branch from release.
        val request = if (BuildConfig.DEBUG) {
            DeveloperViewRequest.parse(intent.getStringExtra("tembra.developer.view"))
        } else {
            DeveloperViewRequest(null)
        }
        val testSignal = if (BuildConfig.DEBUG) TunerTestSignalRequest.parse(intent.getStringExtra("tembra.tuner.testSignal")) else null
        setContent { App(appGraph, request, testSignal) }
    }

    override fun onDestroy() {
        appGraph.microphonePermissionHost.detach()
        super.onDestroy()
    }
}
