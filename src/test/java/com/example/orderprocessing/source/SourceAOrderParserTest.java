package com.example.orderprocessing.source;

import com.example.orderprocessing.TestJson;
import com.example.orderprocessing.model.CanonicalOrder;
import com.example.orderprocessing.processing.OrderProcessingException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceAOrderParserTest {

    private final SourceAOrderParser parser = new SourceAOrderParser();

    private static final String EXAMPLE = """
            {
              "orderId": "ORD-10001",
              "customerId": "CUST-501",
              "customerName": "John Smith",
              "country": "US",
              "orderDate": "2026-09-01T10:30:00",
              "productCode": "P100",
              "quantity": 2,
              "unitPrice": 125.50
            }
            """;

    @Test
    void parses_the_assignment_example() {
        CanonicalOrder order = parser.parse(TestJson.parse(EXAMPLE));

        assertThat(order.orderReference()).isEqualTo("ORD-10001");
        assertThat(order.customerReference()).isEqualTo("CUST-501");
        assertThat(order.customerFullName()).isEqualTo("John Smith");
        assertThat(order.countryCode()).isEqualTo("US");
        assertThat(order.orderTimestamp()).isEqualTo(LocalDateTime.of(2026, 9, 1, 10, 30, 0));
        assertThat(order.productCode()).isEqualTo("P100");
        assertThat(order.quantity()).isEqualTo(2);
        assertThat(order.unitPrice()).isEqualByComparingTo("125.50");
        assertThat(order.source()).isEqualTo(SourceSystem.SOURCE_A);
    }

    @Test
    void accepts_unit_price_sent_as_a_json_string() {
        CanonicalOrder order = parser.parse(TestJson.parse("""
                {"orderId":"O","customerId":"C","customerName":"N","country":"US",
                 "orderDate":"2026-09-01T10:30:00","productCode":"P","quantity":1,"unitPrice":"9.99"}
                """));
        assertThat(order.unitPrice()).isEqualByComparingTo(new BigDecimal("9.99"));
    }

    @Test
    void rejects_missing_required_field() {
        assertThatThrownBy(() -> parser.parse(TestJson.parse("""
                {"orderId":"ORD-1","customerId":"CUST-1","country":"US",
                 "orderDate":"2026-09-01T10:30:00","productCode":"P","quantity":1,"unitPrice":1.00}
                """)))
                .isInstanceOf(OrderProcessingException.class)
                .hasMessageContaining("customerName");
    }

    @Test
    void rejects_non_numeric_quantity() {
        assertThatThrownBy(() -> parser.parse(TestJson.parse("""
                {"orderId":"ORD-1","customerId":"CUST-1","customerName":"N","country":"US",
                 "orderDate":"2026-09-01T10:30:00","productCode":"P","quantity":"two","unitPrice":1.00}
                """)))
                .isInstanceOf(OrderProcessingException.class)
                .hasMessageContaining("quantity");
    }

    @Test
    void can_detect_its_own_shape() {
        assertThat(parser.canParse(TestJson.parse(EXAMPLE))).isTrue();
        assertThat(parser.canParse(TestJson.parse("""
                {"order_number":"ORD-2","customer":{"id":"x"}}"""))).isFalse();
    }
}
