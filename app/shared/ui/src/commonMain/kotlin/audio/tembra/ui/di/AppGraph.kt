package audio.tembra.ui.di

import dev.zacsweers.metrox.viewmodel.ViewModelGraph

/**
 * The application dependency graph shared by both platforms (Metro, `AppScope`).
 * Platform graphs (`IosAppGraph`, `AndroidAppGraph`) extend it and are created once per process.
 */
interface AppGraph : ViewModelGraph
