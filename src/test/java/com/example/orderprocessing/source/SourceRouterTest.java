package com.example.orderprocessing.source;

import com.example.orderprocessing.TestJson;
import com.example.orderprocessing.processing.OrderProcessingException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceRouterTest {

    private final SourceRouter router = SourceRouter.withDefaults();

    private static final String A = """
            {"orderId":"ORD-1","customerId":"C","customerName":"N","country":"US",
             "orderDate":"2026-09-01T10:30:00","productCode":"P","quantity":1,"unitPrice":1.00}
            """;
    private static final String B = """
            {"order_number":"ORD-2","customer":{"id":"C","first_name":"A","last_name":"B","country_code":"US"},
             "created_at":"2026-09-01T11:15:00","item":{"sku":"P","units":1,"price":1.00}}
            """;

    @Test
    void auto_detects_source_a() {
        assertThat(router.parseAutoDetect(TestJson.parse(A)).source()).isEqualTo(SourceSystem.SOURCE_A);
    }

    @Test
    void auto_detects_source_b() {
        assertThat(router.parseAutoDetect(TestJson.parse(B)).source()).isEqualTo(SourceSystem.SOURCE_B);
    }

    @Test
    void honours_an_explicitly_declared_source() {
        assertThat(router.parse(TestJson.parse(A), SourceSystem.SOURCE_A).orderReference()).isEqualTo("ORD-1");
    }

    @Test
    void rejects_an_order_that_matches_no_source() {
        assertThatThrownBy(() -> router.parseAutoDetect(TestJson.parse("{\"foo\":\"bar\"}")))
                .isInstanceOf(OrderProcessingException.class)
                .hasMessageContaining("any known source");
    }
}
