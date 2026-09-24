package audio.tembra.engine

import audio.tembra.core.version.SemanticVersion

/** Test double for [AudioEngine]: never touches native code, records calls. */
class FakeAudioEngine(
    private val descriptor: EngineDescriptor = EngineDescriptor(
        name = "Tembra Engine",
        version = SemanticVersion(0, 0, 0),
        compiledLibraries = emptyList(),
        rawDescription = "Tembra Engine 0.0.0 ()",
    ),
) : AudioEngine {
    var describeCalls: Int = 0
        private set

    override fun describe(): EngineDescriptor {
        describeCalls++
        return descriptor
    }
}
