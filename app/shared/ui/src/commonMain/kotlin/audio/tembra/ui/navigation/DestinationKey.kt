package audio.tembra.ui.navigation

import androidx.navigation3.runtime.NavKey

/** The only Navigation 3 key: one class, so the entry provider has exactly one entry (research R2). */
data class DestinationKey(val destination: Destination) : NavKey
