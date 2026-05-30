package com.airasia.flight.provider;

import com.airasia.flight.model.ProviderFare;

import java.time.LocalDate;

/**
 * Canonical identity of a single fare (flight + price class + date), used to
 * match a sold-out event against the exact fare to exclude during re-search.
 */
public final class FareKeys {

    private FareKeys() {
    }

    public static String of(String flightNumber, String priceClass, LocalDate date) {
        return flightNumber + "|" + priceClass + "|" + date;
    }

    public static String of(ProviderFare fare) {
        return of(fare.flightNumber(), fare.priceClass(), fare.date());
    }
}
