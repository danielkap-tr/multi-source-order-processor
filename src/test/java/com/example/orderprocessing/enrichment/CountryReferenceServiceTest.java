package com.example.orderprocessing.enrichment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CountryReferenceServiceTest {

    private final CountryReferenceService service = CountryReferenceService.withAssignmentDefaults();

    @Test
    void resolves_the_reference_table() {
        assertThat(service.resolve("US")).isEqualTo(new CountryReference("United States", "USD"));
        assertThat(service.resolve("GB")).isEqualTo(new CountryReference("United Kingdom", "GBP"));
        assertThat(service.resolve("DE")).isEqualTo(new CountryReference("Germany", "EUR"));
    }

    @Test
    void is_case_insensitive() {
        assertThat(service.resolve("de").currency()).isEqualTo("EUR");
    }

    @Test
    void throws_for_an_unknown_country_code() {
        assertThatThrownBy(() -> service.resolve("FR"))
                .isInstanceOf(UnknownCountryException.class)
                .hasMessageContaining("FR");
    }
}
