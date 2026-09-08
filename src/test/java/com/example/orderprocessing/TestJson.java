package com.example.orderprocessing;

import com.example.orderprocessing.io.JsonMapperFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Shared Jackson helper for tests. */
public final class TestJson {

    public static final ObjectMapper MAPPER = JsonMapperFactory.create();

    private TestJson() {
    }

    public static JsonNode parse(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
