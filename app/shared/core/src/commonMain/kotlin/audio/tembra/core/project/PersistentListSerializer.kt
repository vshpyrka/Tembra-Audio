package audio.tembra.core.project

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * A [PersistentList] as a plain JSON array (kotlinx.collections.immutable 0.4.0 ships no serializers). The plugin
 * instantiates it with the element serializer when a property is annotated `@Serializable(with = PersistentListSerializer::class)`.
 */
class PersistentListSerializer<T>(elementSerializer: KSerializer<T>) : KSerializer<PersistentList<T>> {
    private val delegate = ListSerializer(elementSerializer)

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: PersistentList<T>) = delegate.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): PersistentList<T> = delegate.deserialize(decoder).toPersistentList()
}
