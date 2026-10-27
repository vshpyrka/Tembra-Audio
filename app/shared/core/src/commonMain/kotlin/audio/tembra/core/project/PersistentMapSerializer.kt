package audio.tembra.core.project

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * A [PersistentMap] as a plain JSON object (kotlinx.collections.immutable 0.4.0 ships no serializers). The plugin
 * instantiates it with the key and value serializers when a property is annotated `@Serializable(with = PersistentMapSerializer::class)`.
 */
class PersistentMapSerializer<K, V>(keySerializer: KSerializer<K>, valueSerializer: KSerializer<V>) : KSerializer<PersistentMap<K, V>> {
    private val delegate = MapSerializer(keySerializer, valueSerializer)

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: PersistentMap<K, V>) = delegate.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): PersistentMap<K, V> = delegate.deserialize(decoder).toPersistentMap()
}
