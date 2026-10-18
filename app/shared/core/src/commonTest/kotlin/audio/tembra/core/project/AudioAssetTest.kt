package audio.tembra.core.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The immutable facts of an audio copy (data-model.md section 1; spec FR-049). */
class AudioAssetTest {

    @Test
    fun durationIsFramesOverRate() {
        assertEquals(32.0, ProjectFixtures.stereoAsset.durationSeconds)
        assertEquals(10.0, ProjectFixtures.monoAsset.durationSeconds)
    }

    @Test
    fun theStoredFileNameUsesTheContentIdAndTheFormatExtension() {
        assertEquals("a".repeat(64) + ".wav", ProjectFixtures.stereoAsset.storedFileName(ProjectFixtures.stereoAssetId))
        assertEquals("b".repeat(64) + ".flac", ProjectFixtures.monoAsset.storedFileName(ProjectFixtures.monoAssetId))
    }

    @Test
    fun anUnknownFormatMakesTheAssetUnavailable() {
        val unknown = ProjectFixtures.stereoAsset.copy(format = null)
        assertFalse(unknown.isAvailable)
        assertNull(unknown.storedFileName(ProjectFixtures.stereoAssetId))
        assertTrue(ProjectFixtures.stereoAsset.isAvailable)
    }
}
