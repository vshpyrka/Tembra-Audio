package audio.tembra.ui.metronome

/** Why a start failed (data-model.md §7): the background host refused, or the engine could not open the output. */
enum class StartFailure { HostRefused, EngineError }
