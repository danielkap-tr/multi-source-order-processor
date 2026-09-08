package com.example.orderprocessing.processing;

import com.example.orderprocessing.enrichment.CountryReference;
import com.example.orderprocessing.enrichment.CountryReferenceService;
import com.example.orderprocessing.model.CanonicalOrder;
import com.example.orderprocessing.model.TargetOrder;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Maps a validated {@link CanonicalOrder} to the target-system {@link TargetOrder}
 * contract, applying the enrichment rules:
 * <ul>
 *   <li>country code &rarr; full country name + currency (reference data lookup)</li>
 *   <li>total order value = quantity &times; unit price, rounded to 2 decimal places</li>
 * </ul>
 */
public final class TargetOrderMapper {

    private final CountryReferenceService countryReferenceService;

    public TargetOrderMapper(CountryReferenceService countryReferenceService) {
        this.countryReferenceService = countryReferenceService;
    }

    public TargetOrder toTarget(CanonicalOrder order) {
        CountryReference country = countryReferenceService.resolve(order.countryCode());

        // Total is computed from the full-precision unit price, then rounded once, so
        // repeated rounding of the unit price does not accumulate error.
        BigDecimal totalOrderValue = order.unitPrice()
                .multiply(BigDecimal.valueOf(order.quantity()))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal unitPrice = order.unitPrice().setScale(2, RoundingMode.HALF_UP);

        return new TargetOrder(
                order.orderReference(),
                new TargetOrder.Customer(
                        order.customerReference(),
                        order.customerFullName(),
                        country.countryName()
                ),
                order.orderTimestamp(),
                new TargetOrder.Product(
                        order.productCode(),
                        order.quantity(),
                        unitPrice
                ),
                totalOrderValue,
                country.currency()
        );
    }
}
