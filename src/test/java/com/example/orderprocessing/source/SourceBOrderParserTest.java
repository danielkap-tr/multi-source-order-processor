package com.example.orderprocessing.source;

import com.example.orderprocessing.TestJson;
import com.example.orderprocessing.model.CanonicalOrder;
import com.example.orderprocessing.processing.OrderProcessingException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceBOrderParserTest {

    private final SourceBOrderParser parser = new SourceBOrderParser();

    private static final String EXAMPLE = """
            {
              "order_number": "ORD-20001",
              "customer": {
                "id": "CUST-842",
                "first_name": "Jane",
                "last_name": "Miller",
                "country_code": "DE"
              },
              "created_at": "2026-09-01T11:15:00",
              "item": { "sku": "P200", "units": 3, "price": 80.00 }
            }
            """;

    @Test
    void parses_the_assignment_example_and_joins_the_name() {
        CanonicalOrder order = parser.parse(TestJson.parse(EXAMPLE));

        assertThat(order.orderReference()).isEqualTo("ORD-20001");
        assertThat(order.customerReference()).isEqualTo("CUST-842");
        assertThat(order.customerFullName()).isEqualTo("Jane Miller");
        assertThat(order.countryCode()).isEqualTo("DE");
        assertThat(order.orderTimestamp()).isEqualTo(LocalDateTime.of(2026, 9, 1, 11, 15, 0));
        assertThat(order.productCode()).isEqualTo("P200");
        assertThat(order.quantity()).isEqualTo(3);
        assertThat(order.unitPrice()).isEqualByComparingTo("80.00");
        assertThat(order.source()).isEqualTo(SourceSystem.SOURCE_B);
    }

    @Test
    void tolerates_a_missing_last_name() {
        CanonicalOrder order = parser.parse(TestJson.parse("""
                {"order_number":"ORD-2","customer":{"id":"C","first_name":"Cher","country_code":"US"},
                 "created_at":"2026-09-01T11:15:00","item":{"sku":"P","units":1,"price":1.00}}
                """));
        assertThat(order.customerFullName()).isEqualTo("Cher");
    }

    @Test
    void rejects_an_order_with_no_customer_name_at_all() {
        assertThatThrownBy(() -> parser.parse(TestJson.parse("""
                {"order_number":"ORD-2","customer":{"id":"C","country_code":"US"},
                 "created_at":"2026-09-01T11:15:00","item":{"sku":"P","units":1,"price":1.00}}
                """)))
                .isInstanceOf(OrderProcessingException.class)
                .hasMessageContaining("name");
    }

    @Test
    void rejects_a_missing_item_object() {
        assertThatThrownBy(() -> parser.parse(TestJson.parse("""
                {"order_number":"ORD-2","customer":{"id":"C","first_name":"A","last_name":"B","country_code":"US"},
                 "created_at":"2026-09-01T11:15:00"}
                """)))
                .isInstanceOf(OrderProcessingException.class)
                .hasMessageContaining("item");
    }
}
