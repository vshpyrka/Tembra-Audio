package audio.tembra.ui.session

import audio.tembra.core.project.AudioAssetId
import audio.tembra.engine.FakeAudioEngine
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The registry's further sources per asset (feature 012: clips of one asset sounding at once; contracts/project-session.md). */
class ClipSourceRegistryTest {

    private val engine = FakeAudioEngine()
    private val registry = ClipSourceRegistry(engine)
    private val asset = AudioAssetId("a".repeat(64))

    @Test
    fun ensureOpensFurtherSourcesUpToTheCountAndCloseClosesThemAll() = runTest {
        assertTrue(registry.open(asset, "/a.wav"))
        assertEquals(1, registry.handles.getValue(asset).size)
        assertEquals(2, registry.ensure(asset, 3))
        assertEquals(3, registry.handles.getValue(asset).size)
        assertEquals(3, registry.handles.getValue(asset).toSet().size, "three distinct handles")
        assertEquals(3, engine.openClipSources.size)
        assertEquals(0, registry.ensure(asset, 2), "enough already open")
        assertFalse(registry.lacks(mapOf(asset to 3)))
        assertTrue(registry.lacks(mapOf(asset to 4)))
        registry.close(asset)
        assertTrue(engine.openClipSources.isEmpty())
        assertTrue(registry.handles.isEmpty())
    }

    @Test
    fun ensureOnAnAssetThatIsNotOpenOpensNothingAndIsNotLacking() = runTest {
        assertEquals(0, registry.ensure(asset, 3))
        assertTrue(engine.openClipSources.isEmpty())
        assertFalse(registry.lacks(mapOf(asset to 3)))
    }

    @Test
    fun closeAllClosesEverySourceOfEveryAsset() = runTest {
        val other = AudioAssetId("b".repeat(64))
        registry.open(asset, "/a.wav")
        registry.open(other, "/b.wav")
        registry.ensure(asset, 2)
        assertEquals(3, engine.openClipSources.size)
        registry.closeAll()
        assertTrue(engine.openClipSources.isEmpty())
        assertTrue(registry.assets.isEmpty())
    }
}
