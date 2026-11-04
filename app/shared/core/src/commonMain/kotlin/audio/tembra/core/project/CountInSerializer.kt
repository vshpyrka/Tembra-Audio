package audio.tembra.core.project

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * [CountIn] as its enum name (`"countIn": "ONE_BAR"`); an unknown name reads as [CountIn.ONE_BAR], the value a
 * project saved by feature 009 (without the field) reads as well.
 */
object CountInSerializer : KSerializer<CountIn> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("audio.tembra.core.project.CountIn", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: CountIn) = encoder.encodeString(value.name)

    override fun deserialize(decoder: Decoder): CountIn {
        val name = decoder.decodeString()
        return CountIn.entries.firstOrNull { it.name == name } ?: CountIn.ONE_BAR
    }
}
