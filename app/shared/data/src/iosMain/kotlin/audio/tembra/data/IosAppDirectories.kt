package audio.tembra.data

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathDirectory
import platform.Foundation.NSUserDomainMask

class IosAppDirectories : AppDirectories {
    override val documents: String = directory(NSDocumentDirectory)
    override val cache: String = directory(NSCachesDirectory)
    override val preferences: String = createDirectory(directory(NSApplicationSupportDirectory) + "/datastore")

    @OptIn(ExperimentalForeignApi::class)
    private fun directory(kind: NSSearchPathDirectory): String {
        val url = NSFileManager.defaultManager.URLForDirectory(
            directory = kind,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )
        return url?.path ?: error("Cannot resolve iOS directory $kind")
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun createDirectory(path: String): String {
        NSFileManager.defaultManager.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)
        return path
    }
}
