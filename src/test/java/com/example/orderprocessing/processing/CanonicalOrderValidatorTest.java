package com.example.orderprocessing.processing;

import com.example.orderprocessing.model.CanonicalOrder;
import com.example.orderprocessing.source.SourceSystem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CanonicalOrderValidatorTest {

    private final CanonicalOrderValidator validator = new CanonicalOrderValidator();

    private static CanonicalOrder order(int quantity, String unitPrice, String name) {
        return new CanonicalOrder("ORD-1", "CUST-1", name, "US",
                LocalDateTime.of(2026, 9, 1, 10, 30, 0), "P1", quantity,
                unitPrice == null ? null : new BigDecimal(unitPrice), SourceSystem.SOURCE_A);
    }

    @Test
    void accepts_a_valid_order() {
        assertThatCode(() -> validator.validate(order(1, "1.00", "Jane Doe"))).doesNotThrowAnyException();
    }

    @Test
    void rejects_zero_quantity() {
        assertThatThrownBy(() -> validator.validate(order(0, "1.00", "Jane Doe")))
                .isInstanceOf(OrderProcessingException.class)
                .hasMessageContaining("quantity");
    }

    @Test
    void rejects_negative_quantity() {
        assertThatThrownBy(() -> validator.validate(order(-2, "1.00", "Jane Doe")))
                .isInstanceOf(OrderProcessingException.class)
                .hasMessageContaining("quantity");
    }

    @Test
    void rejects_negative_unit_price() {
        assertThatThrownBy(() -> validator.validate(order(1, "-0.01", "Jane Doe")))
                .isInstanceOf(OrderProcessingException.class)
                .hasMessageContaining("unitPrice");
    }

    @Test
    void reports_multiple_problems_at_once() {
        assertThatThrownBy(() -> validator.validate(order(0, "-1", "")))
                .isInstanceOf(OrderProcessingException.class)
                .hasMessageContaining("quantity")
                .hasMessageContaining("unitPrice")
                .hasMessageContaining("customerFullName");
    }
}
