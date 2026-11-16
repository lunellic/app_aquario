package br.com.lunelli.lulucoreef.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import java.time.Instant
import java.time.OffsetDateTime

/** Long (epoch ms) no app <-> timestamptz ISO-8601 no Supabase. */
object TsSerializer : KSerializer<Long> {
    override val descriptor = PrimitiveSerialDescriptor("Ts", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeString(Instant.ofEpochMilli(value).toString())
    override fun deserialize(decoder: Decoder): Long = parseTs(decoder.decodeString())
}

fun parseTs(text: String): Long = OffsetDateTime.parse(text).toInstant().toEpochMilli()

@OptIn(ExperimentalSerializationApi::class)
val ApiJson = Json {
    namingStrategy = JsonNamingStrategy.SnakeCase
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = true
}
