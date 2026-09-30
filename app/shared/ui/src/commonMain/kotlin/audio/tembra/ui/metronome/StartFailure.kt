package audio.tembra.ui.metronome

/**
 * Why a start failed (data-model.md §7): the platform refused audio focus (Android, reported by the engine start), the
 * background host refused to keep the process alive, or the engine could not open the output.
 */
enum class StartFailure { FocusDenied, HostRefused, EngineError }
