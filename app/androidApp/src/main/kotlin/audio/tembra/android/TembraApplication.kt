package audio.tembra.android

import android.app.Application
import dev.zacsweers.metro.createGraph

class TembraApplication : Application() {
    val appGraph: AndroidAppGraph by lazy { createGraph<AndroidAppGraph>() }
}
