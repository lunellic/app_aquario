package br.com.lunelli.lulucoreef.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonNamingStrategy
import java.time.Instant
import java.time.OffsetDateTime

/** Long (epoch ms) no app <-> timestamptz ISO-8601 no Supabase. */
object TsSerializer : KSerializer<Long> {
    override val descriptor = PrimitiveSerialDescriptor("Ts", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeString(Instant.ofEpochMilli(value).toString())
    override fun deserialize(decoder: Decoder): Long = parseTs(decoder.decodeString())
}

/** Coluna jsonb no servidor <-> texto JSON no Room. */
object JsonTextSerializer : KSerializer<String> {
    override val descriptor = PrimitiveSerialDescriptor("JsonText", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: String) {
        val json = encoder as? JsonEncoder ?: return encoder.encodeString(value)
        json.encodeJsonElement(try { Json.parseToJsonElement(value) } catch (_: Exception) { JsonPrimitive(value) })
    }
    override fun deserialize(decoder: Decoder): String {
        val json = decoder as? JsonDecoder ?: return decoder.decodeString()
        return json.decodeJsonElement().toString()
    }
}

fun parseTs(text: String): Long = OffsetDateTime.parse(text).toInstant().toEpochMilli()

@OptIn(ExperimentalSerializationApi::class)
val ApiJson = Json {
    namingStrategy = JsonNamingStrategy.SnakeCase
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = true
}
