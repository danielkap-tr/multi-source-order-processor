package com.example.orderprocessing.processing;

/**
 * Thrown when a single order cannot be processed (malformed JSON, missing/invalid
 * business fields, unknown reference data, ...).
 *
 * <p>This is a per-order failure: the pipeline catches it, routes the offending order
 * to the dead-letter sink and continues with the next order. It never aborts the batch.
 */
public class OrderProcessingException extends RuntimeException {

    public OrderProcessingException(String message) {
        super(message);
    }

    public OrderProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
