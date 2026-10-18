package audio.tembra.core.project

import audio.tembra.core.metronome.Tempo
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** The metronome's [Tempo] as the bare integer BPM (`"tempo": 124`); an out-of-range value fails the document. */
object TempoSerializer : KSerializer<Tempo> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("audio.tembra.core.metronome.Tempo", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: Tempo) = encoder.encodeInt(value.bpm)

    override fun deserialize(decoder: Decoder): Tempo = Tempo(decoder.decodeInt())
}
