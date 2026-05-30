package com.airasia.flight.provider;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Immutable instruction handed to every provider. A query can cover a whole
 * month (calendar read) or a single date (sold-out re-aggregation).
 *
 * <p>{@code excludedFareKeys} carries fares known to be unbookable (e.g. a sold
 * out price class). Providers/template filter these out so the aggregated low
 * fare reflects the <em>next</em> available price.
 */
public record FlightSearchQuery(String origin, String destination, List<LocalDate> dates, Set<String> excludedFareKeys) {

    public static FlightSearchQuery forDates(String origin, String destination, List<LocalDate> dates) {
        return new FlightSearchQuery(origin, destination, dates, Set.of());
    }

    public static FlightSearchQuery forSingleDate(String origin, String destination, LocalDate date, Set<String> excludedFareKeys) {
        return new FlightSearchQuery(origin, destination, List.of(date), excludedFareKeys);
    }
}
