package audio.tembra.engine

import kotlin.math.PI
import kotlin.math.sin

/** Builds small PCM WAV files in memory for the device and simulator transport tests (16-bit, one tone per channel). */
internal object TestWav {
    /** [seconds] of a [frequencyHz] tone at [rate] over [channels] channels (the second channel a fifth above), peak 0.5. */
    fun tone(seconds: Double, rate: Int = 48000, channels: Int = 1, frequencyHz: Double = 440.0): ByteArray {
        val frames = (seconds * rate).toInt()
        val data = ByteArray(frames * channels * 2)
        var at = 0
        for (i in 0 until frames) {
            for (channel in 0 until channels) {
                val hz = if (channel == 0) frequencyHz else frequencyHz * 1.5
                val value = (0.5 * sin(2.0 * PI * hz * i / rate) * 32767).toInt()
                data[at++] = (value and 0xFF).toByte()
                data[at++] = ((value shr 8) and 0xFF).toByte()
            }
        }
        return header(rate, channels, data.size) + data
    }

    private fun header(rate: Int, channels: Int, dataBytes: Int): ByteArray {
        val block = channels * 2
        val out = ByteArray(44)
        fun ascii(at: Int, text: String) = text.forEachIndexed { i, c -> out[at + i] = c.code.toByte() }
        fun u32(at: Int, value: Int) { for (i in 0 until 4) out[at + i] = ((value shr (8 * i)) and 0xFF).toByte() }
        fun u16(at: Int, value: Int) { for (i in 0 until 2) out[at + i] = ((value shr (8 * i)) and 0xFF).toByte() }
        ascii(0, "RIFF"); u32(4, 36 + dataBytes); ascii(8, "WAVE")
        ascii(12, "fmt "); u32(16, 16); u16(20, 1); u16(22, channels); u32(24, rate); u32(28, rate * block); u16(32, block); u16(34, 16)
        ascii(36, "data"); u32(40, dataBytes)
        return out
    }
}
