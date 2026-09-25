package audio.tembra.ui.devtools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The launch-argument parser of the developer view (spec FR-022, contracts/developer-view.md). */
class DeveloperViewRequestTest {

    @Test
    fun parsesTheTokenPreviewArgument() {
        assertEquals(DeveloperView.TokenPreview, DeveloperViewRequest.parse("token-preview").view)
    }

    @Test
    fun noArgumentMeansNoView() {
        assertNull(DeveloperViewRequest.parse(null).view)
    }

    @Test
    fun unknownArgumentsMapToNoView() {
        assertNull(DeveloperViewRequest.parse("nonsense").view)
    }
}
