package com.thedavelopers.eventqr.core.api

import com.google.gson.*
import java.lang.reflect.Type
import java.time.format.DateTimeParseException
import java.time.Instant

object InstantTypeAdapter : JsonSerializer<Instant>, JsonDeserializer<Instant> {
    override fun serialize(src: Instant?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        return JsonPrimitive(src?.toString())
    }

    /** Accepts `Z` and numeric offsets (e.g. +08:00); Instant.parse alone rejects offsets on older runtimes. */
    internal fun parseInstant(value: String): Instant =
        try {
            Instant.parse(value)
        } catch (e: DateTimeParseException) {
            java.time.OffsetDateTime.parse(value).toInstant()
        }

    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Instant? {
        val raw = try {
            json?.asString
        } catch (e: IllegalStateException) {
            throw JsonParseException("Expected timestamp to be a JSON string", e)
        } catch (e: UnsupportedOperationException) {
            throw JsonParseException("Expected timestamp to be a JSON string", e)
        } catch (e: ClassCastException) {
            throw JsonParseException("Expected timestamp to be a JSON string", e)
        }
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return null
        return try {
            parseInstant(value)
        } catch (e: DateTimeParseException) {
            throw JsonSyntaxException("Invalid timestamp: $value", e)
        }
    }
}
