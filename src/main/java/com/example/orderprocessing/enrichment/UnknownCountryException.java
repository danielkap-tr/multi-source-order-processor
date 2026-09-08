package com.example.orderprocessing.enrichment;

import com.example.orderprocessing.processing.OrderProcessingException;

/** Raised when an order carries a country code with no reference-data entry. */
public class UnknownCountryException extends OrderProcessingException {

    private final String countryCode;

    public UnknownCountryException(String countryCode) {
        super("Unknown country code '" + countryCode + "' - no name/currency mapping available");
        this.countryCode = countryCode;
    }

    public String countryCode() {
        return countryCode;
    }
}
