package com.example.orderprocessing.delivery;

import com.example.orderprocessing.model.TargetOrder;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link TargetSystemClient} used by tests and the CLI demo.
 *
 * <p>Idempotent: delivering the same {@code orderReference} twice keeps only the last
 * version, mirroring the contract expected of a real target system.
 */
public final class InMemoryTargetSystemClient implements TargetSystemClient {

    private final Map<String, TargetOrder> delivered = new ConcurrentHashMap<>();

    @Override
    public void deliver(TargetOrder order) {
        delivered.put(order.orderReference(), order);
    }

    public List<TargetOrder> delivered() {
        return List.copyOf(delivered.values());
    }

    public int count() {
        return delivered.size();
    }
}
