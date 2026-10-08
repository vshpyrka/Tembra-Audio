package audio.tembra.ui.perf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.File

@Composable
internal actual fun rememberEngineHealthWriter(): EngineHealthWriter {
    val dir = LocalContext.current.filesDir
    return remember(dir) {
        EngineHealthWriter { json ->
            val file = File(dir, ENGINE_HEALTH_FILE)
            file.writeText(json)
            file.absolutePath
        }
    }
}
