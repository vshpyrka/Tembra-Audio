package audio.tembra.ui.theme

import audio.tembra.ui.resources.Res
import audio.tembra.ui.resources.ic_av_timer_24
import audio.tembra.ui.resources.ic_folder_24
import audio.tembra.ui.resources.ic_graphic_eq_24
import audio.tembra.ui.resources.ic_home_24
import audio.tembra.ui.resources.ic_music_note_24
import audio.tembra.ui.resources.ic_person_24
import org.jetbrains.compose.resources.DrawableResource

/**
 * Bundled Material Symbols Outlined glyphs (24 dp, weight 400, grade 0, unfilled) copied from
 * google/material-design-icons at commit 27e9ef1dbeedc13d682fece4a58e1eda4cb0961a (Apache-2.0) and normalised by
 * `tools/fetch-material-symbols.sh` (research R6). Screens use these names only (spec FR-016); to add a glyph run the
 * script and add a value here. Rendered through [TembraIcons.vector].
 */
enum class TembraIcon(internal val resource: DrawableResource) {
    Home(Res.drawable.ic_home_24),
    Timeline(Res.drawable.ic_graphic_eq_24),
    Tuner(Res.drawable.ic_music_note_24),
    Metronome(Res.drawable.ic_av_timer_24),
    Projects(Res.drawable.ic_folder_24),
    Profile(Res.drawable.ic_person_24),
}
