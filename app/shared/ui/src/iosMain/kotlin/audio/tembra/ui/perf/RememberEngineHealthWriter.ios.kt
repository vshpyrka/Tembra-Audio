package audio.tembra.ui.perf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.writeToFile

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun rememberEngineHealthWriter(): EngineHealthWriter = remember {
    EngineHealthWriter { json ->
        val documents = NSFileManager.defaultManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask).firstOrNull()
        val path = (documents as? platform.Foundation.NSURL)?.path?.let { "$it/$ENGINE_HEALTH_FILE" } ?: ENGINE_HEALTH_FILE
        @Suppress("CAST_NEVER_SUCCEEDS")
        (json as NSString).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
        path
    }
}
