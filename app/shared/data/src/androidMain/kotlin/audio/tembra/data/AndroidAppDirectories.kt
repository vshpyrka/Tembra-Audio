package audio.tembra.data

import android.content.Context
import java.io.File

class AndroidAppDirectories(context: Context) : AppDirectories {
    override val documents: String = context.filesDir.absolutePath
    override val cache: String = context.cacheDir.absolutePath
    override val preferences: String = File(context.filesDir, "datastore").apply { mkdirs() }.absolutePath
}
