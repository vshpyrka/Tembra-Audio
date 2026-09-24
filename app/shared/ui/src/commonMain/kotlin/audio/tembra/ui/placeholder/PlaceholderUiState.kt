package audio.tembra.ui.placeholder

import audio.tembra.engine.EngineDescriptor

data class PlaceholderUiState(
    val pressCount: Int = 0,
    val engine: EngineDescriptor,
) {
    init {
        require(pressCount >= 0) { "pressCount must not be negative: $pressCount" }
    }
}
