package audio.tembra.ui.tuner

import audio.tembra.core.tuner.GuitarStringCount
import audio.tembra.core.tuner.Instrument
import audio.tembra.core.tuner.Note
import audio.tembra.core.tuner.PitchDirection
import audio.tembra.core.tuner.TunerMode
import audio.tembra.engine.AudioState
import audio.tembra.engine.PitchSnapshot
import audio.tembra.engine.TestSignals
import audio.tembra.ui.platform.MicrophonePermissionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Defaults and derived values of the UI state (data-model.md §7) and the meter's announcement (§4). */
class TunerUiStateTest {

    @Test
    fun defaultsMatchTheFirstLaunch() {
        val state = TunerUiState()
        assertFalse(state.loaded)
        assertEquals(Instrument.Guitar, state.instrument)
        assertEquals(GuitarStringCount.Six, state.guitarStrings)
        assertEquals("guitar6-standard", state.preset.id)
        assertEquals(7, state.presets.size)
        assertEquals(TunerMode.AutoListen, state.mode)
        assertTrue(state.loopTone)
        assertEquals(MicrophonePermissionStatus.NotDetermined, state.microphone)
        assertEquals(AudioState.Idle, state.audio)
        assertNull(state.reading)
        assertNull(state.audition)
        assertNull(state.testSignal)
        assertEquals(state.preset.strings, state.strings)
    }

    @Test
    fun thePillFollowsTheAudioState() {
        assertNull(TunerUiState().pill)
        assertEquals(AudioStatePill.MicActive, TunerUiState(audio = AudioState.Listening).pill)
        assertEquals(AudioStatePill.TestSignal, TunerUiState(audio = AudioState.Listening, testSignal = TestSignals.byId("e2-in-tune")!!.signal).pill)
        assertEquals(AudioStatePill.ToneOut, TunerUiState(audio = AudioState.Tone).pill)
        assertEquals("Mic active", AudioStatePill.MicActive.label)
        assertEquals("Tone out", AudioStatePill.ToneOut.label)
        assertEquals("Test signal", AudioStatePill.TestSignal.label)
    }

    @Test
    fun theDeniedStateShowsOnlyInAutoListenWithoutATestSignal() {
        assertTrue(TunerUiState(microphone = MicrophonePermissionStatus.DeniedOpenSettings).showDeniedState)
        assertTrue(TunerUiState(microphone = MicrophonePermissionStatus.DeniedCanAsk).showDeniedState)
        assertFalse(TunerUiState(microphone = MicrophonePermissionStatus.NotDetermined).showDeniedState)
        assertFalse(TunerUiState(microphone = MicrophonePermissionStatus.Granted).showDeniedState)
        assertFalse(TunerUiState(microphone = MicrophonePermissionStatus.DeniedOpenSettings, mode = TunerMode.ByEar).showDeniedState)
        assertFalse(TunerUiState(microphone = MicrophonePermissionStatus.DeniedOpenSettings, testSignal = TestSignals.byId("e2-in-tune")!!.signal).showDeniedState)
    }

    @Test
    fun theMeterAnnouncesNoteAndRoundedCents() {
        assertEquals("E2, 4 cents sharp", MeterReading(Note.parse("E2"), 3.6, PitchDirection.Sharp, false, 82.6, 82.41).stateDescription)
        assertEquals("F#3, 12 cents flat", MeterReading(Note.parse("F#3"), -12.4, PitchDirection.Flat, false, 183.7, 185.0).stateDescription)
        assertEquals("A2, in tune", MeterReading(Note.parse("A2"), 1.0, PitchDirection.InTune, true, 110.06, 110.0).stateDescription)
        assertEquals("Listening", MeterReading.describe(null))
    }

    @Test
    fun aSnapshotBecomesAReadingOnlyWithAPitch() {
        val snapshot = PitchSnapshot().apply {
            hasPitch = true
            frequencyHz = 83.37f
        }
        val reading = MeterReading.from(snapshot)!!
        assertEquals(Note.parse("E2"), reading.note)
        assertEquals(PitchDirection.Sharp, reading.direction)
        assertTrue(reading.cents in 19.5..20.5, "cents ${reading.cents}")
        assertNull(MeterReading.from(PitchSnapshot()))
    }
}
