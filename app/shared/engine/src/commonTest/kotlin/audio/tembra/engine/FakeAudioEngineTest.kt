package audio.tembra.engine

import audio.tembra.core.version.SemanticVersion
import kotlin.test.Test
import kotlin.test.assertEquals

class FakeAudioEngineTest {

    @Test
    fun defaultDescriptorIsAnEmptyZeroVersionEngine() {
        val descriptor = FakeAudioEngine().describe()
        assertEquals("Tembra Engine", descriptor.name)
        assertEquals(SemanticVersion(0, 0, 0), descriptor.version)
        assertEquals(emptyList(), descriptor.compiledLibraries)
        assertEquals("Tembra Engine 0.0.0 ()", descriptor.rawDescription)
    }

    @Test
    fun returnsTheConfiguredDescriptorAndCountsCalls() {
        val configured = EngineDescriptor(
            name = "Tembra Engine",
            version = SemanticVersion(9, 8, 7),
            compiledLibraries = listOf("miniaudio 0.11.25"),
            rawDescription = "Tembra Engine 9.8.7 (miniaudio 0.11.25)",
        )
        val engine = FakeAudioEngine(configured)
        assertEquals(0, engine.describeCalls)
        assertEquals(configured, engine.describe())
        assertEquals(configured, engine.describe())
        assertEquals(2, engine.describeCalls)
    }
}
