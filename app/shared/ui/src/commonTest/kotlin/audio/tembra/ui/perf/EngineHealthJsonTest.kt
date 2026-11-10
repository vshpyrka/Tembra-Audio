package audio.tembra.ui.perf

import audio.tembra.engine.perf.AudioHealth
import audio.tembra.engine.CaptureSnapshot
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals

/** `engine-health.json` as the iOS collector reads it (contracts/engine-audio-health.md): schema 1, the journey, nine fields since feature 009, thirteen since feature 010, fifteen since feature 012. */
class EngineHealthJsonTest {

    @Test
    fun scriptedValuesAreWrittenUnderTheContractKeys() {
        val capture = CaptureSnapshot().apply {
            armed = true
            monitoring = true
            sampleRate = 48_000
            alignmentFrames = 1_440  // 30 ms
            inputLatencyNanos = 8_000_000
            outputLatencyNanos = 12_000_000
        }
        val json = engineHealthJson(
            AudioHealth(callbacks = 1200, lastMicros = 850, maxMicros = 1900, overBudget = 2, xruns = 3, realtime = true, avgMicros = 410, starvation = 1, clipsActive = 5, inputOverruns = 4, recordBacklog = 512, masterClips = 7),
            capture,
            meterFramesDropped = 3,
        )
        assertEquals(
            """{"schema":1,"journey":"stress","stream_open":true,"health":{"callbacks":1200,"last_us":850,"max_us":1900,"over_budget":2,"xruns":3,"realtime":1,"avg_us":410,"starvation":1,"clips_active":5,"input_overruns":4,"record_backlog":512,"take_alignment_ms":30,"monitor_delay_ms":20,"master_clips":7,"meter_frames_dropped":3}}""",
            json,
        )
    }

    /** Nothing armed: the alignment and the monitoring delay read 0, as the publisher's counters do; no sheet frames missed. */
    @Test
    fun aDisarmedCaptureWritesZeroAlignmentAndDelay() {
        val json = engineHealthJson(AudioHealth(callbacks = 1, lastMicros = 0, maxMicros = 0, overBudget = 0, xruns = 0, realtime = true), CaptureSnapshot())
        assertTrue(json.endsWith(""","input_overruns":0,"record_backlog":0,"take_alignment_ms":0,"monitor_delay_ms":0,"master_clips":0,"meter_frames_dropped":0}}"""), json)
    }

    @Test
    fun noOpenStreamWritesZerosAndSaysSo() {
        assertEquals(
            """{"schema":1,"journey":"stress","stream_open":false,"health":{"callbacks":0,"last_us":0,"max_us":0,"over_budget":0,"xruns":0,"realtime":0,"avg_us":0,"starvation":0,"clips_active":0,"input_overruns":0,"record_backlog":0,"take_alignment_ms":0,"monitor_delay_ms":0,"master_clips":0,"meter_frames_dropped":0}}""",
            engineHealthJson(null, meterFramesDropped = 9),
        )
    }
}
