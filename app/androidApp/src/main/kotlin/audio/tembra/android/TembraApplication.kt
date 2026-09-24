package audio.tembra.android

import android.app.Application
import dev.zacsweers.metro.createGraphFactory

class TembraApplication : Application() {
    val appGraph: AndroidAppGraph by lazy { createGraphFactory<AndroidAppGraph.Factory>().create(this) }
}
