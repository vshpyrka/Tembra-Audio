package audio.tembra.ui.perf

import audio.tembra.engine.perf.AudioHealth
import kotlin.test.Test
import kotlin.test.assertEquals

/** `engine-health.json` as the iOS collector reads it (contracts/engine-audio-health.md): schema 1, the journey, six fields. */
class EngineHealthJsonTest {

    @Test
    fun scriptedValuesAreWrittenUnderTheContractKeys() {
        val json = engineHealthJson(AudioHealth(callbacks = 1200, lastMicros = 850, maxMicros = 1900, overBudget = 2, xruns = 3, realtime = true))
        assertEquals(
            """{"schema":1,"journey":"stress","stream_open":true,"health":{"callbacks":1200,"last_us":850,"max_us":1900,"over_budget":2,"xruns":3,"realtime":1}}""",
            json,
        )
    }

    @Test
    fun noOpenStreamWritesZerosAndSaysSo() {
        assertEquals(
            """{"schema":1,"journey":"stress","stream_open":false,"health":{"callbacks":0,"last_us":0,"max_us":0,"over_budget":0,"xruns":0,"realtime":0}}""",
            engineHealthJson(null),
        )
    }
}
