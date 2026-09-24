package audio.tembra.ui

import audio.tembra.ui.di.AppGraph
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph

/** iOS application graph: created once in [MainViewController]. */
@DependencyGraph(AppScope::class)
interface IosAppGraph : AppGraph
