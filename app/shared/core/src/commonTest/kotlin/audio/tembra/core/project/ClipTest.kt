package audio.tembra.core.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Clip invariants (data-model.md section 1; spec FR-027, FR-030). */
class ClipTest {

    @Test
    fun lengthMustBePositiveAndOffsetNonNegative() {
        assertFailsWith<IllegalArgumentException> { ProjectFixtures.clip(1, 0.0, length = 0) }
        assertFailsWith<IllegalArgumentException> { ProjectFixtures.clip(1, 0.0, offset = -1) }
    }

    @Test
    fun startMustNotBeNegative() {
        assertFailsWith<IllegalArgumentException> { ProjectFixtures.clip(1, -0.5) }
    }

    @Test
    fun fitsWithinAssetWhenOffsetPlusLengthIsAtMostTheFrames() {
        assertTrue(ProjectFixtures.clip(1, 0.0, offset = 0, length = 1_536_000).fitsWithin(ProjectFixtures.stereoAsset))
        assertTrue(ProjectFixtures.clip(1, 0.0, offset = 1_000, length = 1_535_000).fitsWithin(ProjectFixtures.stereoAsset))
        assertFalse(ProjectFixtures.clip(1, 0.0, offset = 1, length = 1_536_000).fitsWithin(ProjectFixtures.stereoAsset))
    }

    @Test
    fun clipTemplateNamesTheClipAfterTheFileWithoutExtension() {
        val template = ClipTemplate.forAsset(ProjectFixtures.stereoAssetId, ProjectFixtures.stereoAsset)
        assertEquals("loop", template.name)
        assertEquals(0, template.offsetFrames)
        assertEquals(1_536_000, template.lengthFrames)
        val clip = template.toClip(ClipId("c"), Beats(8.0))
        assertEquals(Clip(ClipId("c"), ProjectFixtures.stereoAssetId, Beats(8.0), 0, 1_536_000, "loop"), clip)
        assertEquals("take.01", ClipTemplate.nameOf("take.01.WAV"))
        assertEquals("noext", ClipTemplate.nameOf("noext"))
    }
}
