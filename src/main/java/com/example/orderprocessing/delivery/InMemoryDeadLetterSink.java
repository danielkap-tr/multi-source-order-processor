package com.example.orderprocessing.delivery;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** In-memory {@link DeadLetterSink} used by tests and the CLI demo. */
public final class InMemoryDeadLetterSink implements DeadLetterSink {

    private final List<FailedOrder> failures = new CopyOnWriteArrayList<>();

    @Override
    public void accept(FailedOrder failedOrder) {
        failures.add(failedOrder);
    }

    public List<FailedOrder> failures() {
        return List.copyOf(failures);
    }

    public int count() {
        return failures.size();
    }
}
