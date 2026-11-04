package audio.tembra.ui.theme

import audio.tembra.ui.resources.Res
import audio.tembra.ui.resources.ic_add_24
import audio.tembra.ui.resources.ic_arrow_left_24
import audio.tembra.ui.resources.ic_arrow_right_24
import audio.tembra.ui.resources.ic_audio_file_24
import audio.tembra.ui.resources.ic_check_24
import audio.tembra.ui.resources.ic_close_24
import audio.tembra.ui.resources.ic_content_copy_24
import audio.tembra.ui.resources.ic_content_cut_24
import audio.tembra.ui.resources.ic_expand_less_24
import audio.tembra.ui.resources.ic_expand_more_24
import audio.tembra.ui.resources.ic_fullscreen_24
import audio.tembra.ui.resources.ic_fullscreen_exit_24
import audio.tembra.ui.resources.ic_fast_forward_24
import audio.tembra.ui.resources.ic_fast_rewind_24
import audio.tembra.ui.resources.ic_folder_24
import audio.tembra.ui.resources.ic_graphic_eq_24
import audio.tembra.ui.resources.ic_headphones_24
import audio.tembra.ui.resources.ic_grid_on_24
import audio.tembra.ui.resources.ic_hearing_24
import audio.tembra.ui.resources.ic_library_music_24
import audio.tembra.ui.resources.ic_metronome_24
import audio.tembra.ui.resources.ic_mic_24
import audio.tembra.ui.resources.ic_mic_off_24
import audio.tembra.ui.resources.ic_more_vert_24
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
import audio.tembra.ui.resources.ic_piano_24
import audio.tembra.ui.resources.ic_play_arrow_24
import audio.tembra.ui.resources.ic_play_arrow_fill_24
import audio.tembra.ui.resources.ic_playlist_add_24
import audio.tembra.ui.resources.ic_radio_button_checked_24
import audio.tembra.ui.resources.ic_redo_24
import audio.tembra.ui.resources.ic_remove_24
import audio.tembra.ui.resources.ic_search_24
import audio.tembra.ui.resources.ic_sensors_24
import audio.tembra.ui.resources.ic_settings_24
import audio.tembra.ui.resources.ic_skip_next_24
import audio.tembra.ui.resources.ic_skip_previous_24
import audio.tembra.ui.resources.ic_sort_24
import audio.tembra.ui.resources.ic_swap_horiz_24
import audio.tembra.ui.resources.ic_touch_app_24
import audio.tembra.ui.resources.ic_tune_24
import audio.tembra.ui.resources.ic_tuning_fork_24
import audio.tembra.ui.resources.ic_undo_24
import audio.tembra.ui.resources.ic_volume_up_24
import audio.tembra.ui.resources.ic_zoom_in_24
import audio.tembra.ui.resources.ic_zoom_out_24
import org.jetbrains.compose.resources.DrawableResource

/**
 * Bundled glyphs (24 dp), rendered through [TembraIcons.vector]. Most are Material Symbols Outlined (weight 400,
 * grade 0, unfilled) copied from google/material-design-icons at commit 27e9ef1dbeedc13d682fece4a58e1eda4cb0961a
 * (Apache-2.0) and normalised by `tools/fetch-material-symbols.sh` (research R6 of feature 002). The `Note*` figures
 * are hand-authored by `tools/make-note-glyphs.py`, and the navigation's [Tuner] tuning fork and [Metronome] by
 * `tools/make-nav-glyphs.py` (2026-10-17): Material Symbols has neither glyph.
 * Screens use these names only (spec FR-016, FR-026); to add a glyph run the script and add a value.
 */
enum class TembraIcon(internal val resource: DrawableResource) {
    Timeline(Res.drawable.ic_graphic_eq_24),
    Tuner(Res.drawable.ic_tuning_fork_24),
    Metronome(Res.drawable.ic_metronome_24),
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
    // The Timeline menu's full-screen items in a desktop window (feature 011, spec FR-010a).
    Fullscreen(Res.drawable.ic_fullscreen_24),
    FullscreenExit(Res.drawable.ic_fullscreen_exit_24),
    // The shortcut sheet's close button (feature 011, spec FR-022).
    Close(Res.drawable.ic_close_24),
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
    // Timeline arranger glyphs (feature 009, contracts/design-system-additions.md): toolbar, transport, library and
    // the instrument chips (Mic for Vocals, Piano for Keys, MusicNote for Strings and Other, GraphicEq for Loops).
    ContentCut(Res.drawable.ic_content_cut_24),
    Undo(Res.drawable.ic_undo_24),
    Redo(Res.drawable.ic_redo_24),
    SkipPrevious(Res.drawable.ic_skip_previous_24),
    SkipNext(Res.drawable.ic_skip_next_24),
    FastRewind(Res.drawable.ic_fast_rewind_24),
    FastForward(Res.drawable.ic_fast_forward_24),
    GridOn(Res.drawable.ic_grid_on_24),
    ZoomIn(Res.drawable.ic_zoom_in_24),
    ZoomOut(Res.drawable.ic_zoom_out_24),
    Search(Res.drawable.ic_search_24),
    Sort(Res.drawable.ic_sort_24),
    ContentCopy(Res.drawable.ic_content_copy_24),
    LibraryMusic(Res.drawable.ic_library_music_24),
    ExpandLess(Res.drawable.ic_expand_less_24),
    MoreVert(Res.drawable.ic_more_vert_24),
    AudioFile(Res.drawable.ic_audio_file_24),
    PlaylistAdd(Res.drawable.ic_playlist_add_24),
    Piano(Res.drawable.ic_piano_24),
    // Feature 010 (timeline recording): the input monitoring toggle.
    Headphones(Res.drawable.ic_headphones_24),
}
