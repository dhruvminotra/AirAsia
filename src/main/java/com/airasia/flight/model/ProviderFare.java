package com.airasia.flight.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A single fare quote returned by one provider for one date. Amounts are always
 * carried in the system base currency (see {@code calendar.base-currency}) so
 * aggregation can compare quotes from different providers directly and the cache
 * stays currency-agnostic; conversion to the user's currency happens at read.
 */
public record ProviderFare(
        String provider,
        String origin,
        String destination,
        LocalDate date,
        String flightNumber,
        String priceClass,
        BigDecimal baseAmount) {
}
