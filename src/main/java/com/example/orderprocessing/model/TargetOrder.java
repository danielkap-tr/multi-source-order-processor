package com.example.orderprocessing.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * The exact JSON contract expected by the target system.
 *
 * <p>Field names and nesting mirror the target specification. Jackson serialises records
 * in declaration order, so the emitted JSON matches the documented example.
 */
public record TargetOrder(
        String orderReference,
        Customer customer,
        LocalDateTime orderTimestamp,
        Product product,
        BigDecimal totalOrderValue,
        String currency
) {
    public record Customer(
            String customerReference,
            String fullName,
            String country
    ) {
    }

    public record Product(
            String code,
            int quantity,
            BigDecimal unitPrice
    ) {
    }
}
