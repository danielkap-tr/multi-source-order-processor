package com.example.orderprocessing.source;

import com.example.orderprocessing.processing.OrderProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/**
 * Small helpers for reading required fields out of a raw {@link JsonNode} with
 * consistent, field-qualified error messages. Keeps the source parsers declarative.
 */
final class JsonFields {

    private JsonFields() {
    }

    static String requiredText(JsonNode parent, String path) {
        JsonNode node = parent.path(path);
        if (node.isMissingNode() || node.isNull() || node.asText().isBlank()) {
            throw new OrderProcessingException("Missing required field '" + path + "'");
        }
        return node.asText().trim();
    }

    static String optionalText(JsonNode parent, String path) {
        JsonNode node = parent.path(path);
        return (node.isMissingNode() || node.isNull()) ? "" : node.asText().trim();
    }

    static int requiredInt(JsonNode parent, String path) {
        JsonNode node = parent.path(path);
        if (node.isMissingNode() || node.isNull() || !node.canConvertToInt()) {
            throw new OrderProcessingException(
                    "Field '" + path + "' must be an integer but was '" + node.asText("") + "'");
        }
        return node.asInt();
    }

    static BigDecimal requiredDecimal(JsonNode parent, String path) {
        JsonNode node = parent.path(path);
        if (node.isMissingNode() || node.isNull()) {
            throw new OrderProcessingException("Missing required field '" + path + "'");
        }
        try {
            // Read via text so a JSON string "125.50" is accepted as well as a JSON number.
            return new BigDecimal(node.asText().trim());
        } catch (NumberFormatException e) {
            throw new OrderProcessingException(
                    "Field '" + path + "' must be a number but was '" + node.asText("") + "'");
        }
    }

    static LocalDateTime requiredTimestamp(JsonNode parent, String path) {
        String raw = requiredText(parent, path);
        try {
            return LocalDateTime.parse(raw);
        } catch (DateTimeParseException e) {
            throw new OrderProcessingException(
                    "Field '" + path + "' is not an ISO-8601 local date-time: '" + raw + "'");
        }
    }
}
