package com.example.orderprocessing.enrichment;

/** Reference data for one country: its full display name and ISO-4217 currency code. */
public record CountryReference(String countryName, String currency) {
}
