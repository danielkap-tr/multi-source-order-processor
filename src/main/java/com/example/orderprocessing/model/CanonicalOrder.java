package com.example.orderprocessing.model;

import com.example.orderprocessing.source.SourceSystem;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Source-agnostic internal representation of an order.
 *
 * <p>Every source parser converts its own JSON shape into this single canonical model.
 * All downstream logic (validation, enrichment, mapping to the target contract) works
 * against {@code CanonicalOrder} only, so adding a new source system never touches the
 * processing or delivery code.
 *
 * <p>The name is already normalised to a single full-name field here because that is a
 * source-specific concern (System A sends one field, System B sends two).
 */
public record CanonicalOrder(
        String orderReference,
        String customerReference,
        String customerFullName,
        String countryCode,
        LocalDateTime orderTimestamp,
        String productCode,
        int quantity,
        BigDecimal unitPrice,
        SourceSystem source
) {
}
