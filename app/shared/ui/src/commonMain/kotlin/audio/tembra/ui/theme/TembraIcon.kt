package audio.tembra.ui.theme

import audio.tembra.ui.resources.Res
import audio.tembra.ui.resources.ic_arrow_left_24
import audio.tembra.ui.resources.ic_arrow_right_24
import audio.tembra.ui.resources.ic_av_timer_24
import audio.tembra.ui.resources.ic_cello_24
import audio.tembra.ui.resources.ic_expand_more_24
import audio.tembra.ui.resources.ic_folder_24
import audio.tembra.ui.resources.ic_graphic_eq_24
import audio.tembra.ui.resources.ic_guitar_24
import audio.tembra.ui.resources.ic_hearing_24
import audio.tembra.ui.resources.ic_home_24
import audio.tembra.ui.resources.ic_mic_24
import audio.tembra.ui.resources.ic_mic_off_24
import audio.tembra.ui.resources.ic_music_note_24
import audio.tembra.ui.resources.ic_pause_24
import audio.tembra.ui.resources.ic_person_24
import audio.tembra.ui.resources.ic_play_arrow_24
import audio.tembra.ui.resources.ic_settings_24
import audio.tembra.ui.resources.ic_volume_up_24
import org.jetbrains.compose.resources.DrawableResource

/**
 * Bundled glyphs (24 dp), rendered through [TembraIcons.vector]. Most are Material Symbols Outlined (weight 400,
 * grade 0, unfilled) copied from google/material-design-icons at commit 27e9ef1dbeedc13d682fece4a58e1eda4cb0961a
 * (Apache-2.0) and normalised by `tools/fetch-material-symbols.sh` (research R6 of feature 002); [Guitar] and
 * [Cello] are the project's own design artwork, hand-converted from the Stitch instrument selector (research R14 of
 * feature 003). Screens use these names only (spec FR-016, FR-026); to add a glyph run the script and add a value.
 */
enum class TembraIcon(internal val resource: DrawableResource) {
    Home(Res.drawable.ic_home_24),
    Timeline(Res.drawable.ic_graphic_eq_24),
    Tuner(Res.drawable.ic_music_note_24),
    Metronome(Res.drawable.ic_av_timer_24),
    Projects(Res.drawable.ic_folder_24),
    Profile(Res.drawable.ic_person_24),
    Mic(Res.drawable.ic_mic_24),
    MicOff(Res.drawable.ic_mic_off_24),
    Hearing(Res.drawable.ic_hearing_24),
    PlayArrow(Res.drawable.ic_play_arrow_24),
    Pause(Res.drawable.ic_pause_24),
    VolumeUp(Res.drawable.ic_volume_up_24),
    ExpandMore(Res.drawable.ic_expand_more_24),
    ArrowLeft(Res.drawable.ic_arrow_left_24),
    ArrowRight(Res.drawable.ic_arrow_right_24),
    Settings(Res.drawable.ic_settings_24),
    Guitar(Res.drawable.ic_guitar_24),
    Cello(Res.drawable.ic_cello_24),
}
