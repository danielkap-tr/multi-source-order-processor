package com.example.orderprocessing.source;

import com.example.orderprocessing.model.CanonicalOrder;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Parser for Source System A, whose orders are a flat object:
 * <pre>
 * {
 *   "orderId": "ORD-10001",
 *   "customerId": "CUST-501",
 *   "customerName": "John Smith",
 *   "country": "US",
 *   "orderDate": "2026-09-01T10:30:00",
 *   "productCode": "P100",
 *   "quantity": 2,
 *   "unitPrice": 125.50
 * }
 * </pre>
 */
public final class SourceAOrderParser implements OrderParser {

    @Override
    public SourceSystem source() {
        return SourceSystem.SOURCE_A;
    }

    @Override
    public boolean canParse(JsonNode raw) {
        return raw.hasNonNull("orderId") && raw.hasNonNull("customerName");
    }

    @Override
    public CanonicalOrder parse(JsonNode raw) {
        return new CanonicalOrder(
                JsonFields.requiredText(raw, "orderId"),
                JsonFields.requiredText(raw, "customerId"),
                JsonFields.requiredText(raw, "customerName"),
                JsonFields.requiredText(raw, "country"),
                JsonFields.requiredTimestamp(raw, "orderDate"),
                JsonFields.requiredText(raw, "productCode"),
                JsonFields.requiredInt(raw, "quantity"),
                JsonFields.requiredDecimal(raw, "unitPrice"),
                SourceSystem.SOURCE_A
        );
    }
}
