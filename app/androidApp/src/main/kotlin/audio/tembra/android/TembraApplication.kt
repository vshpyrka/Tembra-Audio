package audio.tembra.android

import android.app.Application
import android.os.StrictMode
import dev.zacsweers.metro.createGraphFactory

class TembraApplication : Application() {
    val appGraph: AndroidAppGraph by lazy { createGraphFactory<AndroidAppGraph.Factory>().create(this) }

    override fun onCreate() {
        super.onCreate()
        // The main-thread audit's debug guard (feature 012, contracts/skills-and-audit.md §C part 4): debug builds log
        // every main-thread disk, network and slow call (`adb logcat -s StrictMode`); release builds install nothing.
        StrictModePolicy.install(debug = BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
        }
    }
}
