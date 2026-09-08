package com.example.orderprocessing;

import com.example.orderprocessing.enrichment.CountryReferenceService;
import com.example.orderprocessing.model.TargetOrder;
import com.example.orderprocessing.processing.TargetOrderMapper;
import com.example.orderprocessing.source.SourceRouter;
import com.example.orderprocessing.source.SourceSystem;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Locks the serialized target JSON to the contract documented in the assignment. */
class TargetOrderJsonTest {

    @Test
    void serialized_target_order_matches_the_documented_contract() throws Exception {
        String sourceA = """
                {"orderId":"ORD-10001","customerId":"CUST-501","customerName":"John Smith","country":"US",
                 "orderDate":"2026-09-01T10:30:00","productCode":"P100","quantity":2,"unitPrice":125.50}
                """;

        TargetOrder target = new TargetOrderMapper(CountryReferenceService.withAssignmentDefaults())
                .toTarget(SourceRouter.withDefaults().parse(TestJson.parse(sourceA), SourceSystem.SOURCE_A));

        String actualJson = TestJson.MAPPER.writeValueAsString(target);
        JsonNode actual = TestJson.parse(actualJson);

        assertThat(actual.get("orderReference").asText()).isEqualTo("ORD-10001");
        assertThat(actual.get("customer").get("customerReference").asText()).isEqualTo("CUST-501");
        assertThat(actual.get("customer").get("fullName").asText()).isEqualTo("John Smith");
        assertThat(actual.get("customer").get("country").asText()).isEqualTo("United States");
        assertThat(actual.get("orderTimestamp").asText()).isEqualTo("2026-09-01T10:30:00");
        assertThat(actual.get("product").get("code").asText()).isEqualTo("P100");
        assertThat(actual.get("product").get("quantity").asInt()).isEqualTo(2);
        assertThat(actual.get("product").get("unitPrice").decimalValue()).isEqualByComparingTo("125.50");
        assertThat(actual.get("totalOrderValue").decimalValue()).isEqualByComparingTo("251.00");
        assertThat(actual.get("currency").asText()).isEqualTo("USD");

        // Money must be serialized in plain decimal notation, not scientific / integer-collapsed.
        assertThat(actualJson).contains("251.00").contains("125.50");

        // No unexpected top-level fields.
        assertThat(actual.fieldNames()).toIterable().containsExactlyInAnyOrder(
                "orderReference", "customer", "orderTimestamp", "product", "totalOrderValue", "currency");
    }

    @Test
    void timestamp_is_serialized_as_an_iso_local_date_time_string() {
        String sourceA = """
                {"orderId":"O","customerId":"C","customerName":"N","country":"US",
                 "orderDate":"2026-09-01T10:30:00","productCode":"P","quantity":1,"unitPrice":1.00}
                """;
        TargetOrder target = new TargetOrderMapper(CountryReferenceService.withAssignmentDefaults())
                .toTarget(SourceRouter.withDefaults().parse(TestJson.parse(sourceA), SourceSystem.SOURCE_A));

        JsonNode node = TestJson.MAPPER.valueToTree(target);
        assertThat(node.get("orderTimestamp").asText()).isEqualTo("2026-09-01T10:30:00");
    }
}
