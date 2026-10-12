package audio.tembra.ui.theme

import audio.tembra.ui.resources.Res
import audio.tembra.ui.resources.ic_add_24
import audio.tembra.ui.resources.ic_arrow_left_24
import audio.tembra.ui.resources.ic_arrow_right_24
import audio.tembra.ui.resources.ic_av_timer_24
import audio.tembra.ui.resources.ic_check_24
import audio.tembra.ui.resources.ic_expand_more_24
import audio.tembra.ui.resources.ic_folder_24
import audio.tembra.ui.resources.ic_graphic_eq_24
import audio.tembra.ui.resources.ic_hearing_24
import audio.tembra.ui.resources.ic_home_24
import audio.tembra.ui.resources.ic_mic_24
import audio.tembra.ui.resources.ic_mic_off_24
import audio.tembra.ui.resources.ic_music_note_24
import audio.tembra.ui.resources.ic_note_eighth_24
import audio.tembra.ui.resources.ic_note_eighth_pair_24
import audio.tembra.ui.resources.ic_note_eighth_triplet_24
import audio.tembra.ui.resources.ic_note_quarter_24
import audio.tembra.ui.resources.ic_note_sixteenth_pair_24
import audio.tembra.ui.resources.ic_note_sixteenth_quad_24
import audio.tembra.ui.resources.ic_note_sixteenth_triplet_24
import audio.tembra.ui.resources.ic_note_thirty_second_quad_24
import audio.tembra.ui.resources.ic_notifications_24
import audio.tembra.ui.resources.ic_pause_24
import audio.tembra.ui.resources.ic_pause_fill_24
import audio.tembra.ui.resources.ic_person_24
import audio.tembra.ui.resources.ic_play_arrow_24
import audio.tembra.ui.resources.ic_play_arrow_fill_24
import audio.tembra.ui.resources.ic_radio_button_checked_24
import audio.tembra.ui.resources.ic_remove_24
import audio.tembra.ui.resources.ic_sensors_24
import audio.tembra.ui.resources.ic_settings_24
import audio.tembra.ui.resources.ic_swap_horiz_24
import audio.tembra.ui.resources.ic_touch_app_24
import audio.tembra.ui.resources.ic_tune_24
import audio.tembra.ui.resources.ic_volume_up_24
import org.jetbrains.compose.resources.DrawableResource

/**
 * Bundled glyphs (24 dp), rendered through [TembraIcons.vector]. Most are Material Symbols Outlined (weight 400,
 * grade 0, unfilled) copied from google/material-design-icons at commit 27e9ef1dbeedc13d682fece4a58e1eda4cb0961a
 * (Apache-2.0) and normalised by `tools/fetch-material-symbols.sh` (research R6 of feature 002). The `Note*` figures
 * are hand-authored by `tools/make-note-glyphs.py`, not fetched Material Symbols.
 * Screens use these names only (spec FR-016, FR-026); to add a glyph run the script and add a value.
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
    // Metronome glyphs (feature 004, research R10): steppers, tap tempo, subdivisions and the five click sounds.
    Remove(Res.drawable.ic_remove_24),
    Add(Res.drawable.ic_add_24),
    TouchApp(Res.drawable.ic_touch_app_24),
    MusicNote(Res.drawable.ic_music_note_24),
    GraphicEq(Res.drawable.ic_graphic_eq_24),
    RadioButtonChecked(Res.drawable.ic_radio_button_checked_24),
    Notifications(Res.drawable.ic_notifications_24),
    Sensors(Res.drawable.ic_sensors_24),
    Tune(Res.drawable.ic_tune_24),
    Check(Res.drawable.ic_check_24),
    // The filled Custom sound's Replace action (feature 004, User Story 5).
    SwapHoriz(Res.drawable.ic_swap_horiz_24),
    // Filled variants (FILL 1) for the metronome's play button, as the design draws them (`tools/fetch-material-symbols.sh --fill`).
    PlayArrowFilled(Res.drawable.ic_play_arrow_fill_24),
    PauseFilled(Res.drawable.ic_pause_fill_24),
    // Note figures for the metronome's subdivision controls, hand-authored by `tools/make-note-glyphs.py`.
    NoteQuarter(Res.drawable.ic_note_quarter_24),
    NoteEighthPair(Res.drawable.ic_note_eighth_pair_24),
    NoteSixteenthQuad(Res.drawable.ic_note_sixteenth_quad_24),
    NoteEighthTriplet(Res.drawable.ic_note_eighth_triplet_24),
    NoteEighth(Res.drawable.ic_note_eighth_24),
    NoteSixteenthPair(Res.drawable.ic_note_sixteenth_pair_24),
    NoteThirtySecondQuad(Res.drawable.ic_note_thirty_second_quad_24),
    NoteSixteenthTriplet(Res.drawable.ic_note_sixteenth_triplet_24),
}
