package com.example.orderprocessing.source;

import com.example.orderprocessing.model.CanonicalOrder;
import com.example.orderprocessing.processing.OrderProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Optional;

/**
 * Holds the registered {@link OrderParser}s and turns a raw JSON order into a
 * {@link CanonicalOrder}, either for an explicitly declared source or by auto-detecting
 * the source from the JSON shape.
 *
 * <p>In production the source is normally known from the transport (a Kafka topic, an
 * SQS queue, an HTTP path), so {@link #parse(JsonNode, SourceSystem)} is the primary
 * entry point. Auto-detection exists mainly so a single mixed file can be replayed.
 */
public final class SourceRouter {

    private final List<OrderParser> parsers;

    public SourceRouter(List<OrderParser> parsers) {
        if (parsers == null || parsers.isEmpty()) {
            throw new IllegalArgumentException("At least one OrderParser must be registered");
        }
        this.parsers = List.copyOf(parsers);
    }

    /** Router wired with the two known source systems. */
    public static SourceRouter withDefaults() {
        return new SourceRouter(List.of(new SourceAOrderParser(), new SourceBOrderParser()));
    }

    /** Parse an order whose source system is already known. */
    public CanonicalOrder parse(JsonNode raw, SourceSystem source) {
        return parsers.stream()
                .filter(p -> p.source() == source)
                .findFirst()
                .orElseThrow(() -> new OrderProcessingException("No parser registered for source " + source))
                .parse(raw);
    }

    /** Parse an order, detecting the source system from its JSON shape. */
    public CanonicalOrder parseAutoDetect(JsonNode raw) {
        List<OrderParser> matches = parsers.stream().filter(p -> p.canParse(raw)).toList();
        if (matches.isEmpty()) {
            throw new OrderProcessingException("Could not match order to any known source system");
        }
        if (matches.size() > 1) {
            throw new OrderProcessingException("Order shape is ambiguous between "
                    + matches.stream().map(OrderParser::source).toList());
        }
        return matches.get(0).parse(raw);
    }

    public Optional<SourceSystem> detect(JsonNode raw) {
        List<OrderParser> matches = parsers.stream().filter(p -> p.canParse(raw)).toList();
        return matches.size() == 1 ? Optional.of(matches.get(0).source()) : Optional.empty();
    }
}
