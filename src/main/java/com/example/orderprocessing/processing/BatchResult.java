package com.example.orderprocessing.processing;

import com.example.orderprocessing.source.SourceSystem;

import java.util.EnumMap;
import java.util.Map;

/** Aggregate counters for one processing run. */
public final class BatchResult {

    private int submitted;
    private int delivered;
    private int failed;
    private final Map<SourceSystem, Integer> deliveredBySource = new EnumMap<>(SourceSystem.class);

    void recordDelivered(SourceSystem source) {
        submitted++;
        delivered++;
        if (source != null) {
            deliveredBySource.merge(source, 1, Integer::sum);
        }
    }

    void recordFailed() {
        submitted++;
        failed++;
    }

    public int submitted() {
        return submitted;
    }

    public int delivered() {
        return delivered;
    }

    public int failed() {
        return failed;
    }

    public Map<SourceSystem, Integer> deliveredBySource() {
        return Map.copyOf(deliveredBySource);
    }

    @Override
    public String toString() {
        return "submitted=" + submitted + ", delivered=" + delivered + ", failed=" + failed
                + ", deliveredBySource=" + deliveredBySource;
    }
}
