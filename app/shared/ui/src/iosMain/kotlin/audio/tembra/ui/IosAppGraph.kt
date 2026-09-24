package audio.tembra.ui

import audio.tembra.data.AppDirectories
import audio.tembra.data.IosAppDirectories
import audio.tembra.ui.di.AppGraph
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/** iOS application graph: created once in [MainViewController]. */
@DependencyGraph(AppScope::class)
interface IosAppGraph : AppGraph {
    @Provides
    @SingleIn(AppScope::class)
    fun provideAppDirectories(): AppDirectories = IosAppDirectories()
}
