package audio.tembra.android

import audio.tembra.ui.di.AppGraph
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph

/** Android application graph: created once in [TembraApplication]. */
@DependencyGraph(AppScope::class)
interface AndroidAppGraph : AppGraph
