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
 * [AudioFormat] as its lower-case name (`"format": "wav"`). Nullable on purpose: a container this reader does not
 * know reads as null, which makes the asset unavailable without failing the document (contracts/project-document.md,
 * evolution rule 7). Writers never produce null for an asset they imported. The null mark calls are the library's
 * experimental surface for hand-written nullable serializers.
 */
@OptIn(ExperimentalSerializationApi::class)
object LenientAudioFormatSerializer : KSerializer<AudioFormat?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("audio.tembra.core.project.AudioFormat", PrimitiveKind.STRING).nullable

    override fun serialize(encoder: Encoder, value: AudioFormat?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value.extension)
    }

    override fun deserialize(decoder: Decoder): AudioFormat? {
        if (!decoder.decodeNotNullMark()) return decoder.decodeNull()
        val name = decoder.decodeString()
        return AudioFormat.entries.firstOrNull { it.extension == name }
    }
}
