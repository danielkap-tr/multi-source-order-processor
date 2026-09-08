package com.example.orderprocessing.source;

import com.example.orderprocessing.model.CanonicalOrder;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Converts one source system's raw JSON order into the {@link CanonicalOrder} model.
 *
 * <p>Implementations are the only place in the codebase that knows a source's wire format.
 * To onboard a new source system you add one implementation and register it with the
 * {@link SourceRouter}; nothing else changes.
 */
public interface OrderParser {

    /** The source system this parser understands. */
    SourceSystem source();

    /**
     * @return {@code true} if {@code raw} looks like an order from {@link #source()}.
     *         Used for auto-detection when the caller does not declare the source.
     */
    boolean canParse(JsonNode raw);

    /**
     * @throws com.example.orderprocessing.processing.OrderProcessingException
     *         if a required field is missing or malformed.
     */
    CanonicalOrder parse(JsonNode raw);
}
