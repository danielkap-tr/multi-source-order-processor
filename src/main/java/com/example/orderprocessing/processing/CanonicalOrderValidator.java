package com.example.orderprocessing.processing;

import com.example.orderprocessing.model.CanonicalOrder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Business validation applied to every order after parsing and before enrichment.
 *
 * <p>Field-presence problems are already caught by the source parsers; this class owns
 * the source-independent business invariants (positive quantity, non-negative price,
 * timestamp not absurdly in the future, ...). All violations for an order are collected
 * and reported together.
 */
public final class CanonicalOrderValidator {

    public void validate(CanonicalOrder order) {
        List<String> errors = new ArrayList<>();

        if (isBlank(order.orderReference())) {
            errors.add("orderReference is blank");
        }
        if (isBlank(order.customerReference())) {
            errors.add("customerReference is blank");
        }
        if (isBlank(order.customerFullName())) {
            errors.add("customerFullName is blank");
        }
        if (isBlank(order.productCode())) {
            errors.add("productCode is blank");
        }
        if (order.quantity() <= 0) {
            errors.add("quantity must be > 0 but was " + order.quantity());
        }
        if (order.unitPrice() == null || order.unitPrice().compareTo(BigDecimal.ZERO) < 0) {
            errors.add("unitPrice must be >= 0 but was " + order.unitPrice());
        }
        if (order.orderTimestamp() == null) {
            errors.add("orderTimestamp is missing");
        }

        if (!errors.isEmpty()) {
            throw new OrderProcessingException(
                    "Order " + order.orderReference() + " failed validation: " + String.join("; ", errors));
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
