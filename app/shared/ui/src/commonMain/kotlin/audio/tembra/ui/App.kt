package audio.tembra.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import audio.tembra.ui.di.AppGraph
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory

/** Root composable hosted by both platform shells. */
@Composable
fun App(appGraph: AppGraph) {
    CompositionLocalProvider(LocalMetroViewModelFactory provides appGraph.metroViewModelFactory) {
        MaterialTheme {
            Scaffold { innerPadding ->
                Text(
                    text = "Tembra Audio",
                    modifier = Modifier.padding(innerPadding).testTag("placeholder.title"),
                )
            }
        }
    }
}
