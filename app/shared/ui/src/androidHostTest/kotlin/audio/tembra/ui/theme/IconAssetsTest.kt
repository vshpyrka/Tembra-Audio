package audio.tembra.ui.theme

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Every bundled glyph is normalised for the Compose resource loader and licensed (spec FR-016 of feature 002,
 * FR-026 of feature 003, research R6 / R14; FR-028 of feature 004): the Material Symbols files name the pinned commit, the two design
 * glyphs name the Stitch screen they were converted from. Runs on the JVM host because it reads repository files.
 */
class IconAssetsTest {

    private val resources = File(System.getProperty("tembra.repoRoot"), "app/shared/ui/src/commonMain/composeResources")
    private val drawable = File(resources, "drawable")
    /** Feature 004 (metronome); `ic_graphic_eq_24` and `ic_music_note_24` were already bundled for navigation. */
    private val metronomeGlyphs = listOf(
        "ic_remove_24.xml", "ic_add_24.xml", "ic_touch_app_24.xml", "ic_music_note_24.xml", "ic_graphic_eq_24.xml",
        "ic_radio_button_checked_24.xml", "ic_notifications_24.xml", "ic_sensors_24.xml", "ic_tune_24.xml", "ic_check_24.xml",
        "ic_play_arrow_fill_24.xml", "ic_pause_fill_24.xml", "ic_swap_horiz_24.xml",
    )

    /** Feature 009 (timeline arranger): toolbar, transport, library and instrument-chip glyphs; `mic` and `expand_more` were already bundled. */
    private val timelineGlyphs = listOf(
        "ic_content_cut_24.xml", "ic_undo_24.xml", "ic_redo_24.xml", "ic_skip_previous_24.xml", "ic_skip_next_24.xml",
        "ic_fast_rewind_24.xml", "ic_fast_forward_24.xml", "ic_grid_on_24.xml", "ic_zoom_in_24.xml", "ic_zoom_out_24.xml",
        "ic_search_24.xml", "ic_sort_24.xml", "ic_content_copy_24.xml", "ic_library_music_24.xml", "ic_expand_more_24.xml",
        "ic_expand_less_24.xml", "ic_more_vert_24.xml", "ic_audio_file_24.xml", "ic_playlist_add_24.xml", "ic_mic_24.xml", "ic_piano_24.xml",
    )

    /** The filled variants (FILL 1) the play button uses, fetched with `--fill`. */
    private val filledGlyphs = listOf("ic_play_arrow_fill_24.xml", "ic_pause_fill_24.xml")
    private val materialGlyphs = (
        listOf(
            "ic_graphic_eq_24.xml", "ic_music_note_24.xml",
            "ic_folder_24.xml", "ic_person_24.xml",
            "ic_mic_24.xml", "ic_mic_off_24.xml", "ic_hearing_24.xml", "ic_play_arrow_24.xml", "ic_pause_24.xml",
            "ic_volume_up_24.xml", "ic_expand_more_24.xml", "ic_arrow_left_24.xml", "ic_arrow_right_24.xml", "ic_settings_24.xml",
        ) + metronomeGlyphs + timelineGlyphs
        ).distinct()

    /** Feature 004 D1: the subdivision note figures, hand-authored by `tools/make-note-glyphs.py`. */
    private val noteGlyphs = listOf(
        "ic_note_quarter_24.xml", "ic_note_eighth_pair_24.xml", "ic_note_sixteenth_quad_24.xml", "ic_note_eighth_triplet_24.xml",
        "ic_note_eighth_24.xml", "ic_note_sixteenth_pair_24.xml", "ic_note_thirty_second_quad_24.xml", "ic_note_sixteenth_triplet_24.xml",
    )
    /** The navigation's Tuner and Metronome glyphs (2026-10-17), hand-drawn by `tools/make-nav-glyphs.py`. */
    private val navGlyphs = listOf("ic_tuning_fork_24.xml", "ic_metronome_24.xml")
    private val glyphFiles = materialGlyphs + noteGlyphs + navGlyphs

    /** The header comment names the source; the vector body must not reference any platform resource. */
    private fun bodyWithoutHeader(text: String): String = text.lines().filterNot { it.trimStart().startsWith("<!--") }.joinToString("\n")

    @Test
    fun noteGlyphsNameTheirGenerator() {
        for (name in noteGlyphs) {
            val header = File(drawable, name).readText().lineSequence().first { it.trimStart().startsWith("<!--") }
            assertTrue(header.startsWith("<!-- Tembra note glyph"), "$name header is not the note glyph header")
            assertTrue(header.contains("tools/make-note-glyphs.py"), "$name header does not name tools/make-note-glyphs.py")
            assertTrue(File(drawable, name).readText().contains("android:fillColor=\"#FFFFFFFF\""), "$name has no literal white fill")
        }
    }

    @Test
    fun navGlyphsNameTheirGeneratorAndAreWhiteStrokes() {
        for (name in navGlyphs) {
            val text = File(drawable, name).readText()
            val header = text.lineSequence().first { it.trimStart().startsWith("<!--") }
            assertTrue(header.startsWith("<!-- Tembra navigation glyph"), "$name header is not the navigation glyph header")
            assertTrue(header.contains("tools/make-nav-glyphs.py"), "$name header does not name tools/make-nav-glyphs.py")
            assertTrue(text.contains("android:strokeColor=\"#FFFFFFFF\""), "$name has no literal white stroke")
            assertFalse(text.contains("android:fillColor"), "$name fills a path; its lines are strokes")
        }
    }

    @Test
    fun everyGlyphIsBundled() {
        for (name in glyphFiles) {
            assertTrue(File(drawable, name).isFile, "$name is missing from composeResources/drawable")
        }
    }

    @Test
    fun everyGlyphIsA24dpVector() {
        for (name in glyphFiles) {
            val text = File(drawable, name).readText()
            assertTrue(text.contains("android:width=\"24dp\""), "$name is not a 24 dp vector")
            assertTrue(text.contains("android:height=\"24dp\""), "$name is not a 24 dp vector")
        }
    }

    @Test
    fun materialGlyphsNameThePinnedCommit() {
        for (name in materialGlyphs) {
            val header = File(drawable, name).readText().lineSequence().first { it.trimStart().startsWith("<!--") }
            assertTrue(header.startsWith("<!-- Material Symbols Outlined"), "$name header is not the Material Symbols header")
            assertTrue(header.contains(PINNED_COMMIT), "$name header does not name commit $PINNED_COMMIT")
        }
    }

    @Test
    fun filledGlyphsSayFilledAndDifferFromTheOutlinedOnes() {
        for (name in filledGlyphs) {
            val text = File(drawable, name).readText()
            assertTrue(text.lineSequence().first().contains("(filled)"), "$name header does not say it is the filled variant")
            val outlined = File(drawable, name.replace("_fill_24", "_24")).readText()
            assertFalse(bodyWithoutHeader(text) == bodyWithoutHeader(outlined), "$name has the outlined glyph's path")
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
