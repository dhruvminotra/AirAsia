package com.airasia.flight.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One cell of the calendar response: the cheapest bookable price for a date,
 * already converted to the requested currency. {@code price} is null when no
 * inventory is available for that date.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FareCalendarData(LocalDate date, BigDecimal price, boolean available) {

    public static FareCalendarData unavailable(LocalDate date) {
        return new FareCalendarData(date, null, false);
    }
}
