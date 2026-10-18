package audio.tembra.core.project

import audio.tembra.core.metronome.TimeSignature
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** The metronome's [TimeSignature] as its label (`"timeSignature": "4/4"`); an unknown label fails the document. */
object TimeSignatureSerializer : KSerializer<TimeSignature> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("audio.tembra.core.metronome.TimeSignature", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: TimeSignature) = encoder.encodeString(value.label)

    override fun deserialize(decoder: Decoder): TimeSignature {
        val label = decoder.decodeString()
        return TimeSignature.entries.firstOrNull { it.label == label } ?: throw SerializationException("Unknown time signature \"$label\"")
    }
}
