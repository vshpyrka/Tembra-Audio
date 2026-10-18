package audio.tembra.core.project

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * [MusicalKey] as its display name (`"key": "Dmin"`). The serializer is nullable on purpose: a name this reader does
 * not know reads as no key (contracts/project-document.md, evolution rule 7) instead of failing the document. The
 * null mark calls are the library's experimental surface for hand-written nullable serializers.
 */
@OptIn(ExperimentalSerializationApi::class)
object MusicalKeySerializer : KSerializer<MusicalKey?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("audio.tembra.core.project.MusicalKey", PrimitiveKind.STRING).nullable

    override fun serialize(encoder: Encoder, value: MusicalKey?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value.displayName)
    }

    override fun deserialize(decoder: Decoder): MusicalKey? {
        if (!decoder.decodeNotNullMark()) return decoder.decodeNull()
        return MusicalKey.fromDisplayName(decoder.decodeString())
    }
}
