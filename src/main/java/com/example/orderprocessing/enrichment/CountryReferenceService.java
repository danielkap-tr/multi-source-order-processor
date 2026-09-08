package com.example.orderprocessing.enrichment;

import java.util.Map;
import java.util.Optional;

/**
 * Resolves a 2-letter country code to its full name and currency.
 *
 * <p>The assignment supplies a fixed table (US, GB, DE). This is deliberately behind an
 * interface-like static factory so the in-memory table can be swapped for a call to a
 * reference-data service (with caching) without touching the pipeline.
 */
public final class CountryReferenceService {

    private static final Map<String, CountryReference> DEFAULT_TABLE = Map.of(
            "US", new CountryReference("United States", "USD"),
            "GB", new CountryReference("United Kingdom", "GBP"),
            "DE", new CountryReference("Germany", "EUR")
    );

    private final Map<String, CountryReference> table;

    public CountryReferenceService(Map<String, CountryReference> table) {
        this.table = Map.copyOf(table);
    }

    public static CountryReferenceService withAssignmentDefaults() {
        return new CountryReferenceService(DEFAULT_TABLE);
    }

    /**
     * @return the reference data for {@code countryCode} (case-insensitive).
     * @throws UnknownCountryException if the code is not in the table.
     */
    public CountryReference resolve(String countryCode) {
        return lookup(countryCode)
                .orElseThrow(() -> new UnknownCountryException(countryCode));
    }

    public Optional<CountryReference> lookup(String countryCode) {
        if (countryCode == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(table.get(countryCode.trim().toUpperCase()));
    }
}
