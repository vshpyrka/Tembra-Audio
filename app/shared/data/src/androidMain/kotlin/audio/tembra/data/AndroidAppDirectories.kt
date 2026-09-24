package audio.tembra.data

import android.content.Context

class AndroidAppDirectories(context: Context) : AppDirectories {
    override val documents: String = context.filesDir.absolutePath
    override val cache: String = context.cacheDir.absolutePath
}
