package audio.tembra.engine

import audio.tembra.core.version.SemanticVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EngineDescriptorParserTest {

    @Test
    fun parsesNameVersionAndLibraries() {
        val raw = "Tembra Engine 0.1.0 (oboe 1.11.0; miniaudio 0.11.25; dr_wav 0.14.5; signalsmith-stretch 1.1.0)"
        val descriptor = EngineDescriptorParser.parse(raw)
        assertEquals("Tembra Engine", descriptor.name)
        assertEquals(SemanticVersion(0, 1, 0), descriptor.version)
        assertEquals(listOf("oboe 1.11.0", "miniaudio 0.11.25", "dr_wav 0.14.5", "signalsmith-stretch 1.1.0"), descriptor.compiledLibraries)
        assertEquals(raw, descriptor.rawDescription)
    }

    @Test
    fun emptyLibraryListIsAllowed() {
        val descriptor = EngineDescriptorParser.parse("Tembra Engine 0.1.0 ()")
        assertEquals(emptyList(), descriptor.compiledLibraries)
        assertEquals(SemanticVersion(0, 1, 0), descriptor.version)
    }

    @Test
    fun rejectsMalformedDescriptions() {
        for (bad in listOf("", "Tembra Engine 0.1.0", "Tembra Engine 0.1.0 (miniaudio 0.11.25", "Tembra Engine v1 ()", "0.1.0 ()")) {
            assertFailsWith<IllegalArgumentException>("parse(\"$bad\") must throw") { EngineDescriptorParser.parse(bad) }
        }
    }
}
