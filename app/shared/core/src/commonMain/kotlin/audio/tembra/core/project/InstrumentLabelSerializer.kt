package audio.tembra.core.project

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** [InstrumentLabel] as its lower-case name (`"label": "drums"`); an unknown name reads as [InstrumentLabel.Other]. */
object InstrumentLabelSerializer : KSerializer<InstrumentLabel> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("audio.tembra.core.project.InstrumentLabel", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: InstrumentLabel) = encoder.encodeString(value.serialName)

    override fun deserialize(decoder: Decoder): InstrumentLabel = InstrumentLabel.fromSerialName(decoder.decodeString())
}
