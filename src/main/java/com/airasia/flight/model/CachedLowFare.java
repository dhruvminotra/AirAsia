package com.airasia.flight.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The value stored in Redis per (route, date). It holds the winning quote in the
 * base currency plus enough provenance (provider, flight, price class) to make
 * sold-out invalidation precise and to power observability. An {@code empty}
 * marker lets us negative-cache dates with no inventory.
 */
public record CachedLowFare(
        LocalDate date,
        BigDecimal baseAmount,
        String provider,
        String flightNumber,
        String priceClass,
        boolean empty) {

    public static CachedLowFare of(ProviderFare fare) {
        return new CachedLowFare(fare.date(), fare.baseAmount(), fare.provider(),
                fare.flightNumber(), fare.priceClass(), false);
    }

    public static CachedLowFare empty(LocalDate date) {
        return new CachedLowFare(date, null, null, null, null, true);
    }
}
