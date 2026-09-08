package com.example.orderprocessing.delivery;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Sink for orders that could not be processed. Captures the original payload plus the
 * reason, so failures can be inspected, fixed and replayed without data loss.
 */
public interface DeadLetterSink {

    void accept(FailedOrder failedOrder);

    record FailedOrder(JsonNode rawOrder, String source, String reason) {
    }
}
