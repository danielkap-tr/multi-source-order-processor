package com.example.orderprocessing.io;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/** Single place that configures Jackson so every component behaves consistently. */
public final class JsonMapperFactory {

    private JsonMapperFactory() {
    }

    public static ObjectMapper create() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                // Emit "2026-09-01T10:30:00", not an epoch array/number.
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                // Emit 251.00, not 2.51E2. (Money keeps its BigDecimal scale because
                // values are serialized directly, never via a JsonNode tree.)
                .enable(SerializationFeature.WRITE_BIGDECIMAL_AS_PLAIN)
                .enable(SerializationFeature.INDENT_OUTPUT)
                // Unknown source fields are expected; ignore rather than fail.
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}
