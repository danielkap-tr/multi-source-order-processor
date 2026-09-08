package com.example.orderprocessing.processing;

import com.example.orderprocessing.delivery.DeadLetterSink;
import com.example.orderprocessing.delivery.TargetSystemClient;
import com.example.orderprocessing.model.CanonicalOrder;
import com.example.orderprocessing.model.TargetOrder;
import com.example.orderprocessing.source.SourceRouter;
import com.example.orderprocessing.source.SourceSystem;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * The processing pipeline for a single order:
 *
 * <pre>
 *   raw JSON --parse--> CanonicalOrder --validate--> --map+enrich--> TargetOrder --deliver--> target system
 *                                   \--- any failure ---> dead-letter sink
 * </pre>
 *
 * <p>Stateless and thread-safe (given thread-safe collaborators), so a run can fan the
 * same processor across many worker threads.
 */
public final class OrderProcessor {

    private static final System.Logger LOG = System.getLogger(OrderProcessor.class.getName());

    private final SourceRouter sourceRouter;
    private final CanonicalOrderValidator validator;
    private final TargetOrderMapper mapper;
    private final TargetSystemClient targetSystemClient;
    private final DeadLetterSink deadLetterSink;

    public OrderProcessor(SourceRouter sourceRouter,
                          CanonicalOrderValidator validator,
                          TargetOrderMapper mapper,
                          TargetSystemClient targetSystemClient,
                          DeadLetterSink deadLetterSink) {
        this.sourceRouter = sourceRouter;
        this.validator = validator;
        this.mapper = mapper;
        this.targetSystemClient = targetSystemClient;
        this.deadLetterSink = deadLetterSink;
    }

    /**
     * Process one raw order.
     *
     * @param raw            the source payload
     * @param declaredSource the known source system, or {@code null} to auto-detect
     * @return {@code true} if the order was delivered, {@code false} if it was dead-lettered
     */
    public boolean process(JsonNode raw, SourceSystem declaredSource) {
        try {
            CanonicalOrder canonical = (declaredSource != null)
                    ? sourceRouter.parse(raw, declaredSource)
                    : sourceRouter.parseAutoDetect(raw);

            validator.validate(canonical);

            TargetOrder target = mapper.toTarget(canonical);
            targetSystemClient.deliver(target);

            LOG.log(System.Logger.Level.DEBUG, "Delivered order {0} from {1}",
                    target.orderReference(), canonical.source());
            return true;
        } catch (RuntimeException e) {
            String source = declaredSource != null ? declaredSource.name()
                    : sourceRouter.detect(raw).map(Enum::name).orElse("UNKNOWN");
            LOG.log(System.Logger.Level.WARNING, "Dead-lettering order from {0}: {1}", source, e.getMessage());
            deadLetterSink.accept(new DeadLetterSink.FailedOrder(raw, source, e.getMessage()));
            return false;
        }
    }

    /**
     * Process a batch of raw orders, isolating per-order failures.
     *
     * @param rawOrders      the source payloads
     * @param declaredSource the known source system for the whole batch, or {@code null} to auto-detect each
     */
    public BatchResult processBatch(Iterable<JsonNode> rawOrders, SourceSystem declaredSource) {
        BatchResult result = new BatchResult();
        for (JsonNode raw : rawOrders) {
            boolean delivered = process(raw, declaredSource);
            if (delivered) {
                SourceSystem source = declaredSource != null ? declaredSource
                        : sourceRouter.detect(raw).orElse(null);
                result.recordDelivered(source);
            } else {
                result.recordFailed();
            }
        }
        return result;
    }
}
