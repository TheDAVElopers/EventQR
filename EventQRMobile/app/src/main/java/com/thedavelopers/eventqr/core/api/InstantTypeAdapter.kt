package com.thedavelopers.eventqr.core.api

import com.google.gson.*
import java.lang.reflect.Type
import java.time.format.DateTimeParseException
import java.time.Instant

object InstantTypeAdapter : JsonSerializer<Instant>, JsonDeserializer<Instant> {
    override fun serialize(src: Instant?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        return JsonPrimitive(src?.toString())
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
            Instant.parse(value)
        } catch (e: DateTimeParseException) {
            throw JsonSyntaxException("Invalid timestamp: $value", e)
        }
    }
}
