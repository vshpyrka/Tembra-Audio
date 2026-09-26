package audio.tembra.data.tuner

import audio.tembra.core.tuner.GuitarStringCount
import audio.tembra.core.tuner.Instrument
import audio.tembra.core.tuner.TunerMode
import kotlin.test.Test
import kotlin.test.assertEquals

/** Defaults, decoding of stored strings and encoding back (contracts/tuner-preferences.md, spec FR-005). */
class TunerPreferencesSnapshotTest {

    @Test
    fun defaultsAreGuitarSixStandardAutoListenLoopOn() {
        val snapshot = TunerPreferencesSnapshot()
        assertEquals(Instrument.Guitar, snapshot.instrument)
        assertEquals(GuitarStringCount.Six, snapshot.guitarStrings)
        assertEquals("guitar6-standard", snapshot.presetId)
        assertEquals(TunerMode.AutoListen, snapshot.mode)
        assertEquals(true, snapshot.loopTone)
    }

    @Test
    fun decodeReadsKnownValuesAndFallsBackForTheRest() {
        val decoded = TunerPreferencesSnapshot.decode(
            mapOf(TunerPreferenceKeys.INSTRUMENT to "cello", TunerPreferenceKeys.MODE to "byEar"),
            loopTone = false,
        )
        assertEquals(Instrument.Cello, decoded.instrument)
        assertEquals(GuitarStringCount.Six, decoded.guitarStrings)
        assertEquals("guitar6-standard", decoded.presetId)
        assertEquals(TunerMode.ByEar, decoded.mode)
        assertEquals(false, decoded.loopTone)
    }

    @Test
    fun unknownStringsYieldTheDefaults() {
        val decoded = TunerPreferencesSnapshot.decode(
            mapOf(
                TunerPreferenceKeys.INSTRUMENT to "banjo",
                TunerPreferenceKeys.GUITAR_STRINGS to "9",
                TunerPreferenceKeys.MODE to "maybe",
                TunerPreferenceKeys.PRESET_ID to null,
            ),
            loopTone = null,
        )
        assertEquals(TunerPreferencesSnapshot(), decoded)
    }

    @Test
    fun encodeWritesTheFiveKeysWithEnumIds() {
        val encoded = TunerPreferencesSnapshot(
            instrument = Instrument.Guitar,
            guitarStrings = GuitarStringCount.Seven,
            presetId = "guitar7-drop-a",
            mode = TunerMode.ByEar,
            loopTone = false,
        ).encode()
        assertEquals(
            mapOf(
                "tuner.instrument" to "guitar",
                "tuner.guitarStrings" to "7",
                "tuner.presetId" to "guitar7-drop-a",
                "tuner.mode" to "byEar",
                "tuner.loopTone" to "false",
            ),
            encoded,
        )
    }

    @Test
    fun encodeAndDecodeRoundTrip() {
        val original = TunerPreferencesSnapshot(Instrument.Cello, GuitarStringCount.Six, "cello-bach-5", TunerMode.ByEar, false)
        val encoded = original.encode()
        val decoded = TunerPreferencesSnapshot.decode(encoded, encoded[TunerPreferenceKeys.LOOP_TONE]?.toBooleanStrictOrNull())
        assertEquals(original, decoded)
    }
}
