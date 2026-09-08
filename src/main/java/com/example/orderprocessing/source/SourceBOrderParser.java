package com.example.orderprocessing.source;

import com.example.orderprocessing.model.CanonicalOrder;
import com.example.orderprocessing.processing.OrderProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Parser for Source System B, whose orders are nested:
 * <pre>
 * {
 *   "order_number": "ORD-20001",
 *   "customer": { "id": "CUST-842", "first_name": "Jane", "last_name": "Miller", "country_code": "DE" },
 *   "created_at": "2026-09-01T11:15:00",
 *   "item": { "sku": "P200", "units": 3, "price": 80.00 }
 * }
 * </pre>
 *
 * <p>System B splits the customer name into first/last. We join them into the single
 * full-name field the canonical model (and the target system) expect.
 */
public final class SourceBOrderParser implements OrderParser {

    @Override
    public SourceSystem source() {
        return SourceSystem.SOURCE_B;
    }

    @Override
    public boolean canParse(JsonNode raw) {
        return raw.hasNonNull("order_number") && raw.path("customer").isObject();
    }

    @Override
    public CanonicalOrder parse(JsonNode raw) {
        JsonNode customer = raw.path("customer");
        JsonNode item = raw.path("item");
        if (!customer.isObject()) {
            throw new OrderProcessingException("Missing required object 'customer'");
        }
        if (!item.isObject()) {
            throw new OrderProcessingException("Missing required object 'item'");
        }

        return new CanonicalOrder(
                JsonFields.requiredText(raw, "order_number"),
                JsonFields.requiredText(customer, "id"),
                joinName(customer),
                JsonFields.requiredText(customer, "country_code"),
                JsonFields.requiredTimestamp(raw, "created_at"),
                JsonFields.requiredText(item, "sku"),
                JsonFields.requiredInt(item, "units"),
                JsonFields.requiredDecimal(item, "price"),
                SourceSystem.SOURCE_B
        );
    }

    private static String joinName(JsonNode customer) {
        String first = JsonFields.optionalText(customer, "first_name");
        String last = JsonFields.optionalText(customer, "last_name");
        String full = (first + " " + last).trim().replaceAll("\\s+", " ");
        if (full.isBlank()) {
            throw new OrderProcessingException(
                    "Customer name is empty (neither 'first_name' nor 'last_name' present)");
        }
        return full;
    }
}
