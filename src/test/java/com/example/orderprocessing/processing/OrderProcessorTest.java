package com.example.orderprocessing.processing;

import com.example.orderprocessing.TestJson;
import com.example.orderprocessing.delivery.InMemoryDeadLetterSink;
import com.example.orderprocessing.delivery.InMemoryTargetSystemClient;
import com.example.orderprocessing.enrichment.CountryReferenceService;
import com.example.orderprocessing.model.TargetOrder;
import com.example.orderprocessing.source.SourceRouter;
import com.example.orderprocessing.source.SourceSystem;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderProcessorTest {

    private InMemoryTargetSystemClient target;
    private InMemoryDeadLetterSink deadLetters;
    private OrderProcessor processor;

    private static final String A_ORDER = """
            {"orderId":"ORD-10001","customerId":"CUST-501","customerName":"John Smith","country":"US",
             "orderDate":"2026-09-01T10:30:00","productCode":"P100","quantity":2,"unitPrice":125.50}
            """;
    private static final String B_ORDER = """
            {"order_number":"ORD-20001","customer":{"id":"CUST-842","first_name":"Jane","last_name":"Miller",
             "country_code":"DE"},"created_at":"2026-09-01T11:15:00","item":{"sku":"P200","units":3,"price":80.00}}
            """;

    @BeforeEach
    void setUp() {
        target = new InMemoryTargetSystemClient();
        deadLetters = new InMemoryDeadLetterSink();
        processor = new OrderProcessor(
                SourceRouter.withDefaults(),
                new CanonicalOrderValidator(),
                new TargetOrderMapper(CountryReferenceService.withAssignmentDefaults()),
                target,
                deadLetters);
    }

    @Test
    void processes_orders_from_both_sources_in_one_mixed_batch() {
        List<JsonNode> batch = List.of(TestJson.parse(A_ORDER), TestJson.parse(B_ORDER));

        BatchResult result = processor.processBatch(batch, null);

        assertThat(result.delivered()).isEqualTo(2);
        assertThat(result.failed()).isZero();
        assertThat(result.deliveredBySource())
                .containsEntry(SourceSystem.SOURCE_A, 1)
                .containsEntry(SourceSystem.SOURCE_B, 1);
        assertThat(target.delivered()).extracting(TargetOrder::orderReference)
                .containsExactlyInAnyOrder("ORD-10001", "ORD-20001");
    }

    @Test
    void routes_an_unknown_country_to_the_dead_letter_sink_without_failing_the_batch() {
        String badCountry = A_ORDER.replace("\"country\":\"US\"", "\"country\":\"FR\"");

        BatchResult result = processor.processBatch(
                List.of(TestJson.parse(badCountry), TestJson.parse(B_ORDER)), null);

        assertThat(result.delivered()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(deadLetters.failures()).singleElement()
                .satisfies(f -> {
                    assertThat(f.source()).isEqualTo("SOURCE_A");
                    assertThat(f.reason()).contains("FR");
                });
    }

    @Test
    void dead_letters_a_malformed_order_and_keeps_its_payload() {
        JsonNode malformed = TestJson.parse("{\"orderId\":\"ORD-X\"}");

        boolean delivered = processor.process(malformed, SourceSystem.SOURCE_A);

        assertThat(delivered).isFalse();
        assertThat(deadLetters.failures()).singleElement()
                .satisfies(f -> assertThat(f.rawOrder()).isEqualTo(malformed));
    }

    @Test
    void delivery_is_idempotent_on_order_reference() {
        processor.process(TestJson.parse(A_ORDER), SourceSystem.SOURCE_A);
        processor.process(TestJson.parse(A_ORDER), SourceSystem.SOURCE_A);

        assertThat(target.count()).isEqualTo(1);
    }
}
