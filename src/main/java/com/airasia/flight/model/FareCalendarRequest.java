package com.airasia.flight.model;

import java.time.YearMonth;

/**
 * Normalised, validated representation of a calendar request. Origin/destination
 * are upper-cased and currency defaults are applied at construction so the rest
 * of the pipeline can rely on canonical values (important: the same canonical
 * form is what the cache key is derived from).
 */
public record FareCalendarRequest(String origin, String destination, YearMonth month, String currency) {

    public static FareCalendarRequest of(String origin, String destination, YearMonth month, String currency) {
        return new FareCalendarRequest(
                origin.trim().toUpperCase(),
                destination.trim().toUpperCase(),
                month,
                currency.trim().toUpperCase());
    }

    public String route() {
        return origin + "-" + destination;
    }
}
