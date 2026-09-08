package com.example.orderprocessing.delivery;

import com.example.orderprocessing.model.TargetOrder;

/**
 * Sink for successfully processed orders.
 *
 * <p>Abstracts "the target system". Implementations might POST to an HTTP API, publish
 * to a queue, or write a file. Implementations should be idempotent on
 * {@link TargetOrder#orderReference()} so that pipeline retries do not create duplicates.
 */
public interface TargetSystemClient {

    void deliver(TargetOrder order);
}
