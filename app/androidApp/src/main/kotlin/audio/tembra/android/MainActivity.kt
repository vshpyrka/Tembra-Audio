package audio.tembra.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import audio.tembra.ui.App
import audio.tembra.ui.devtools.DeveloperViewRequest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val appGraph = (application as TembraApplication).appGraph
        // Debug builds accept the "tembra.developer.view" extra (spec FR-022); the shrinker removes this branch from release.
        val request = if (BuildConfig.DEBUG) {
            DeveloperViewRequest.parse(intent.getStringExtra("tembra.developer.view"))
        } else {
            DeveloperViewRequest(null)
        }
        setContent { App(appGraph, request) }
    }
}
