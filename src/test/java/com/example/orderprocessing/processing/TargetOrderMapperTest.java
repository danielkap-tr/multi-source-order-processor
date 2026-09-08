package com.example.orderprocessing.processing;

import com.example.orderprocessing.enrichment.CountryReferenceService;
import com.example.orderprocessing.enrichment.UnknownCountryException;
import com.example.orderprocessing.model.CanonicalOrder;
import com.example.orderprocessing.model.TargetOrder;
import com.example.orderprocessing.source.SourceSystem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TargetOrderMapperTest {

    private final TargetOrderMapper mapper =
            new TargetOrderMapper(CountryReferenceService.withAssignmentDefaults());

    private static CanonicalOrder canonical(String country, int qty, String unitPrice) {
        return new CanonicalOrder("ORD-10001", "CUST-501", "John Smith", country,
                LocalDateTime.of(2026, 9, 1, 10, 30, 0), "P100", qty, new BigDecimal(unitPrice),
                SourceSystem.SOURCE_A);
    }

    @Test
    void maps_to_the_documented_target_example() {
        TargetOrder target = mapper.toTarget(canonical("US", 2, "125.50"));

        assertThat(target.orderReference()).isEqualTo("ORD-10001");
        assertThat(target.customer().customerReference()).isEqualTo("CUST-501");
        assertThat(target.customer().fullName()).isEqualTo("John Smith");
        assertThat(target.customer().country()).isEqualTo("United States");
        assertThat(target.orderTimestamp()).isEqualTo(LocalDateTime.of(2026, 9, 1, 10, 30, 0));
        assertThat(target.product().code()).isEqualTo("P100");
        assertThat(target.product().quantity()).isEqualTo(2);
        assertThat(target.product().unitPrice()).isEqualByComparingTo("125.50");
        assertThat(target.totalOrderValue()).isEqualByComparingTo("251.00");
        assertThat(target.currency()).isEqualTo("USD");
    }

    @Test
    void total_order_value_is_rounded_half_up_to_two_decimals() {
        // 3 x 12.345 = 37.035 -> 37.04
        assertThat(mapper.toTarget(canonical("DE", 3, "12.345")).totalOrderValue())
                .isEqualByComparingTo("37.04");
    }

    @Test
    void total_order_value_has_a_scale_of_exactly_two() {
        assertThat(mapper.toTarget(canonical("US", 2, "125.50")).totalOrderValue().scale()).isEqualTo(2);
    }

    @Test
    void unknown_country_is_rejected() {
        assertThatThrownBy(() -> mapper.toTarget(canonical("FR", 1, "1.00")))
                .isInstanceOf(UnknownCountryException.class);
    }
}
