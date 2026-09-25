package audio.tembra.ui.theme

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The six navigation glyphs are bundled, normalised for the Compose resource loader and licensed (spec FR-016,
 * research R6). Runs on the JVM host because it reads the repository files.
 */
class IconAssetsTest {

    private val resources = File(System.getProperty("tembra.repoRoot"), "app/shared/ui/src/commonMain/composeResources")
    private val drawable = File(resources, "drawable")
    private val glyphFiles = listOf(
        "ic_home_24.xml", "ic_graphic_eq_24.xml", "ic_music_note_24.xml",
        "ic_av_timer_24.xml", "ic_folder_24.xml", "ic_person_24.xml",
    )

    /** The header comment names the source commit; the vector body must not reference any platform resource. */
    private fun bodyWithoutHeader(text: String): String = text.lines().filterNot { it.trimStart().startsWith("<!--") }.joinToString("\n")

    @Test
    fun everyNavigationGlyphIsBundled() {
        for (name in glyphFiles) {
            assertTrue(File(drawable, name).isFile, "$name is missing from composeResources/drawable")
        }
    }

    @Test
    fun everyGlyphIsA24dpVectorFromThePinnedCommit() {
        for (name in glyphFiles) {
            val text = File(drawable, name).readText()
            assertTrue(text.contains("android:width=\"24dp\""), "$name is not a 24 dp vector")
            assertTrue(text.contains(PINNED_COMMIT), "$name header does not name commit $PINNED_COMMIT")
        }
    }

    @Test
    fun noGlyphReferencesAPlatformResourceOrThemeAttribute() {
        for (name in glyphFiles) {
            val body = bodyWithoutHeader(File(drawable, name).readText())
            assertFalse(body.contains("@"), "$name still references a platform resource")
            assertFalse(body.contains("?attr"), "$name still references a theme attribute")
        }
    }

    @Test
    fun theApacheLicenceIsBundled() {
        val licence = File(resources, "files/LICENSE-MaterialSymbols.txt").readText().trimStart()
        assertTrue(licence.startsWith("Apache License"), "LICENSE-MaterialSymbols.txt does not start with the Apache header")
    }

    private companion object {
        const val PINNED_COMMIT = "27e9ef1dbeedc13d682fece4a58e1eda4cb0961a"
    }
}
